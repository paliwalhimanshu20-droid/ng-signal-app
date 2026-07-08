# NG Signal Pro — Phase 0: Backend Migration & Performance Optimization

**Status:** Draft for Watcher approval · **Prepared against:** live `ng-signal-app` repo (main branch, pulled directly for this review) · **Owner:** The Watcher · **Primary agents involved:** Batman (Architect), Jarvis (Orchestration), Captain America (QA), Nick Fury (PM)

---

## 1. Current Architecture Analysis

I pulled the actual repo rather than working from memory, so this is based on real code, not assumptions. Two important corrections to the brief's premise, up front:

- **The scanner is *not* auto-run on page load today.** In `app.py`, `run_scanner()` only fires when the user clicks the "🚀 Run Live Scan" button, and results are cached in `st.session_state` (`scan_df` / `scan_full_df`) across reruns. So opening/refreshing the dashboard doesn't itself trigger a full scan.
- **There is already one backend job**: `check_signals.py`, run every 30 min in trading hours by `.github/workflows/check_signals.yml`. But it only *checks outcomes* of already-open trades (target/SL hit) — it does **not** generate new signals. New signal generation only happens inside the interactive Streamlit session, on click.

That second point is the real latency source and the correct target for this migration: **signal generation is currently coupled to a live user click inside the UI process**, and there's no scheduled job that produces fresh signals independent of someone having the app open.

### Current data/compute flow

```
User clicks "Run Live Scan" (app.py)
   → scanner.run_scanner()
        → upstox_client.get_prices_bulk() / get_candles() / get_daily_trend() / get_market_trend()  [network]
        → signal_logic: ema/atr/rsi/supertrend/adx/signal_engine/levels  [CPU]
        → market_engine (Regime/Fundamental/Event/Confidence)           [CPU]
   → risk_engine.generate_trade_summary()  [cheap, per-signal math using UI inputs]
   → signal_log.append_new_signals() → research_db.live_trades → push_research_db_to_github()  [network + git]
   → rendered in the same request
```

Everything above the risk-sizing step runs **synchronously inside the Streamlit process, in the same request that's rendering the page**, for every user who clicks Scan.

### Module inventory (already well-factored)

| Module | Role today |
|---|---|
| `scanner.py` | Orchestrates one full scan pass (data fetch + scoring) |
| `signal_logic.py` | Indicator math + signal scoring (shared with `backtest.py`) |
| `market_engine.py`, `event_engine.py`, `fundamental_engine.py` | Regime/confidence/context layers |
| `risk_engine.py` | Position sizing — pure math, no network calls, depends on live UI inputs (account size, risk %) |
| `upstox_client.py` | All Upstox API calls |
| `research_db/` | `live_trades` table + 9 research tables — **this already is the "central database" the brief asks for** |
| `signal_log.py` | Reads/writes `live_trades`, pushes DB back to GitHub |
| `check_signals.py` | Standalone, zero-Streamlit-dependency script; scheduled outcome checker |
| `warehouse/` | Separate historical OHLCV warehouse (Parquet+DuckDB) — Phase 1 territory, currently blocked by Captain America's NO-GO on `data/warehouse` persistence |
| `app.py`, `ui_components.py`, `charts.py`, `dashboard.css` | Presentation only |

So the good news: you don't need a from-scratch redesign. `research_db.live_trades` already has `signal_id, timestamp, instrument, signal, trend, confidence, score, entry_price, sl, t1, t2, status, adx, conviction_pct, expected_move_pct, daily_trend_agree, supertrend_agree, market_trend_agree`. It's missing only a `market_regime` column for the Regime Engine output. This is a **completion**, not a rebuild.

---

## 2. Bottleneck Analysis

| Task | Where it runs today | Classification | Latency impact |
|---|---|---|---|
| Bulk LTP + candle fetch (Upstox) | `scanner.py`, inside Streamlit request | **Backend Task** | High — network-bound, ~1 call per watchlist symbol group |
| EMA/RSI/ATR/Supertrend/ADX/scoring | `signal_logic.py`, inside Streamlit request | **Backend Task** | Medium-high — CPU-bound across full watchlist |
| Regime/Fundamental/Event/Confidence | `market_engine.py` etc., inside Streamlit request | **Backend Task** | Medium |
| `append_new_signals` → DB write → GitHub push | `signal_log.py`, inside Streamlit request | **Backend Task** | Medium — network round-trip to GitHub Contents API, blocks the UI thread |
| Position sizing (`risk_engine.generate_trade_summary`) | `app.py`, inside Streamlit request | **Shared Utility** | Negligible — pure arithmetic, but depends on live UI state (account size, risk %), so it correctly stays close to the UI, not in the backend batch job |
| Chart rendering (`charts.py`) | Streamlit | **UI Task** | Low, already fine |
| Performance summaries (`compute_performance_summary`, `compute_factor_performance`, `compute_timing_stats`) | `signal_log.py`, called from `app.py` | **UI Task today, borderline** | Low-medium — these are aggregations over `live_trades`, cheap enough to leave as read-time UI aggregation for now; candidates to pre-materialize later if the Performance tab gets slow |
| Watchlist instrument-key validation | `app.py` "Validate Watchlist Instrument Keys" expander | **UI Task (on-demand)** | Low — already opt-in behind a button/expander, not on every load |
| Outcome checking (target/SL hit) | `check_signals.py` | Already backend | None — already correctly placed |

**Root cause, restated simply:** the app doesn't have a "no signals to display yet" cold-start problem — it has a "signal generation only happens when a human is staring at the loading spinner" problem. Fix that one seam and the rest of the app is already close to the target architecture.

---

## 3. New Backend Architecture

Add one new scheduled job that plays the same role as `check_signals.py`, but for signal **generation** instead of outcome checking. It reuses `scanner.py` and `signal_logic.py` as-is — no logic changes, only a new caller.

```
generate_signals.py  (new, zero-Streamlit dependency, run by GitHub Actions)
   → scanner.run_scanner()  [same function app.py calls today]
   → risk_engine — NOT called here (depends on per-user UI state, stays in Streamlit)
   → signal_log.append_new_signals()  [same function, writes to live_trades]
   → push_research_db_to_github()     [same function, already exists]
   → Telegram alert for each new signal (reuses check_signals.py's Telegram helper)
```

Runs every 15–30 min during market hours, same cron pattern as `check_signals.yml`. Streamlit's Scanner tab changes from "click to compute" to "read latest batch from `live_trades`, with an optional manual Force Rescan button for ad-hoc use" (kept for your own testing/debugging — it still calls the same `run_scanner`, just isn't the primary path anymore).

### One code-quality fix needed to make `scanner.py` backend-safe

`scanner.py` has a single `st.error(...)` call inside the fetch-error branch (line ~214). That's the only Streamlit coupling in the module. It needs to become a plain return/log so `generate_signals.py` can import `scanner.py` without pulling in Streamlit. Small, mechanical change — see §6.

### Watch Tower fit

- **Doctor Strange** (Trading Intelligence) owns `signal_logic.py` / `market_engine.py` — unaffected, no logic changes.
- **Iron Man** (Data) owns `upstox_client.py`, `research_db` — the new job's DB writes go through his existing surface.
- **Captain America** (QA) — this migration does **not** touch the `data/warehouse` NO-GO; that's the separate Historical Intelligence Warehouse and stays blocked until warehouse persistence is verified independently. Flagging so Phase 0 isn't mistaken for resolving it.
- **Flash** (Performance) — this is squarely his mandate; recommend he owns the before/after timing capture in §8.
- **Jarvis** — coordinates the PR sequence below and reports status to you as Watcher.

---

## 4. Updated Folder Structure

Only additive changes — nothing existing moves.

```
ng-signal-app/
├── generate_signals.py          # NEW — backend signal-generation job (mirrors check_signals.py)
├── check_signals.py             # unchanged
├── scanner.py                   # 1-line change: st.error → return error in result dict
├── signal_log.py                # + one new read function: get_latest_signal_batch()
├── research_db/
│   ├── migrations.py            # + migration_003_add_market_regime_column (optional, see §6)
│   └── database.py              # + get_latest_batch() helper
├── app.py                       # Scanner tab: read-first, Force-Rescan-optional
└── .github/workflows/
    └── generate_signals.yml     # NEW — schedules generate_signals.py
```

---

## 5. Migration Plan

Sequenced for your GitHub-web-UI, no-terminal workflow — each step is a small, independently-mergeable PR you can push via the web editor.

1. **PR 1 — Make `scanner.py` Streamlit-free.** Replace the one `st.error` call with a plain dict entry (error message returned in results, not rendered). Zero behavior change for the UI, since `app.py` already reads `full_df` for display.
2. **PR 2 — `generate_signals.py`.** New standalone script, copy-pattern of `check_signals.py`: imports `scanner`, `signal_log`, `research_config` only — no `streamlit` import, so it runs in a bare Actions runner.
3. **PR 3 — `.github/workflows/generate_signals.yml`.** Same shape as `check_signals.yml`: schedule, checkout, install deps, run script, commit `research_learning.db`, push.
4. **PR 4 — Dry-run in parallel.** Enable the new workflow with `workflow_dispatch` only (no cron yet) for a few manual runs. Compare its output rows in `live_trades` against a manual "Run Live Scan" click for the same watchlist/time. Captain America signs off on parity before cron is enabled.
5. **PR 5 — Enable cron** on `generate_signals.yml`.
6. **PR 6 — `app.py` Scanner tab switch-over.** Default view reads the latest batch from `live_trades` via a new `signal_log.get_latest_signal_batch()`; "Run Live Scan" becomes an explicit "Force Rescan" button for ad-hoc use, still fully functional, just no longer the primary path.
7. **PR 7 (optional, Phase-1 prep) — `market_regime` column.** Add `migration_003` and start writing the Regime Engine's output into `live_trades` from `generate_signals.py`, since Phase 1 (Historical Warehouse) will want it available without recomputation.

Each PR is independently revertible (§9), and steps 1–3 ship with **zero visible change** to the app — only step 6 changes what the user sees.

---

## 6. Code Changes

### 6a. `scanner.py` — remove the Streamlit coupling

```python
# Before (line ~214):
            except Exception as e:
                st.error(f"{name} Error: {e}")
                continue

# After:
            except Exception as e:
                all_results.append({"Instrument": name, "Error": str(e)})
                continue
```
(Adjust to match the exact surrounding dict shape used elsewhere in the loop — `full_df` already tolerates partial/error rows for display, so this keeps that contract intact.) Also drop the now-unused `import streamlit as st` at the top of the file once this is the only reference.

### 6b. `generate_signals.py` (new file, backend job)

```python
"""
generate_signals.py

Standalone script — run independently of the Streamlit app, on a schedule,
via GitHub Actions (see .github/workflows/generate_signals.yml).

Zero Streamlit dependency by design, same pattern as check_signals.py:
runs the exact same scan + scoring logic the "Run Live Scan" button uses,
writes approved signals to research_learning.db's live_trades table, and
pushes the updated DB back to the repo. The Streamlit app then just reads
the latest batch — it never recomputes.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from scanner import run_scanner
from signal_log import append_new_signals, push_research_db_to_github
# reuse check_signals.py's Telegram sender rather than duplicating it —
# import it directly if it's a top-level function there, e.g.:
# from check_signals import send_telegram_message

def main():
    # Commodity contracts: generate_signals.py runs headless, so it can't
    # read the UI's expiry dropdown. Use the same "current front-month"
    # resolution logic upstox_client already exposes for get_market_trend,
    # or pass commodity_contracts=None if run_scanner() already resolves
    # front-month internally when nothing is passed.
    top5_df, full_df = run_scanner(commodity_contracts=None)

    if full_df is None or full_df.empty:
        print("No scan results — nothing to persist this run.")
        return

    new_count = append_new_signals(full_df)
    print(f"Persisted {new_count} new signal(s) to live_trades.")

    if new_count:
        push_research_db_to_github(
            commit_message=f"Add {new_count} new signal(s) [automated]"
        )
        # send_telegram_message(...) for each new BUY/SELL — reuse the
        # existing helper from check_signals.py rather than rewriting it.

if __name__ == "__main__":
    main()
```

*Note: `append_new_signals` currently expects the shape `app.py` passes it after a scan — confirm the exact `full_df` vs `top5_df` argument before merging PR 2; I've flagged it rather than guessing, since getting this wrong would silently duplicate or skip rows.*

### 6c. `.github/workflows/generate_signals.yml` (new file)

```yaml
name: Generate Trading Signals

on:
  schedule:
    # Every 30 min during NSE/MCX trading hours (IST 9:00–23:30), Mon–Fri.
    - cron: "5,35 3-18 * * 1-5"   # offset 5 min from check_signals.yml
  workflow_dispatch: {}

permissions:
  contents: write

jobs:
  generate-signals:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.11"
      - run: pip install -r requirements.txt
      - name: Run signal generator
        env:
          UPSTOX_ACCESS_TOKEN: ${{ secrets.UPSTOX_ACCESS_TOKEN }}
          TELEGRAM_BOT_TOKEN: ${{ secrets.TELEGRAM_BOT_TOKEN }}
          TELEGRAM_CHAT_ID: ${{ secrets.TELEGRAM_CHAT_ID }}
        run: python generate_signals.py
```

*(Offset by 5 minutes from `check_signals.yml`'s `0,30` schedule so the two jobs don't race on committing the same `.db` file.)*

### 6d. `signal_log.py` — new read function for the UI

```python
def get_latest_signal_batch():
    """
    Reads the most recent scan batch from live_trades (grouped by the
    latest `timestamp` value) for the Scanner tab's default view.
    Read-only — no computation, no network calls.
    """
    db = _open_db()
    latest_ts = db.get_latest_signal_timestamp()  # add alongside get_open_live_trades()
    return db.get_signals_at_timestamp(latest_ts)
```

Once PRs 1–5 are merged and validated, `app.py`'s Scanner tab calls `get_latest_signal_batch()` by default instead of requiring a click — this is the actual "instant load" the brief asks for.

---

## 7. Validation Plan

Owned by Captain America, gated before each cron-enabling step:

1. **Parity check (PR 4):** for a manual `workflow_dispatch` run of `generate_signals.py`, compare resulting `live_trades` rows against a same-minute manual "Run Live Scan" click — same instruments, same scores, same entry/SL/T1/T2 within float tolerance.
2. **Idempotency check:** running `generate_signals.py` twice in the same scan window shouldn't double-write rows (confirm `append_new_signals`'s existing dedupe/`signal_id` uniqueness handles this — it should, since `signal_id` is `UNIQUE` in the schema).
3. **Failure isolation:** if `generate_signals.py` fails mid-run (API timeout, etc.), confirm no partial/corrupt commit — same pattern `check_signals.yml` already handles via `git diff --staged --quiet`.
4. **Telegram parity:** new-signal alerts fire once per signal, not duplicated by both the backend job and a manual Force Rescan on the same signal.
5. **UI regression:** Scanner tab, Performance tab, Settings tab all render correctly reading from `live_trades` with no scan having been manually triggered in that session.
6. **Sign-off:** Captain America issues GO/NO-GO on enabling cron (step 5) and again on the `app.py` switch-over (step 6), same gating pattern as the existing warehouse NO-GO.

---

## 8. Performance Comparison (Before vs. After)

| Metric | Before | After |
|---|---|---|
| Time to first render on app open | Instant (already cached/no auto-scan) | Instant — unchanged, but now shows **fresh** data without a click |
| Time from "I want current signals" to seeing them | Full scan duration (network + CPU + GitHub push, blocking the UI thread) on every click | ~0 — reads a DB row; freshness bounded by the 15–30 min job interval instead of by clicking |
| UI thread blocking during scan | Yes — full Streamlit request blocked | No — scan runs in Actions runner, decoupled from any user session |
| Concurrent users triggering redundant scans | Possible — every user's click re-runs the full scan independently | Eliminated — one shared scan feeds all users |
| GitHub API push latency exposed to user | Yes, inline with the click | No — happens in the background job |

*(Flash should attach actual measured numbers from `_timed()` instrumentation already sitting in `app.py` once PR 6 ships — that instrumentation is already in the codebase and ready to capture this.)*

---

## 9. Rollback Plan

Because every step is additive and the existing click-to-scan path is preserved as "Force Rescan," rollback is low-risk at every stage:

- **PR 1–3 (scanner cleanup, new script, new workflow):** revert the PR; nothing else depends on them yet.
- **PR 5 (cron enabled):** disable the workflow from the Actions tab (or revert the cron line) — `check_signals.py` and the app's manual scan path are completely unaffected.
- **PR 6 (`app.py` switch-over):** this is the only user-visible step. Revert reinstates "Run Live Scan" as the default/only path — no data loss, since `live_trades` rows written by the backend job remain valid and readable either way.
- **Data safety:** all writes go through the same `append_new_signals` / `push_research_db_to_github` functions already in production use by the manual path — no new write path, no new corruption surface.

---

## 10. Final Architecture Diagram

```
                    ┌─────────────────────────────┐
                    │   GitHub Actions (scheduled) │
                    │                              │
                    │  generate_signals.py         │
                    │   scanner.run_scanner()       │
                    │   signal_logic scoring        │
                    │   market_engine (regime/conf) │
                    │   signal_log.append_new_...  │
                    └───────────────┬──────────────┘
                                    │ commits + pushes
                                    ▼
                    ┌─────────────────────────────┐
                    │ research_learning.db          │
                    │  (live_trades table)          │
                    │  — the central DB, already    │
                    │    exists, git-persisted      │
                    └───────────────┬──────────────┘
                                    │ read-only
                                    ▼
                    ┌─────────────────────────────┐
                    │  Streamlit app.py             │
                    │   signal_log.get_latest_...   │  ← instant, no compute
                    │   risk_engine (per-user sizing)│  ← cheap, stays here
                    │   charts.py / ui_components.py │
                    │   [Force Rescan] ← optional,   │
                    │     still calls run_scanner()  │
                    └─────────────────────────────┘

     (unchanged, parallel job)
     check_signals.py — every 30 min — checks OPEN trades → target/SL →
     updates live_trades → Telegram alert on close
```

---

## Open items for The Watcher before PR 1 ships

1. Confirm `run_scanner(commodity_contracts=None)` correctly resolves MCX front-month contracts headlessly, or whether `generate_signals.py` needs its own expiry-resolution call (there's dropdown-driven logic in `app.py` for this today that a headless job can't reuse as-is).
2. Confirm whether `append_new_signals` expects `top5_df` or `full_df` — I flagged this rather than guessing (§6b).
3. Decide cron offset/frequency for `generate_signals.yml` relative to `check_signals.yml` to avoid commit races on the same `.db` file.

Once those three are resolved, PRs 1–3 have no open questions and can ship first since they're invisible to the running app.
