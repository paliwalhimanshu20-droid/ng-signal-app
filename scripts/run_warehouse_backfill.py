"""
scripts/run_warehouse_backfill.py

CLI entrypoint for Historical Warehouse backfills and incremental updates,
intended for genuinely large runs (100+ instruments, multi-year ranges)
that shouldn't block a Streamlit browser tab for hours. Uses the exact
same NGWH-002 code path as the Historical Downloader page in the app
(warehouse.downloader.run_historical_backfill / run_daily_incremental_update)
— nothing here reimplements download logic. Checkpointing behaves
identically whether triggered from this script or from the app: an
interrupted run resumes cleanly from wherever it left off next time this
script (or the app) is run with the same job's parameters.

Reads UPSTOX_ACCESS_TOKEN from the environment (not Streamlit Secrets —
this runs outside a Streamlit session), matching the existing
check_signals.py / scripts/run_update.py pattern of environment-variable
configuration for non-Streamlit entrypoints.

Usage:
    # Full backfill, all active equities, daily candles, 10 years back
    python scripts/run_warehouse_backfill.py backfill \\
        --asset-class equity --timeframes 1day \\
        --start 2016-01-01 --end 2026-01-01

    # Backfill specific instruments
    python scripts/run_warehouse_backfill.py backfill \\
        --instruments "NSE_EQ|INE002A01018,NSE_EQ|INE467B01029" \\
        --timeframes 1day,30min --start 2020-01-01 --end 2026-01-01

    # Daily incremental update (intended for a scheduled GitHub Action,
    # same pattern as check_signals.yml)
    python scripts/run_warehouse_backfill.py incremental \\
        --asset-class equity --timeframes 1day,30min --lookback-days 5

Exit code is non-zero if any instrument/timeframe combination failed, so a
CI/cron job can surface it loudly (same convention as run_update.py).
"""

from __future__ import annotations

import argparse
import datetime as dt
import logging
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")


def _parse_timeframes(raw: str):
    from warehouse.core.constants import Timeframe

    values = [v.strip() for v in raw.split(",") if v.strip()]
    return [Timeframe(v) for v in values]


def _parse_instruments(raw: str | None):
    if not raw:
        return None
    return [v.strip() for v in raw.split(",") if v.strip()]


def _parse_date(raw: str) -> dt.date:
    return dt.datetime.strptime(raw, "%Y-%m-%d").date()


def _require_token() -> str:
    token = os.environ.get("UPSTOX_ACCESS_TOKEN", "")
    if not token:
        print("UPSTOX_ACCESS_TOKEN environment variable is not set — cannot proceed.", file=sys.stderr)
        sys.exit(1)
    return token


def cmd_backfill(args: argparse.Namespace) -> int:
    from warehouse import load_config, WarehouseBootstrap
    from warehouse.downloader import DownloaderConfig, run_historical_backfill

    token = _require_token()
    handles = WarehouseBootstrap(load_config()).run()

    downloader_config = DownloaderConfig(max_parallel_downloads=args.parallel_workers)

    result = run_historical_backfill(
        handles, token,
        _parse_instruments(args.instruments), _parse_timeframes(args.timeframes),
        _parse_date(args.start), _parse_date(args.end),
        asset_class=args.asset_class, downloader_config=downloader_config,
        force_refresh=args.force_refresh,
    )

    _print_result(result)
    handles.duckdb_manager.close()
    return 0 if result.is_fully_successful else 1


def cmd_incremental(args: argparse.Namespace) -> int:
    from warehouse import load_config, WarehouseBootstrap
    from warehouse.downloader import DownloaderConfig, run_daily_incremental_update

    token = _require_token()
    handles = WarehouseBootstrap(load_config()).run()

    downloader_config = DownloaderConfig(max_parallel_downloads=args.parallel_workers)

    result = run_daily_incremental_update(
        handles, token,
        _parse_instruments(args.instruments), _parse_timeframes(args.timeframes),
        asset_class=args.asset_class, downloader_config=downloader_config,
        lookback_days=args.lookback_days,
    )

    _print_result(result)
    handles.duckdb_manager.close()
    return 0 if result.is_fully_successful else 1


def _print_result(result) -> None:
    print(f"Job ID: {result.job_id}")
    print(f"Instrument/timeframe combinations succeeded: {len(result.successes)}")
    print(f"Instrument/timeframe combinations failed:    {len(result.failures)}")
    print(f"Total candles written: {result.total_rows_written:,}")
    if result.failures:
        print("\nFailures:")
        for f in result.failures:
            print(f"  - {f.instrument_id} ({f.timeframe}): {f.error}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="NGWH-002/003 Historical Warehouse backfill CLI")
    subparsers = parser.add_subparsers(dest="command", required=True)

    common_selection = argparse.ArgumentParser(add_help=False)
    common_selection.add_argument(
        "--instruments", type=str, default=None,
        help="Comma-separated instrument_keys. Omit to select via --asset-class instead.",
    )
    common_selection.add_argument(
        "--asset-class", type=str, default=None,
        help="Filter active Instrument Master entries by asset_class (e.g. equity, commodity_futures). "
             "Ignored if --instruments is given. Omit both for ALL active instruments.",
    )
    common_selection.add_argument(
        "--timeframes", type=str, required=True,
        help="Comma-separated timeframes to download directly, e.g. 1day,30min",
    )
    common_selection.add_argument("--parallel-workers", type=int, default=4)

    backfill_parser = subparsers.add_parser("backfill", parents=[common_selection], help="Run a historical backfill")
    backfill_parser.add_argument("--start", type=str, required=True, help="YYYY-MM-DD")
    backfill_parser.add_argument("--end", type=str, required=True, help="YYYY-MM-DD")
    backfill_parser.add_argument("--force-refresh", action="store_true", default=False)
    backfill_parser.set_defaults(func=cmd_backfill)

    incremental_parser = subparsers.add_parser(
        "incremental", parents=[common_selection], help="Run a daily incremental update"
    )
    incremental_parser.add_argument("--lookback-days", type=int, default=5)
    incremental_parser.set_defaults(func=cmd_incremental)

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    exit_code = args.func(args)
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
