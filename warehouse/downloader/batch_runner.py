"""
warehouse.downloader.batch_runner
=====================================

Fans a backfill or incremental-update request out across many
(instrument, timeframe) combinations, bounded by
`DownloaderConfig.max_parallel_downloads`, wrapped end-to-end in a
`JobManager` job so progress, failure, and resumability are all visible
through the same infrastructure the rest of NGWH-001 uses.

Parallelism boundary: work is parallelized ACROSS distinct
(instrument, timeframe) pairs, never within one — each
`DownloadOrchestrator.run()` call processes its own chunks strictly
sequentially. This means two threads never write to the same partition
file concurrently (different instruments/timeframes always resolve to
different partition paths), so no additional file-level locking is needed
beyond what `ParquetStorageManager`'s atomic writes already provide. The
rate limiter is the only object genuinely shared across threads, and it's
built to be thread-safe for exactly this reason.
"""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from datetime import date, timedelta

from warehouse.bootstrap.bootstrap import WarehouseHandles
from warehouse.core.constants import JobStatus, JobType, Timeframe
from warehouse.core.logging_config import get_logger, log_with_context
from warehouse.downloader.download_orchestrator import DownloadOrchestrator, OrchestratorResult
from warehouse.downloader.downloader_config import DownloaderConfig
from warehouse.downloader.exceptions import BatchRunError
from warehouse.downloader.http_client import UpstoxHistoricalClient
from warehouse.downloader.rate_limiter import TokenBucketRateLimiter
from warehouse.progress.progress_tracker import ProgressTracker

logger = get_logger(__name__)


@dataclass
class InstrumentTimeframeFailure:
    instrument_id: str
    timeframe: str
    error: str


@dataclass
class BatchResult:
    job_id: str
    successes: list[OrchestratorResult] = field(default_factory=list)
    failures: list[InstrumentTimeframeFailure] = field(default_factory=list)

    @property
    def total_rows_written(self) -> int:
        return sum(r.total_rows_written for r in self.successes)

    @property
    def is_fully_successful(self) -> bool:
        return len(self.failures) == 0


class BatchRunner:
    """Runs a backfill or incremental update across many instruments/timeframes."""

    def __init__(
        self,
        handles: WarehouseHandles,
        downloader_config: DownloaderConfig,
        access_token: str,
    ):
        self._handles = handles
        self._config = downloader_config
        self._access_token = access_token

    def run_backfill(
        self,
        instrument_ids: list[str],
        timeframes: list[Timeframe],
        start_date: date,
        end_date: date,
        *,
        force_refresh: bool = False,
    ) -> BatchResult:
        job_id = self._handles.job_manager.create_job(
            JobType.BACKFILL_DOWNLOAD,
            {
                "instrument_ids": instrument_ids, "timeframes": [t.value for t in timeframes],
                "start_date": start_date.isoformat(), "end_date": end_date.isoformat(),
                "force_refresh": force_refresh,
            },
        )
        return self._execute(job_id, instrument_ids, timeframes, start_date, end_date, force_refresh)

    def run_incremental_update(
        self,
        instrument_ids: list[str],
        timeframes: list[Timeframe],
        *,
        lookback_days: int = 5,
    ) -> BatchResult:
        """
        Convenience wrapper for daily incremental updates: requests the last
        `lookback_days` calendar days (a small overlap window past the
        expected 1-day gap absorbs holidays/weekends/late catalog updates
        without missing data) — `coverage_planner` still only actually
        fetches whatever portion of that window isn't already covered.
        """
        end_date = date.today()
        start_date = end_date - timedelta(days=lookback_days)
        job_id = self._handles.job_manager.create_job(
            JobType.INCREMENTAL_UPDATE,
            {
                "instrument_ids": instrument_ids, "timeframes": [t.value for t in timeframes],
                "lookback_days": lookback_days,
            },
        )
        return self._execute(job_id, instrument_ids, timeframes, start_date, end_date, force_refresh=False)

    def _execute(
        self,
        job_id: str,
        instrument_ids: list[str],
        timeframes: list[Timeframe],
        start_date: date,
        end_date: date,
        force_refresh: bool,
    ) -> BatchResult:
        self._handles.job_manager.transition(job_id, JobStatus.RUNNING)
        result = BatchResult(job_id=job_id)

        rate_limiter = TokenBucketRateLimiter(self._config.rate_limit)
        tasks = [(iid, tf) for iid in instrument_ids for tf in timeframes]
        progress = ProgressTracker(job_id, f"backfill:{job_id}", total_units=len(tasks))

        log_with_context(
            logger, 20, "Batch download starting",
            job_id=job_id, instruments=len(instrument_ids), timeframes=[t.value for t in timeframes],
            total_tasks=len(tasks), max_parallel=self._config.max_parallel_downloads,
        )

        try:
            with ThreadPoolExecutor(max_workers=self._config.max_parallel_downloads) as executor:
                futures = {
                    executor.submit(
                        self._run_one, job_id, instrument_id, timeframe, start_date, end_date, force_refresh,
                        rate_limiter, progress,
                    ): (instrument_id, timeframe)
                    for instrument_id, timeframe in tasks
                }
                for future in as_completed(futures):
                    instrument_id, timeframe = futures[future]
                    try:
                        orch_result = future.result()
                        result.successes.append(orch_result)
                    except Exception as exc:
                        log_with_context(
                            logger, 40, "Instrument/timeframe download failed",
                            job_id=job_id, instrument_id=instrument_id, timeframe=timeframe.value, error=str(exc),
                        )
                        result.failures.append(InstrumentTimeframeFailure(instrument_id, timeframe.value, str(exc)))
        except Exception as exc:
            self._handles.job_manager.transition(job_id, JobStatus.FAILED, error_message=str(exc))
            progress.finish()
            raise BatchRunError(f"Batch run {job_id} failed during orchestration setup", context={"job_id": job_id}) from exc

        progress.finish()
        self._handles.job_manager.transition(job_id, JobStatus.COMPLETED)
        log_with_context(
            logger, 20, "Batch download finished",
            job_id=job_id, successes=len(result.successes), failures=len(result.failures),
            total_rows_written=result.total_rows_written,
        )
        return result

    def _run_one(
        self,
        job_id: str,
        instrument_id: str,
        timeframe: Timeframe,
        start_date: date,
        end_date: date,
        force_refresh: bool,
        rate_limiter: TokenBucketRateLimiter,
        progress: ProgressTracker,
    ) -> OrchestratorResult:
        client = UpstoxHistoricalClient(self._access_token, self._config, rate_limiter)
        orchestrator = DownloadOrchestrator(self._handles, self._config, client)
        return orchestrator.run(job_id, instrument_id, timeframe, start_date, end_date, force_refresh=force_refresh, progress=progress)
