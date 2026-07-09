"""
ng-signal-app — Streamlit entry point.

This file is now a thin orchestrator: it wires together the tabs (Scanner /
Performance / Settings) using functions imported from the modules below. It
should NOT be where you add new indicator math, scoring logic, API calls, or
CSS — each of those has its own home now:

    signal_logic.py    — indicator math + signal scoring (shared with backtest.py)
    config.py          — secrets, constants, sector map, commodity list
    watchlist.py       — the hardcoded NSE watchlist + sector lookup
    upstox_client.py   — all Upstox (and instrument-master) API calls
    signal_log.py      — signal_log.csv read/write/push + performance analytics
    scanner.py         — the run_scanner() scan loop
    charts.py          — the per-instrument Plotly chart builder
    ui_components.py   — CSS injection, stat cards, badges, opportunity/hero cards

If you need to tune a threshold or add a scoring factor, edit signal_logic.py.
If you need to add a watchlist symbol, edit watchlist.py. If you need a
colors/fonts/spacing tweak, edit dashboard.css. This file should mostly only
change when the TAB LAYOUT itself changes.
"""



import streamlit as st
import pandas as pd
from datetime import datetime

# ============================================================
# TEMPORARY TIMING INSTRUMENTATION (this session) — covers the
# Admin Center tab from get_admin_kpis() through render_warehouse_center(),
# i.e. everything that is NOT already instrumented inside
# warehouse_admin/render.py. No logic changed anywhere below; only
# `with _timed(...)` wrappers and prints were added. Remove `_timed`,
# `_now_iso`, `import time`, and every `with _timed(...)` block once the
# slow stage is identified.
# ============================================================
import time as _time
from contextlib import contextmanager as _contextmanager
from datetime import datetime as _dt

_SLOW_THRESHOLD_MS = 50.0

_APP_RERUN_T0 = _time.perf_counter()


def _now_iso():
    return _dt.now().isoformat(timespec="milliseconds")


@_contextmanager
def _timed(label):
    _t0 = _time.perf_counter()
    print(f"[TIMING START] {label} @ {_now_iso()}")
    try:
        yield
    finally:
        _ms = (_time.perf_counter() - _t0) * 1000
        _flag = "  <<< SLOW (>50ms)" if _ms > _SLOW_THRESHOLD_MS else ""
        print(f"[TIMING END] {label} = {_ms:.2f} ms{_flag} @ {_now_iso()}")


print(f"\n########## [app.py] TOTAL APP RERUN START @ {_now_iso()} ##########")
print(f"[TIMING START] APP: app initialization (imports + session_state defaults) @ {_now_iso()}")
_APP_INIT_T0 = _time.perf_counter()


# Admin
from admin_tools import weekly_summary

# Reports
from reports import (
    generate_weekly_report,
    generate_monthly_report,
    export_excel_report,
)
# Signal Log
from signal_log import (
    load_signal_log,
    get_admin_kpis,
    append_new_signals,
    compute_performance_summary,
    compute_factor_performance,
    compute_timing_stats,
)

# Config
from config import IST, COMMODITY_DEFINITIONS, SECTOR_ORDER

# Risk Config (NGSP-003)
from risk_config import (
    ACCOUNT_SIZE_PRESETS, DEFAULT_ACCOUNT_SIZE,
    RISK_PER_TRADE_PRESETS_PCT, DEFAULT_RISK_PER_TRADE_PCT,
)

# Risk Engine (NGSP-003) — position sizing / risk management, decision
# support only. See risk_engine.py's module docstring: no broker
# integration, no order placement.
from risk_engine import generate_trade_summary

# Signal Logic
from signal_logic import (
    ADX_WEAK_BELOW,
    ADX_STRONG_AT_OR_ABOVE,
    MIN_EXPECTED_MOVE_PCT,
    MAX_EXPECTED_MOVE_PCT,
)

# Watchlist
from watchlist import get_watchlist

# Upstox
from upstox_client import (
    validate_watchlist_keys,
    get_commodity_contracts,
    get_candles_range,
)

# Scanner
from scanner import run_scanner
from scan_snapshot_reader import load_latest_scan_snapshot

# Charts
from charts import build_instrument_chart

# UI
from ui_components import (
    inject_dashboard_css,
    render_stat_cards,
    render_opportunity_card,
    render_hero_card,
    render_risk_card,
    render_risk_unavailable_card,
    style_signal_column,
    render_validation_summary,
)

# Validation Center (NGSP-003B.1) — single public entry point, per
# validation/__init__.py's "only expose run_validation()" rule.
from validation import run_validation

# Warehouse Operations Center (NGWH-003) — single public entry point, per
# warehouse_admin/__init__.py's "only expose render_warehouse_center()" rule.
# Fully independent of Scanner/Performance/Settings — reads/writes only its
# own warehouse data, never touches signal_log, watchlist, or scan state.
from warehouse_admin import render_warehouse_center

# NOTE: all indicator math and signal-scoring logic lives in signal_logic.py,
# NOT here — it's imported so this app and backtest.py (the offline threshold
# tester) always run the exact same scoring code. If you need to tune a
# threshold or add a factor, edit signal_logic.py, not this file.

# ================= UI =================

inject_dashboard_css()

st.markdown('<div class="dash-title">📊 ng-signal-app</div>', unsafe_allow_html=True)
st.markdown('<div class="dash-sub">NSE intraday scanner — live signal dashboard</div>', unsafe_allow_html=True)

if "scan_count" not in st.session_state:
    st.session_state.scan_count = 0
if "last_scan" not in st.session_state:
    st.session_state.last_scan = "Never"

_app_init_ms = (_time.perf_counter() - _APP_INIT_T0) * 1000
_app_init_flag = "  <<< SLOW (>50ms)" if _app_init_ms > _SLOW_THRESHOLD_MS else ""
print(f"[TIMING END] APP: app initialization (imports + session_state defaults) = {_app_init_ms:.2f} ms{_app_init_flag} @ {_now_iso()}")

tab_scanner, tab_performance, tab_settings, tab_admin = st.tabs([
    "📡 Scanner",
    "📈 Performance",
    "⚙️ Settings",
    "🛠️ Admin Center"
])

# =========================================================================
# SETTINGS TAB — runs FIRST in code (so commodity_contracts exists before
# the Scanner tab needs it) but still renders as the rightmost tab — tab
# placement comes from the st.tabs() label order above, not code order.
# =========================================================================

with tab_settings:

    st.markdown('<div class="section-eyebrow">Commodity Contract Selection</div>', unsafe_allow_html=True)

    commodity_contracts = {}

    if not COMMODITY_DEFINITIONS:
        st.caption("No commodities configured.")
    else:
        cols = st.columns(len(COMMODITY_DEFINITIONS))

        for idx, (display_name, symbol_filter) in enumerate(COMMODITY_DEFINITIONS):
            with cols[idx]:
                result = get_commodity_contracts(symbol_filter, max_contracts=4)
                contracts = result["contracts"]

                if result["error"]:
                    st.error(f"{display_name}: {result['error']}")
                    continue

                if not contracts:
                    st.warning(f"No live {display_name} futures contracts found.")
                    continue

                chosen_label = st.selectbox(
                    f"{display_name} expiry",
                    options=[c["label"] for c in contracts],
                    key=f"expiry_{symbol_filter}"
                )

                chosen = next(c for c in contracts if c["label"] == chosen_label)
                commodity_contracts[display_name] = chosen["key"]

    _watchlist_size = len(get_watchlist(commodity_contracts))
    st.caption(
        f"Scanning {_watchlist_size} instruments (NSE equities across 7 sectors"
        f"{' + selected MCX commodity contract(s)' if commodity_contracts else ''})."
    )

    st.markdown("---")
    st.markdown('<div class="section-eyebrow">Risk Management Settings (NGSP-003)</div>', unsafe_allow_html=True)
    st.caption(
        "These settings drive position sizing on every signal card and in "
        "exported reports — decision support only, this never places an order."
    )

    rc1, rc2 = st.columns(2)

    with rc1:
        account_preset = st.selectbox(
            "Account Size",
            options=[f"₹{p:,}" for p in ACCOUNT_SIZE_PRESETS] + ["Custom"],
            index=ACCOUNT_SIZE_PRESETS.index(DEFAULT_ACCOUNT_SIZE) if DEFAULT_ACCOUNT_SIZE in ACCOUNT_SIZE_PRESETS else 0,
            key="risk_account_preset",
        )
        if account_preset == "Custom":
            st.session_state.risk_account_size = st.number_input(
                "Custom Account Size (₹)", min_value=1.0,
                value=float(DEFAULT_ACCOUNT_SIZE), step=10000.0, key="risk_account_custom",
            )
        else:
            st.session_state.risk_account_size = float(account_preset.replace("₹", "").replace(",", ""))

    with rc2:
        risk_preset = st.selectbox(
            "Risk Per Trade",
            options=[f"{p}%" for p in RISK_PER_TRADE_PRESETS_PCT] + ["Custom"],
            index=RISK_PER_TRADE_PRESETS_PCT.index(DEFAULT_RISK_PER_TRADE_PCT) if DEFAULT_RISK_PER_TRADE_PCT in RISK_PER_TRADE_PRESETS_PCT else 0,
            key="risk_pct_preset",
        )
        if risk_preset == "Custom":
            st.session_state.risk_per_trade_pct = st.number_input(
                "Custom Risk Per Trade (%)", min_value=0.01, max_value=100.0,
                value=DEFAULT_RISK_PER_TRADE_PCT, step=0.1, key="risk_pct_custom",
            )
        else:
            st.session_state.risk_per_trade_pct = float(risk_preset.replace("%", ""))

    rc3, rc4 = st.columns(2)

    with rc3:
        lot_mode = st.radio("Lot Size", options=["Auto", "Manual Override"], key="risk_lot_mode", horizontal=True)
        if lot_mode == "Manual Override":
            st.session_state.risk_lot_override = st.number_input(
                "Manual Lot Size", min_value=1.0, value=1.0, step=1.0, key="risk_lot_override_input",
            )
        else:
            st.session_state.risk_lot_override = None

    with rc4:
        round_label = st.radio(
            "Round Quantity", options=["Nearest Lot", "Nearest Share"], key="risk_round_mode_widget", horizontal=True,
        )
        st.session_state.risk_round_mode = "nearest_lot" if round_label == "Nearest Lot" else "nearest_share"

    st.caption(
        f"Sizing every signal for a ₹{st.session_state.risk_account_size:,.0f} account at "
        f"{st.session_state.risk_per_trade_pct}% risk per trade "
        f"({'lot size: ' + str(st.session_state.risk_lot_override) if st.session_state.risk_lot_override else 'auto lot size'}, "
        f"rounding to {round_label.lower()})."
    )


    st.markdown('<div class="section-eyebrow">Watchlist Diagnostics</div>', unsafe_allow_html=True)

    with st.expander("🔍 Validate Watchlist Instrument Keys"):
        if st.button("Run validation check"):
            with st.spinner("Checking watchlist against Upstox's NSE instrument master..."):
                mismatches = validate_watchlist_keys(get_watchlist(commodity_contracts))

            if mismatches is None:
                st.error("Couldn't fetch Upstox's NSE instrument master file — try again in a moment.")
            elif not mismatches:
                st.success("✅ All hardcoded NSE equity instrument keys match Upstox's current master file.")
            else:
                st.warning(f"⚠️ {len(mismatches)} instrument key(s) don't match Upstox's current records:")
                st.dataframe(
                    pd.DataFrame(mismatches, columns=["Instrument", "Your Hardcoded Key", "Upstox's Current Key / Issue"]),
                    use_container_width=True,
                    hide_index=True
                )
                st.caption(
                    "For each row above, replace 'Your Hardcoded Key' with the value shown in "
                    "'Upstox's Current Key' inside get_watchlist() in watchlist.py."
                )

    st.markdown("---")
    st.markdown('<div class="section-eyebrow">How a Signal Becomes BUY/SELL</div>', unsafe_allow_html=True)

    st.markdown(
        f"""
A score ≥ 8 only becomes an actionable **BUY/SELL** if ALL of these also hold
— otherwise it's capped at **WATCH**, regardless of score:

- **Trend conviction ≥ 50%** — majority of available higher-context reads
  (Daily trend, Supertrend, Nifty market trend) agree with the instrument's
  own EMA20/50 trend. These three are blended into one conviction score
  rather than each independently vetoing the trade.
- **ADX ≥ {ADX_WEAK_BELOW}** — trend strength filter. Below this, price action
  is statistically closer to chop than a real trend (ADX ≥ {ADX_STRONG_AT_OR_ABOVE}
  gets a bonus as a strong trend).
- **ExpectedMove% between {MIN_EXPECTED_MOVE_PCT}% and {MAX_EXPECTED_MOVE_PCT}%** —
  volatility sanity filter. Below the floor usually means a dead/illiquid
  session; above the ceiling usually means a gap/news spike, not a tradeable trend.

All three checks are folded into the score and explained in each row's
"Reason" text (full detail in the underlying scan data and the "Best Trade
Setup" card on the Scanner tab) — or check `signal_logic.py` directly. These
thresholds are starting points, not fixed truths — tune them in
`signal_logic.py` once you've checked the Factor Performance Analysis
section (Performance tab) against real outcomes.
        """
    )

# =========================================================================
# SCANNER TAB
# =========================================================================

with tab_scanner:
    _scanner_tab_t0 = _time.perf_counter()
    print(f"[TIMING START] APP: Scanner tab render @ {_now_iso()}")

    run = st.button("🚀 Run Live Scan")

    # First load this session (no live scan clicked yet): read the last
    # background-generated batch (generate_signals.py, PR 6a) instead of
    # calling run_scanner() ourselves — this is the read path PR 6b adds.
    # This block only ever fills an empty session_state; it never runs
    # again once scan_top5_df/scan_full_df exist, so it can't clobber a
    # live scan result from later in the same session.
    if not run and "scan_full_df" not in st.session_state:
        snap_top5, snap_full, snap_scanned_at = load_latest_scan_snapshot()
        st.session_state.scan_top5_df = snap_top5
        st.session_state.scan_full_df = snap_full
        st.session_state.scan_source = "background" if snap_scanned_at else None
        st.session_state.last_scan = snap_scanned_at or "—"

    # Results are cached in st.session_state and reused across reruns
    # until the next deliberate "Run Live Scan" click — switching tabs,
    # filters, or the commodity dropdown does NOT silently re-trigger a
    # full scan (that would burn through Upstox's rate limit on normal use).
    if run:
        st.session_state.scan_count += 1
        st.session_state.last_scan = datetime.now(IST).strftime("%d-%m-%Y %H:%M:%S")
        st.session_state.scan_source = "live"
        with st.spinner("Scanning..."):
            with _timed("APP: run_scanner() call [see scanner.py's own internal breakdown in the same log for composition]"):
                _scan_top5, _scan_full = run_scanner(commodity_contracts)
            with _timed("APP: session state update [scan_top5_df/scan_full_df assignment]"):
                st.session_state.scan_top5_df, st.session_state.scan_full_df = _scan_top5, _scan_full

    df = st.session_state.scan_top5_df
    full_df = st.session_state.scan_full_df

    # Log any new actionable (BUY/SELL) signals from this scan to signal_log.csv.
    if run and not full_df.empty:
        append_new_signals(full_df)
        # Must invalidate the cache added to load_signal_log() (see
        # signal_log.py) right here — the very next line re-reads the log
        # and has to see the signals just written, not a stale cache hit.
        load_signal_log.clear()

    _source_label = {
        "live": "Live Scan",
        "background": "Background Snapshot",
    }.get(st.session_state.get("scan_source"), "No scan yet")
    st.caption(
        f"Scans run: {st.session_state.scan_count} · Last scan: {st.session_state.last_scan} "
        f"· Source: {_source_label}"
    )

    if full_df.empty:
        st.warning("No data returned from scanner. Click 'Run Live Scan' above, or check the API error banners if one was just attempted.")
    else:
        # ---- Position sizing (NGSP-003): one summary function reused by
        # every card below, driven by the Settings-tab session_state values
        # set earlier in this file. open_signal_count is read once per
        # render (not per card) since it's the same "how many trades are
        # already open" number for every candidate this scan.
        _log_df = load_signal_log()
        _open_count = int((_log_df["status"] == "OPEN").sum()) if not _log_df.empty else 0

        _ACTIONABLE_SIGNALS = ("BUY", "SELL")

        def _risk_summary_for_row(row):
            return generate_trade_summary(
                instrument_name=row["Instrument"],
                signal=row["Signal"],
                entry=row["Price"],
                stop_loss=row["SL"],
                target1=row["T1"],
                target2=row["T2"],
                confidence_pct=row["Prob%"],
                regime=row["Regime"],
                technical_score=row["Score"],
                account_size=st.session_state.risk_account_size,
                risk_per_trade_pct=st.session_state.risk_per_trade_pct,
                lot_size_override=st.session_state.risk_lot_override,
                round_mode=st.session_state.risk_round_mode,
                open_signal_count=_open_count,
            )

        def _render_position_sizing(row):
            """
            THE fix for the WATCH/ValueError bug: every call site below
            goes through this one gate instead of calling
            generate_trade_summary() directly. risk_engine.py stays
            strict (BUY/SELL only, by design — see its module docstring)
            and is never touched here; this function is purely the UI
            traffic cop deciding whether it's even appropriate to call it.
            Routes WATCH/NO TRADE/anything else to the informational card
            instead of the sizing card, so the Best Trade Setup slot (which
            scanner.py's top5_df deliberately allows to be a WATCH row) can
            never again reach generate_trade_summary() with a non-
            actionable signal.
            """
            if row["Signal"] in _ACTIONABLE_SIGNALS:
                render_risk_card(_risk_summary_for_row(row))
            else:
                render_risk_unavailable_card(row["Instrument"], row["Signal"])

        # ---- BUY / SELL opportunity cards (today's actionable picks) ----
        buy_df = df[df["Signal"] == "BUY"]
        sell_df = df[df["Signal"] == "SELL"]

        if not buy_df.empty:
            st.markdown('<div class="section-eyebrow">🟢 Buy Opportunities</div>', unsafe_allow_html=True)
            for _, row in buy_df.iterrows():
                render_opportunity_card(row)
                with st.expander(f"💰 Position Sizing — {row['Instrument']}"):
                    _render_position_sizing(row)

        if not sell_df.empty:
            st.markdown('<div class="section-eyebrow">🔴 Sell Opportunities</div>', unsafe_allow_html=True)
            for _, row in sell_df.iterrows():
                render_opportunity_card(row)
                with st.expander(f"💰 Position Sizing — {row['Instrument']}"):
                    _render_position_sizing(row)

        if not df.empty:
            render_hero_card(df.iloc[0])
            with st.expander(f"💰 Position Sizing — {df.iloc[0]['Instrument']} (Best Setup)", expanded=True):
                _render_position_sizing(df.iloc[0])
        elif buy_df.empty and sell_df.empty:
            st.info("No strong setups (score ≥ 7, actionable) found this scan.")

        st.markdown("---")

        # ---- Full Scanned Universe ----
        st.markdown('<div class="section-eyebrow">Full Scanned Universe</div>', unsafe_allow_html=True)

        fcol1, fcol2, fcol3 = st.columns(3)

        with fcol1:
            signal_filter = st.multiselect(
                "Signal",
                options=sorted(full_df["Signal"].unique()),
                default=list(sorted(full_df["Signal"].unique()))
            )

        with fcol2:
            confidence_filter = st.multiselect(
                "Confidence",
                options=sorted(full_df["Confidence"].unique()),
                default=list(sorted(full_df["Confidence"].unique()))
            )

        with fcol3:
            min_score = st.slider("Minimum Score", min_value=0, max_value=10, value=0)

        filtered_df = full_df[
            (full_df["Signal"].isin(signal_filter)) &
            (full_df["Confidence"].isin(confidence_filter)) &
            (full_df["Score"] >= min_score)
        ].reset_index(drop=True)

        display_cols = [
            "Instrument", "Signal", "Confidence", "Trend", "DailyTrend", "Supertrend",
            "MarketTrend", "ConvictionPct", "ADX", "Regime",
            "Score", "Prob%", "RSI", "Volume", "Volume Ratio",
            "ExpectedMove%", "RR", "Price", "SL", "T1", "T2"
        ]

        st.caption(f"Showing {len(filtered_df)} of {len(full_df)} scanned instruments. "
                   f"👇 Expand a sector and click a row to view its chart below.")

        selected_name = None
        selected_key = None

        for sector in SECTOR_ORDER:
            sector_df = filtered_df[filtered_df["Sector"] == sector].reset_index(drop=True)

            if sector_df.empty:
                continue

            with st.expander(f"{sector}  ·  {len(sector_df)} instrument(s)", expanded=False):
                styled = style_signal_column(sector_df[display_cols].style)
                sel_event = st.dataframe(
                    styled,
                    use_container_width=True,
                    hide_index=True,
                    on_select="rerun",
                    selection_mode="single-row",
                    key=f"universe_table_{sector}"
                )

                rows = sel_event.selection.get("rows", []) if sel_event else []
                if rows:
                    selected_row = sector_df.iloc[rows[0]]
                    selected_name = selected_row["Instrument"]
                    selected_key = selected_row.get("InstrumentKey", "")

        # ---- Instrument chart (shown when a row is clicked above) ----
        if selected_name:
            selected_idx_lookup = filtered_df[filtered_df["Instrument"] == selected_name]
            if not selected_idx_lookup.empty:
                selected_row = selected_idx_lookup.iloc[0]
                selected_key = selected_row.get("InstrumentKey", selected_key)

            st.markdown("---")
            st.markdown(f'<div class="section-eyebrow">📊 {selected_name} — Chart & Indicators</div>', unsafe_allow_html=True)

            if not selected_key:
                st.warning("No instrument key available for this row — can't fetch chart data.")
            else:
                with st.spinner(f"Loading {selected_name} chart..."):
                    chart_candles = get_candles_range(selected_key, days_back=7)

                if not chart_candles:
                    st.warning(
                        f"Could not load chart data for {selected_name}. "
                        f"This can happen outside market hours, on holidays, or if the "
                        f"range endpoint isn't available for this instrument type."
                    )
                else:
                    fig = build_instrument_chart(selected_name, chart_candles)
                    if fig:
                        st.plotly_chart(fig, use_container_width=True)
                    else:
                        st.info(f"Not enough candle history yet to chart {selected_name}.")

    _scanner_tab_ms = (_time.perf_counter() - _scanner_tab_t0) * 1000
    _scanner_tab_flag = "  <<< SLOW (>50ms)" if _scanner_tab_ms > _SLOW_THRESHOLD_MS else ""
    print(f"[TIMING END] APP: Scanner tab render = {_scanner_tab_ms:.2f} ms{_scanner_tab_flag} @ {_now_iso()}")

# =========================================================================
# PERFORMANCE TAB
# =========================================================================

with tab_performance:
    _performance_tab_t0 = _time.perf_counter()
    print(f"[TIMING START] APP: Performance tab render @ {_now_iso()}")

    st.markdown('<div class="section-eyebrow">📈 Signal Performance (Historical)</div>', unsafe_allow_html=True)

    signal_log_df = load_signal_log()

    if signal_log_df.empty:
        st.info(
            "No signal history yet. As BUY/SELL signals are generated, they're logged "
            "automatically. Win rate and P&L% will appear here once signals have been "
            "checked against price (handled by the scheduled outcome-checker — see setup notes)."
        )
    else:
        open_count = (signal_log_df["status"] == "OPEN").sum()
        per_instrument, overall = compute_performance_summary(signal_log_df)

        if overall is None:
            st.info(
                f"{open_count} signal(s) currently OPEN, none closed yet. "
                f"Performance stats appear once signals hit their target or stop loss."
            )
        else:
            render_stat_cards([
                ("Closed Trades", overall["total_trades"], "default"),
                ("Win Rate", f"{overall['win_rate_pct']}%", "default"),
                ("Avg P&L / Trade", f"{overall['avg_pnl_pct']}%", "pos" if overall['avg_pnl_pct'] >= 0 else "neg"),
                ("Currently Open", int(open_count), "default"),
            ])

            st.markdown("**Per-Instrument Breakdown**")
            st.dataframe(
                per_instrument.rename(columns={
                    "instrument": "Instrument",
                    "Trades": "Trades",
                    "Wins": "Wins",
                    "WinRate_Pct": "Win Rate %",
                    "AvgPnL_Pct": "Avg P&L %"
                }),
                use_container_width=True,
                hide_index=True
            )

        with st.expander("View raw signal log"):
            st.dataframe(signal_log_df, use_container_width=True, hide_index=True)

    st.markdown("---")

    st.markdown('<div class="section-eyebrow">⏱️ Historical Timing Engine</div>', unsafe_allow_html=True)
    st.caption(
        "How long closed signals actually took to resolve — purely observational, "
        "computed from timestamps already in the log. T1/SL timing work from day one; "
        "T2 timing fills in over time as check_signals.py read-only-tracks signals "
        "for a few days after they close at T1 (see its module docstring — it never "
        "manages or re-opens a position, just records whether/when T2 was also touched)."
    )

    timing_stats = compute_timing_stats(signal_log_df) if not signal_log_df.empty else {"t1": None, "t2": None, "sl": None}

    if not any(timing_stats.values()):
        st.info("No closed signals yet — timing stats appear once signals start hitting T1 or SL.")
    else:
        timing_cards = []
        for key, label in [("t1", "Avg Time to T1"), ("t2", "Avg Time to T2"), ("sl", "Avg Time to SL")]:
            stat = timing_stats[key]
            if stat:
                timing_cards.append((f"{label} (n={stat['n']})", f"{stat['avg_hours']}h", "default"))
            else:
                timing_cards.append((label, "—", "default"))
        render_stat_cards(timing_cards)
        st.caption(
            "n = number of historical signals each average is based on — treat any "
            "stat with n under ~20 as too small a sample to plan around yet."
        )

    st.markdown("---")

    st.markdown('<div class="section-eyebrow">🔬 Factor Performance Analysis</div>', unsafe_allow_html=True)
    st.caption(
        "Does each scoring factor actually correlate with wins, using YOUR real "
        "outcomes — not assumed from the hand-tuned scoring weights. Needs closed "
        "trades to populate; treat any breakdown under ~20 closed trades as too "
        "small a sample to act on yet."
    )

    factor_perf = compute_factor_performance(signal_log_df) if not signal_log_df.empty else {}

    if not factor_perf:
        st.info(
            "No closed trades with factor data yet. This fills in automatically as "
            "BUY/SELL signals logged from now on get checked against target/stop "
            "by the outcome-checker. Signals logged before this update won't have "
            "factor data — only new ones do."
        )
    else:
        for label, factor_df in factor_perf.items():
            st.markdown(f"**{label}**")
            st.dataframe(
                factor_df.rename(columns={
                    "Trades": "Trades", "Wins": "Wins",
                    "WinRate_Pct": "Win Rate %", "AvgPnL_Pct": "Avg P&L %"
                }),
                use_container_width=True,
                hide_index=True
            )

    _performance_tab_ms = (_time.perf_counter() - _performance_tab_t0) * 1000
    _performance_tab_flag = "  <<< SLOW (>50ms)" if _performance_tab_ms > _SLOW_THRESHOLD_MS else ""
    print(f"[TIMING END] APP: Performance tab render = {_performance_tab_ms:.2f} ms{_performance_tab_flag} @ {_now_iso()}")
    # =========================================================================
# ADMIN CENTER
# =========================================================================

print(f"\n########## [app.py] ADMIN CENTER (post-tabs) block START @ {_now_iso()} ##########")
_admin_block_t0 = _time.perf_counter()

with _timed("get_admin_kpis() [cached load_signal_log(), local DB]"):
    kpis = get_admin_kpis()

with _timed("st.columns(4) + 4x st.metric() [admin KPI row]"):
    c1, c2, c3, c4 = st.columns(4)

    with c1:
        st.metric("Total Trades", kpis["total_trades"])

    with c2:
        st.metric("Win Rate %", f"{kpis['win_rate']}%")

    with c3:
        st.metric("Avg P&L %", kpis["avg_pnl"])

    with c4:
        st.metric("Open Trades", kpis["open_trades"])


with tab_admin:
    _admin_tab_t0 = _time.perf_counter()
    print(f"[TIMING START] APP: Admin tab render @ {_now_iso()}")

    st.markdown(
        '<div class="section-eyebrow">🛠️ Signal Pro Admin Center</div>',
        unsafe_allow_html=True
    )

    st.info(
        "Developer tools, reports and diagnostics will be managed from here."
    )

    col1, col2 = st.columns(2)

    # =====================================================
    # REPORTS
    # =====================================================

    with col1:

        # -----------------------------
        # WEEKLY REPORT
        # -----------------------------
        if st.button("📊 Weekly Report"):

            week_df, summary = generate_weekly_report()

            if week_df.empty:

                st.info("No signals found in the last 7 days.")

            else:

                st.success(f"{len(week_df)} signals found.")

                rc1, rc2, rc3, rc4 = st.columns(4)

                with rc1:
                    st.metric("Total Trades", summary["total_trades"])

                with rc2:
                    st.metric("Closed Trades", summary["closed_trades"])

                with rc3:
                    st.metric("Win Rate", f"{summary['win_rate']}%")

                with rc4:
                    st.metric("Avg P&L", f"{summary['avg_pnl']}%")

                st.dataframe(
                    week_df,
                    use_container_width=True,
                    hide_index=True,
                )

                excel_file = export_excel_report(week_df, account_size=st.session_state.risk_account_size, risk_per_trade_pct=st.session_state.risk_per_trade_pct)

                st.download_button(
                    "📥 Download Weekly Excel Report",
                    data=excel_file,
                    file_name="SignalPro_Weekly_Report.xlsx",
                    mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                )

        # -----------------------------
        # MONTHLY REPORT
        # -----------------------------
        if st.button("📈 Monthly Report"):

            month_df, summary = generate_monthly_report()

            if month_df.empty:

                st.info("No signals found in the last 30 days.")

            else:

                st.success(f"{len(month_df)} signals found.")

                rc1, rc2, rc3, rc4 = st.columns(4)

                with rc1:
                    st.metric("Total Trades", summary["total_trades"])

                with rc2:
                    st.metric("Closed Trades", summary["closed_trades"])

                with rc3:
                    st.metric("Win Rate", f"{summary['win_rate']}%")

                with rc4:
                    st.metric("Avg P&L", f"{summary['avg_pnl']}%")

                st.dataframe(
                    month_df,
                    use_container_width=True,
                    hide_index=True,
                )

                excel_file = export_excel_report(month_df, account_size=st.session_state.risk_account_size, risk_per_trade_pct=st.session_state.risk_per_trade_pct)

                st.download_button(
                    "📥 Download Monthly Excel Report",
                    data=excel_file,
                    file_name="SignalPro_Monthly_Report.xlsx",
                    mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                )

        st.button("📉 Performance Report", disabled=True)

    # =====================================================
    # TOOLS
    # =====================================================

    with col2:

        st.button("🧪 Run Backtest", disabled=True)

        # -----------------------------
        # DIAGNOSTICS — Validation Center (NGSP-003B.1)
        # -----------------------------
        if st.button("🔍 Diagnostics"):
            with st.spinner("Running Validation Center checks (Application, Database, Dashboard, Configuration)..."):
                with _timed("*** run_validation() [click-triggered, includes validate_warehouse()] ***"):
                    st.session_state.validation_summary = run_validation()

        st.button("🗂️ Data Management", disabled=True)

    # Validation results render full-width below both columns, not
    # squeezed into col2, since there's a lot to show once it's run.
    with _timed("check: 'validation_summary' in st.session_state"):
        _has_validation_summary = "validation_summary" in st.session_state
    if _has_validation_summary:
        print(f"[STATE]         validation_summary IS present in session_state -- re-rendering on THIS rerun too")
        st.markdown("---")
        st.markdown(
            '<div class="section-eyebrow">🔍 Validation Center Report</div>',
            unsafe_allow_html=True
        )
        with _timed("*** render_validation_summary() [re-renders on EVERY rerun if summary persists] ***"):
            render_validation_summary(st.session_state.validation_summary)
    else:
        print(f"[STATE]         validation_summary NOT present in session_state -- Diagnostics was never clicked this session")

    # =====================================================
    # WAREHOUSE OPERATIONS CENTER (NGWH-003)
    # =====================================================
    # Fully self-contained — independent of Scanner/Performance/Settings
    # state above. A failure here (e.g. warehouse not yet initialized)
    # cannot affect the rest of the Admin Center or any other tab; see
    # render_warehouse_center()'s own try/except around bootstrap.
    #
    # PR 7E: gated behind explicit user action. render_warehouse_center()
    # used to run unconditionally on every single Streamlit rerun — including
    # ones triggered by clicking "Run Live Scan" in the Scanner tab, since
    # st.tabs() executes every tab's body regardless of which tab is visible
    # (see PR 7A/7D). PR 7D's production logs showed ~44s+ of dead time
    # starting inside get_warehouse_handles() on exactly such a rerun. Gating
    # this behind a checkbox means Warehouse code — bootstrap, DuckDB, all 5
    # sub-tabs — only executes when someone deliberately opens this section,
    # never as a side effect of using the Scanner.
    st.markdown("---")
    _load_warehouse = st.checkbox("🏛️ Load Warehouse Operations Center", key="load_warehouse_center")
    if _load_warehouse:
        with _timed("*** render_warehouse_center() [instrumented internally in warehouse_admin/render.py] ***"):
            render_warehouse_center()
    else:
        st.caption("Warehouse Operations Center is not loaded. Check the box above to initialize it.")

    _admin_tab_ms = (_time.perf_counter() - _admin_tab_t0) * 1000
    _admin_tab_flag = "  <<< SLOW (>50ms)" if _admin_tab_ms > _SLOW_THRESHOLD_MS else ""
    print(f"[TIMING END] APP: Admin tab render = {_admin_tab_ms:.2f} ms{_admin_tab_flag} @ {_now_iso()}")

_admin_block_total_ms = (_time.perf_counter() - _admin_block_t0) * 1000
_admin_flag = "  <<< SLOW" if _admin_block_total_ms > _SLOW_THRESHOLD_MS else ""
print(f"########## [app.py] ADMIN CENTER (post-tabs) block END @ {_now_iso()}  TOTAL={_admin_block_total_ms:9.2f} ms{_admin_flag} ##########\n")

_app_rerun_total_ms = (_time.perf_counter() - _APP_RERUN_T0) * 1000
_app_rerun_flag = "  <<< SLOW (>1000ms)" if _app_rerun_total_ms > 1000 else ""
print(f"[TIMING END] APP: total app rerun = {_app_rerun_total_ms:.2f} ms{_app_rerun_flag} @ {_now_iso()}")
print(f"########## [app.py] TOTAL APP RERUN END @ {_now_iso()} ##########\n")
