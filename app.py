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
from admin_tools import (
    generate_weekly_report,
    weekly_summary
)
from admin import generate_weekly_report
from signal_log import load_signal_log, get_admin_kpis
from reports import (
    generate_weekly_report,
    weekly_report_excel
)

from config import IST, COMMODITY_DEFINITIONS, SECTOR_ORDER
from signal_logic import (
    ADX_WEAK_BELOW, ADX_STRONG_AT_OR_ABOVE,
    MIN_EXPECTED_MOVE_PCT, MAX_EXPECTED_MOVE_PCT,
)
from watchlist import get_watchlist
from upstox_client import validate_watchlist_keys, get_commodity_contracts, get_candles_range
from signal_log import (
    load_signal_log, append_new_signals,
    compute_performance_summary, compute_factor_performance,
    compute_timing_stats,
)
from scanner import run_scanner
from charts import build_instrument_chart
from ui_components import (
    inject_dashboard_css, render_stat_cards, render_opportunity_card,
    render_hero_card, style_signal_column,
)

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

    run = st.button("🚀 Run Live Scan")

    # Results are cached in st.session_state and reused across reruns
    # until the next deliberate "Run Live Scan" click — switching tabs,
    # filters, or the commodity dropdown does NOT silently re-trigger a
    # full scan (that would burn through Upstox's rate limit on normal use).
    if run:
        st.session_state.scan_count += 1
        st.session_state.last_scan = datetime.now(IST).strftime("%d-%m-%Y %H:%M:%S")
        with st.spinner("Scanning..."):
            st.session_state.scan_df, st.session_state.scan_full_df = run_scanner(commodity_contracts)

    if "scan_full_df" not in st.session_state:
        st.session_state.scan_df = pd.DataFrame()
        st.session_state.scan_full_df = pd.DataFrame()

    df = st.session_state.scan_df
    full_df = st.session_state.scan_full_df

    # Log any new actionable (BUY/SELL) signals from this scan to signal_log.csv.
    if run and not full_df.empty:
        append_new_signals(full_df)

    st.caption(f"Scans run: {st.session_state.scan_count} · Last scan: {st.session_state.last_scan}")

    if full_df.empty:
        st.warning("No data returned from scanner. Click 'Run Live Scan' above, or check the API error banners if one was just attempted.")
    else:
        # ---- BUY / SELL opportunity cards (today's actionable picks) ----
        buy_df = df[df["Signal"] == "BUY"]
        sell_df = df[df["Signal"] == "SELL"]

        if not buy_df.empty:
            st.markdown('<div class="section-eyebrow">🟢 Buy Opportunities</div>', unsafe_allow_html=True)
            for _, row in buy_df.iterrows():
                render_opportunity_card(row)

        if not sell_df.empty:
            st.markdown('<div class="section-eyebrow">🔴 Sell Opportunities</div>', unsafe_allow_html=True)
            for _, row in sell_df.iterrows():
                render_opportunity_card(row)

        if not df.empty:
            render_hero_card(df.iloc[0])
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

# =========================================================================
# PERFORMANCE TAB
# =========================================================================

with tab_performance:

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
    # =========================================================================
# ADMIN CENTER
# =========================================================================

df = load_signal_log()
kpis = get_admin_kpis()

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

    st.markdown(
        '<div class="section-eyebrow">🛠️ Signal Pro Admin Center</div>',
        unsafe_allow_html=True
    )

    st.info(
        "Developer tools, reports and diagnostics will be managed from here."
    )

    col1, col2 = st.columns(2)

    with col1:

        if st.button("📊 Weekly Report"):

            report = generate_weekly_report()

            if report.empty:
                st.info("No signals found in the last 7 days.")
            else:
                summary = weekly_summary(report)

                st.success(f"{len(report)} signals found.")

                if summary:
                    c1, c2, c3, c4 = st.columns(4)

                    c1.metric("Total Signals", summary["Total Signals"])
                    c2.metric("BUY / SELL", f"{summary['BUY']} / {summary['SELL']}")
                    c3.metric("Target / SL", f"{summary['Target Hit']} / {summary['Stop Loss']}")
                    c4.metric("Avg P&L %", summary["Avg P&L"])

                st.dataframe(
                    report,
                    use_container_width=True,
                    hide_index=True
                )

                excel_file = weekly_report_excel(report)

                st.download_button(
                    "📥 Download Weekly Report (.xlsx)",
                    data=excel_file,
                    file_name="weekly_report.xlsx",
                    mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )

        st.button("📈 Monthly Report", disabled=True)
        st.button("📉 Performance Report", disabled=True)

    with col2:

        st.button("🧪 Run Backtest", disabled=True)
        st.button("🔍 Diagnostics", disabled=True)
        st.button("🗂️ Data Management", disabled=True)
