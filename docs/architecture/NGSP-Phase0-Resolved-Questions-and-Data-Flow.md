# NG Signal Pro — Phase 0: Resolved Questions + Signal Data Flow Documentation

**Status:** Findings for Watcher validation before PR 1 · **Method:** verified against actual repo code, not inferred

One correction to my prior analysis up front: I'd said the Regime/Fundamental/Event/Confidence engines run inside the live scan request. That was wrong — I checked `scanner.py`'s imports directly and **`market_engine.py`, `market_data.py`, `event_engine.py`, `fundamental_engine.py` are not imported by `scanner.py`, `app.py`, or `check_signals.py` at all.** They're only imported by each other and by `test_market_engine.py`. They exist in the repo (NGSP-002) but are not wired into the live signal path today. I've corrected the data flow table below accordingly, and flagged it as a separate decision rather than assuming it belongs in `generate_signals.py`.

---

## Question 1 — Can `run_scanner()` resolve MCX front-month contracts headlessly?

**Yes, with one small addition to `generate_signals.py` — `run_scanner()` itself doesn't need to change.**

Traced the exact resolution path in `app.py` (Settings tab, lines ~180–202):

```python
result = get_commodity_contracts(symbol_filter, max_contracts=4)
contracts = result["contracts"]
chosen_label = st.selectbox(f"{display_name} expiry", options=[c["label"] for c in contracts], key=...)
chosen = next(c for c in contracts if c["label"] == chosen_label)
commodity_contracts[display_name] = chosen["key"]
```

Two facts settle this:

1. `get_commodity_contracts()`'s own docstring (`upstox_client.py`) states contracts are returned **"sorted by nearest expiry first."**
2. `st.selectbox` is called with no `index=` argument, so Streamlit defaults to **index 0** — i.e., on a fresh session, the UI's own default selection *is* the nearest-expiry contract. There's no dropdown-specific logic being applied — it's just picking element 0 of an already-sorted list.

So headless resolution is simply: call `get_commodity_contracts(symbol_filter, max_contracts=4)` for each entry in `COMMODITY_DEFINITIONS`, take `contracts[0]["key"]`, build the same `commodity_contracts` dict shape, and pass it into `run_scanner()` exactly as `app.py` does. No new logic in `scanner.py` or `upstox_client.py` — this is a ~6-line addition inside `generate_signals.py` only.

```python
from config import COMMODITY_DEFINITIONS
from upstox_client import get_commodity_contracts

def resolve_front_month_contracts():
    """Headless equivalent of app.py's Settings-tab expiry dropdown —
    picks nearest expiry (index 0) for each configured commodity,
    matching the UI's own default selection on a fresh session."""
    resolved = {}
    for display_name, symbol_filter in COMMODITY_DEFINITIONS:
        result = get_commodity_contracts(symbol_filter, max_contracts=4)
        if result["error"] or not result["contracts"]:
            continue  # same soft-skip behavior as the UI's st.warning/st.error branch
        resolved[display_name] = result["contracts"][0]["key"]
    return resolved
```

Note: this means the backend job always trades the **nearest** expiry. If you ever manually pick a further-out contract in the UI, that only affects the "Force Rescan" path — the scheduled job always uses front-month. Worth confirming that's the behavior you want; flagging rather than assuming.

---

## Question 2 — Does `append_new_signals()` consume `top5_df` or `full_df`?

**Confirmed via the actual call site: `full_df`.** `app.py` line 352:

```python
append_new_signals(full_df)
```

And it needs to be `full_df`, not `top5_df` — confirmed by reading `append_new_signals()`'s body in `signal_log.py`:

```python
actionable = scan_results_df[scan_results_df["Signal"].isin(["BUY", "SELL"])]
```

It filters the input down to actionable BUY/SELL rows itself. `top5_df` is a display-only slice (highest-scoring 5 for the dashboard's hero cards); a genuine BUY or SELL setup outside the top 5 would be silently dropped if `top5_df` were passed instead. **`generate_signals.py` must call `append_new_signals(full_df)`**, matching what's in my earlier draft — this confirms it rather than changing it.

---

## Question 3 — Avoiding commit races on the shared database

Checked every workflow's schedule and commit target:

| Workflow | Cron (UTC) | File(s) committed |
|---|---|---|
| `check_signals.yml` | `0,30 3-18 * * 1-5` | `data/research_learning.db` |
| `generate_signals.yml` (proposed) | *(to decide)* | `data/research_learning.db` — **same file** |
| `update_instrument_master.yml` | `0 2 * * 1-5` | `data/instrument_master.db`, `data/instrument_master_validation_report.json`, `data/validation_history.db` — different files, no conflict |
| `weekly_summary.yml` | `30 2 * * 6` | none (read-only report) |
| `migrate_csv_to_db.yml` | manual, one-time | `data/research_learning.db` — already run, `workflow_dispatch` only going forward, negligible ongoing risk |

**Only `check_signals.yml` and the new `generate_signals.yml` genuinely collide** — both write `research_learning.db`. Cron offset alone isn't a full fix, since GitHub Actions scheduled runs can be delayed several minutes under load, so two jobs "10 minutes apart" on paper can still overlap in practice. Recommended approach, kept intentionally simple per your instruction to avoid unnecessary architecture:

1. **Offset the schedules** (first line of defense, cheap): `check_signals.yml` stays at `0,30`; `generate_signals.yml` at `15,45`.
2. **Add a `concurrency` group to each workflow** so a workflow never overlaps *with itself* if a run is delayed/slow:
   ```yaml
   concurrency:
     group: research-db-writer
     cancel-in-progress: false
   ```
   Using the **same group name in both workflows** additionally prevents the two workflows from running simultaneously against each other — GitHub Actions queues the second one instead of racing it. This is the actual fix; the cron offset is just a courtesy on top.
3. **No custom locking script needed.** `concurrency:` is a native GitHub Actions feature and fully covers this — introducing a bespoke lock file or retry library would be the "unnecessary architectural change" you asked me to avoid. The existing `git diff --staged --quiet || git commit ... && git push` pattern already handles the case of "nothing changed," and with `concurrency` serializing the two workflows, a real push conflict (non-fast-forward) shouldn't occur at all. If you want a belt-and-suspenders retry anyway, it's a 3-line addition:
   ```yaml
   - name: Commit updated database
     run: |
       git config --global user.name "signal-bot"
       git config --global user.email "actions@github.com"
       git add data/research_learning.db
       git diff --staged --quiet || {
         git commit -m "..."
         git pull --rebase && git push
       }
   ```

This resolves Q3 with the minimum necessary change: one `concurrency:` block per workflow, shared group name.

---

## Current Signal Data Flow Documentation

`Market Data → Indicator Calculation → Signal Generation → Validation → Database → Telegram → Streamlit`

| Stage | Source file | Function(s) | Inputs → Outputs | Backend or UI | Bottlenecks / dependencies |
|---|---|---|---|---|---|
| **1. Market Data** | `upstox_client.py` | `get_prices_bulk(keys)`, `get_candles(key, label)`, `get_daily_trend(key, label)`, `get_market_trend()` | Watchlist instrument keys → LTPs, OHLCV candles, daily/market trend labels | **Backend** | Network-bound; `get_prices_bulk` is already batched (one call for all LTPs, not per-symbol) — good existing optimization. `get_market_trend()` and `get_daily_trend()` are additional per-scan calls. |
| **2. Indicator Calculation** | `signal_logic.py` | `ema()`, `atr()`, `rsi()`, `calculate_supertrend()`, `compute_adx()` | Candle series → EMA20/50, ATR, RSI, Supertrend trend, ADX | **Backend** | Pure CPU, no I/O. Runs once per watchlist symbol inside `scanner.py`'s loop — scales linearly with watchlist size. |
| **3. Signal Generation** | `signal_logic.py`, orchestrated by `scanner.py` | `signal_engine(...)`, `levels(...)` | Price + indicators + trend-agreement flags → signal (BUY/SELL/HOLD), score, conviction_pct, entry/SL/T1/T2 | **Backend** | Pure CPU. `scanner.py` passes `config.COMMODITY_RISK_PARAMS` for MCX instruments so equity-tuned thresholds don't wrongly gate commodity setups — already asset-class-aware, no change needed for headless use. |
| **3b. Regime/Fundamental/Event/Confidence** | `market_engine.py`, `event_engine.py`, `fundamental_engine.py` | `RegimeEngine`, `ConfidenceCalculator`, `MarketEngine` | Would take market/fundamental/event snapshots → regime label, confidence score | **Not currently in the live path** | **Correction from my last message:** verified these are not imported by `scanner.py`/`app.py`/`check_signals.py` — they're built (NGSP-002) but not wired in. Recommend leaving them out of `generate_signals.py` for Phase 0 (no functional change, matches "preserve current functionality") and treating wiring them in as a separate, explicitly-scoped decision — not bundled into this migration. |
| **4. Validation** | `signal_log.py` (inline, inside `append_new_signals`) | dedupe check (`instrument`+`signal`+`status==OPEN`), `SL`/`T1` != `"N/A"` gate | `full_df` actionable rows → filtered new-row list | **Backend** (needs to move with signal generation, since it's the gate before persistence) | This is the *actual* per-signal validation today — not the `validation/` package. |
| **4b. System-health validation** | `validation/validation_runner.py` | `run_validation()` | System state → HealthScore, OverallStatus across app/db/dashboard/config/warehouse/instrument-master | **UI Task, on-demand** | Explicitly documented in its own docstring as "NOT wired into app.py... standalone function" for automatic use — actually is imported once in `app.py` (Admin Center, on-demand). Unrelated to per-signal validation; no change needed for Phase 0. |
| **5. Database** | `signal_log.py`, `research_db/database.py` | `append_new_signals()`, `save_signal_log()`, `push_research_db_to_github()` | New rows → `live_trades` table write, then DB file committed + pushed to GitHub | **Backend** | Network round-trip to GitHub Contents API — this is the step most directly blocking the UI thread today, since it currently only runs after a user's click. |
| **6. Telegram** | `check_signals.py` | `send_telegram_message(text)` | Closed-trade text → Telegram API call | **Backend** (already correctly placed) | Currently only fires for outcome closures (target/SL hit) in `check_signals.py`. `generate_signals.py` will need its own call for *new*-signal alerts — recommend extracting `send_telegram_message` into a small shared module (e.g. `telegram_notify.py`) so both scripts call one implementation instead of duplicating it. This is a 1-function, zero-behavior-change extraction, not a new architectural layer. |
| **7. Streamlit** | `app.py`, `signal_log.py`, `charts.py`, `ui_components.py`, `risk_engine.py` | `load_signal_log()` (today), `get_latest_signal_batch()` (proposed), `generate_trade_summary()` | `live_trades` rows (+ live UI inputs for risk sizing) → rendered dashboard | **UI Task** | `risk_engine.generate_trade_summary()` correctly stays here — it's cheap arithmetic parameterized by live account-size/risk% widgets, not something a backend batch job can precompute per-user. |

### What actually changes vs. what's already correct

- **Stages 1–5 already exist and are already correctly factored** as Backend-shaped functions — `scanner.py` and `signal_log.py` don't need new logic, only a new *caller* (`generate_signals.py`) that isn't gated behind a UI click.
- **Stage 3b is explicitly out of scope for Phase 0** — including it would be exactly the kind of "unnecessary architectural change" you asked me to avoid, since it's not in the current live path at all.
- **Stage 6 gets one small, justified extraction** (shared Telegram helper) to avoid duplicating a function across two backend scripts.
- **Stage 7 is the only stage whose *caller* changes** in `app.py` (PR 6) — reading instead of computing.

---

## Updated `generate_signals.py` (final, incorporating both answers)

```python
"""
generate_signals.py

Standalone script — zero Streamlit dependency, same pattern as
check_signals.py. Run on a schedule via GitHub Actions
(.github/workflows/generate_signals.yml).

Resolves MCX front-month contracts the same way app.py's Settings tab
defaults to on a fresh session (nearest expiry — see get_commodity_contracts()'s
"sorted by nearest expiry first" contract), runs the same scan + scoring
scanner.py already uses for the "Run Live Scan" button, and persists
actionable signals via the same append_new_signals()/push_research_db_to_github()
functions the UI path already uses — full_df, not top5_df, since
append_new_signals() filters to BUY/SELL itself and a top-5 slice would
silently drop actionable setups outside the top 5.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import COMMODITY_DEFINITIONS
from upstox_client import get_commodity_contracts
from scanner import run_scanner
from signal_log import append_new_signals, load_signal_log, push_research_db_to_github
from telegram_notify import send_telegram_message  # extracted from check_signals.py


def resolve_front_month_contracts():
    resolved = {}
    for display_name, symbol_filter in COMMODITY_DEFINITIONS:
        result = get_commodity_contracts(symbol_filter, max_contracts=4)
        if result["error"] or not result["contracts"]:
            continue
        resolved[display_name] = result["contracts"][0]["key"]
    return resolved


def main():
    commodity_contracts = resolve_front_month_contracts()
    top5_df, full_df = run_scanner(commodity_contracts)

    if full_df is None or full_df.empty:
        print("No scan results — nothing to persist this run.")
        return

    before = load_signal_log()
    append_new_signals(full_df)          # confirmed: full_df, not top5_df
    after = load_signal_log()

    new_rows = after[~after["signal_id"].isin(before["signal_id"])]
    if new_rows.empty:
        print("Scan complete — no new actionable signals this run.")
        return

    push_research_db_to_github(
        commit_message=f"Add {len(new_rows)} new signal(s) [automated]"
    )
    for _, row in new_rows.iterrows():
        send_telegram_message(
            f"🆕 {row['signal']} — {row['instrument']}\n"
            f"Entry: {row['entry_price']} | SL: {row['sl']} | T1: {row['t1']} | T2: {row['t2']}\n"
            f"Confidence: {row['confidence']} | Score: {row['score']}"
        )
    print(f"Persisted and alerted on {len(new_rows)} new signal(s).")


if __name__ == "__main__":
    main()
```

## Updated `.github/workflows/generate_signals.yml` (with concurrency fix for Q3)

```yaml
name: Generate Trading Signals

on:
  schedule:
    - cron: "15,45 3-18 * * 1-5"   # offset from check_signals.yml's 0,30
  workflow_dispatch: {}

permissions:
  contents: write

concurrency:
  group: research-db-writer   # SAME group name must be added to check_signals.yml too
  cancel-in-progress: false

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
      - name: Commit updated database
        run: |
          git config --global user.name "signal-bot"
          git config --global user.email "actions@github.com"
          if [ -f data/research_learning.db ]; then
            git add data/research_learning.db
            git diff --staged --quiet || git commit -m "Add new signals [automated]"
            git push
          else
            echo "data/research_learning.db does not exist yet — nothing to commit."
          fi
```

**Also add the same `concurrency:` block to `check_signals.yml`** (same `group: research-db-writer`) — that's the one required change to the existing workflow, and it's additive/non-breaking.

---

## Revised PR sequence (folds in the above)

1. **PR 1** — `scanner.py`: remove the one `st.error` call (unchanged from before).
2. **PR 1b (new, small)** — extract `send_telegram_message` from `check_signals.py` into `telegram_notify.py`; update `check_signals.py`'s import; zero behavior change.
3. **PR 2** — `generate_signals.py` (final version above).
4. **PR 3** — `.github/workflows/generate_signals.yml` + add `concurrency:` block to `check_signals.yml`.
5. **PR 4** — dry-run via `workflow_dispatch`, Captain America parity check against a manual scan.
6. **PR 5** — enable cron.
7. **PR 6** — `app.py` Scanner tab: default to `get_latest_signal_batch()`, keep "Force Rescan" as manual override.

All open questions from the prior message are now resolved with evidence from the actual code. Ready for PR 1 whenever you confirm.
