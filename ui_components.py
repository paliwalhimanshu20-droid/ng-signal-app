"""
Small reusable rendering helpers for the dashboard's visual layer: CSS
injection, stat cards, signal badges, opportunity cards, the "Best Trade
Setup" hero card, and the Signal-column table styling.

Design system: light minimalist base (calm, generous spacing, restrained
color) + bold accent color used SPARINGLY on signature elements only
(active tab, conviction bars, the Best Setup hero card's edge) — not
splashed everywhere. The goal was "modern and easy to explain", not
maximalist.

Matching .streamlit/config.toml (separate file, same repo root) should
set: base="light", primaryColor="#8B5CF6", backgroundColor="#FAFAF8",
secondaryBackgroundColor="#F3F1F7", textColor="#1C1B22".
"""

import streamlit as st


def inject_dashboard_css():
    """
    Loads styling from dashboard.css (same repo folder as app.py) and
    injects it into the page.

    DELIBERATELY kept in a separate file: this is the ONLY thing you
    should need to touch for a colors/fonts/spacing tweak going forward —
    not any of the .py files. Replace dashboard.css alone in GitHub's
    web editor and redeploy; no code changes needed.
    """
    try:
        with open("dashboard.css", "r") as f:
            css = f.read()
        st.markdown(f"<style>{css}</style>", unsafe_allow_html=True)
    except FileNotFoundError:
        # Minimal fallback so the app isn't unstyled-white if dashboard.css
        # hasn't been added to the repo yet — the real styling lives in
        # dashboard.css now, this is just a safety net.
        st.markdown(
            "<style>.stApp, [data-testid='stAppViewContainer']{background-color:#15121F !important;}</style>",
            unsafe_allow_html=True
        )
        st.warning(
            "dashboard.css not found in the repo root — using minimal fallback "
            "styling. Add dashboard.css alongside app.py to restore the full look."
        )


def render_stat_cards(items):
    """items: list of (label, value_str, kind) where kind in 'default'/'pos'/'neg'."""
    cards_html = "".join(
        f'<div class="stat-card"><div class="label">{label}</div>'
        f'<div class="value {kind if kind != "default" else ""}">{value}</div></div>'
        for label, value, kind in items
    )
    st.markdown(f'<div class="stat-grid">{cards_html}</div>', unsafe_allow_html=True)


def signal_badge_html(signal):
    cls = {"BUY": "buy", "SELL": "sell", "WATCH": "watch"}.get(signal, "none")
    label = signal if signal else "N/A"
    return f'<span class="sig-badge {cls}">{label}</span>'


def trade_quality_badge_html(quality):
    """Same visual language as signal_badge_html — small pill, not a
    second competing style system. See dashboard.css's .quality-badge.*"""
    cls = {
        "Excellent": "excellent", "Good": "good",
        "Average": "average", "Poor": "poor",
    }.get(quality, "none")
    label = quality if quality else "N/A"
    return f'<span class="quality-badge {cls}">{label}</span>'


def render_opportunity_card(row):
    st.markdown(
        f"""
        <div class="opp-card">
          <div class="opp-top">
            <span class="opp-name">{row['Instrument']}</span>
            {signal_badge_html(row['Signal'])}
          </div>
          <div class="opp-metrics">
            <span>Price <b>{row['Price']}</b></span>
            <span>Confidence <b>{row['Prob%']}%</b></span>
            <span>RSI <b>{row['RSI']}</b></span>
            <span>Volume <b>{row['Volume']}</b></span>
            <span>RR <b>{row['RR']}</b></span>
          </div>
        </div>
        """,
        unsafe_allow_html=True
    )


def render_hero_card(best):
    """The single 'signature' element of this design — see module docstring
    above. Everything else stays deliberately calm."""
    conviction = best.get("ConvictionPct", "N/A")
    conviction_pct_num = conviction if isinstance(conviction, (int, float)) else 0

    st.markdown(
        f"""
        <div class="hero-card">
          <div class="hero-eyebrow">Best Trade Setup</div>
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <span class="hero-name">{best['Instrument']}</span>
            {signal_badge_html(best['Signal'])}
          </div>
          <div class="hero-metrics">
            <div><div class="m-label">Score</div><div class="m-value">{best['Score']}/10</div></div>
            <div><div class="m-label">Confidence</div><div class="m-value">{best['Prob%']}%</div></div>
            <div><div class="m-label">RSI</div><div class="m-value">{best['RSI']}</div></div>
            <div><div class="m-label">RR</div><div class="m-value">{best['RR']}</div></div>
          </div>
          <div class="conv-label"><span>Trend Conviction</span><span>{conviction}{'%' if conviction != 'N/A' else ''}</span></div>
          <div class="conv-track"><div class="conv-fill" style="width:{conviction_pct_num}%"></div></div>
          <div class="levels-row">
            <span>Entry <b>{best['Price']}</b></span>
            <span>SL <b>{best['SL']}</b></span>
            <span>T1 <b>{best['T1']}</b></span>
            <span>T2 <b>{best['T2']}</b></span>
          </div>
          <div class="hero-reason">{best['Reason']}</div>
        </div>
        """,
        unsafe_allow_html=True
    )


def render_risk_unavailable_card(instrument_name, signal):
    """
    Shown in place of render_risk_card() for non-actionable signals
    (WATCH, NO TRADE, or anything that isn't BUY/SELL). risk_engine's
    generate_trade_summary()/calculate_position_size() deliberately
    raise ValueError for a non-BUY/SELL signal — sizing a trade that
    hasn't confirmed is meaningless, and the engine is supposed to stay
    strict about that. This function is the UI-side guard: callers must
    check the signal type themselves and route WATCH/NO TRADE here
    instead of calling generate_trade_summary() at all. Same card shell
    as render_risk_card() so it doesn't look like a broken/error state.
    """
    label = signal if signal else "NO TRADE"
    st.markdown(
        f"""
        <div class="risk-card risk-card-unavailable">
          <div class="risk-top">
            <span class="risk-name">{instrument_name} — Position Sizing</span>
            {signal_badge_html(signal)}
          </div>
          <div class="risk-unavailable-msg">
            Position sizing is available only for actionable BUY and SELL signals.
            "{label}" is not yet a confirmed trade — waiting for a BUY/SELL signal
            before calculating position size.
          </div>
        </div>
        """,
        unsafe_allow_html=True,
    )


def render_risk_card(summary):
    """
    summary: the dict returned by risk_engine.generate_trade_summary().
    Renders the Position Sizing & Risk Management (NGSP-003) breakdown
    for one signal — quantity, capital required, RR, exposure, portfolio
    heat, and a Trade Quality badge — using the same visual language as
    render_opportunity_card()/render_hero_card() (see dashboard.css's
    .risk-card.* rules), not a new competing style.

    Purely a rendering function — it does not compute anything itself,
    and it never places or suggests placing an order; see
    risk_engine.py's module docstring.
    """
    if not summary["is_valid"]:
        st.markdown(
            f"""
            <div class="risk-card risk-card-invalid">
              <div class="risk-top">
                <span class="risk-name">{summary['instrument']} — Position Sizing</span>
              </div>
              <div class="risk-invalid-msg">⚠ {summary['invalid_reason']}</div>
            </div>
            """,
            unsafe_allow_html=True,
        )
        return

    warnings_html = "".join(f'<div class="risk-warning">⚠ {w}</div>' for w in summary["warnings"])

    def _fmt_money(v):
        return f"₹{v:,.2f}" if v is not None else "N/A"

    st.markdown(
        f"""
        <div class="risk-card">
          <div class="risk-top">
            <span class="risk-name">{summary['instrument']} — Position Sizing</span>
            {trade_quality_badge_html(summary['trade_quality'])}
          </div>
          <div class="risk-metrics">
            <div><div class="rm-label">Quantity</div><div class="rm-value">{summary['quantity']:,}</div></div>
            <div><div class="rm-label">Capital Required</div><div class="rm-value">{_fmt_money(summary['capital_required'])}</div></div>
            <div><div class="rm-label">Max Risk</div><div class="rm-value">{_fmt_money(summary['max_risk_amount'])}</div></div>
            <div><div class="rm-label">RR (T1)</div><div class="rm-value">{summary['rr_t1'] if summary['rr_t1'] is not None else 'N/A'}</div></div>
          </div>
          <div class="risk-metrics">
            <div><div class="rm-label">Profit @ T1</div><div class="rm-value pos">{_fmt_money(summary['potential_profit_t1'])}</div></div>
            <div><div class="rm-label">Profit @ T2</div><div class="rm-value pos">{_fmt_money(summary['potential_profit_t2'])}</div></div>
            <div><div class="rm-label">Exposure</div><div class="rm-value">{summary['exposure_pct']}% ({summary['exposure_tier']})</div></div>
            <div><div class="rm-label">Portfolio Risk</div><div class="rm-value">{summary['portfolio_risk_pct']}% ({summary['portfolio_risk_tier']})</div></div>
          </div>
          {warnings_html}
        </div>
        """,
        unsafe_allow_html=True,
    )


def style_signal_column(styler, signal_col="Signal"):
    """
    Applies a light background tint to the Signal column of a scanner
    table so BUY/SELL/WATCH are scannable at a glance, without losing
    st.dataframe's row-selection (on_select) behavior — only background-
    color is touched, which Streamlit's dataframe renderer supports
    alongside selection. (One thing that could not be verified end-to-end
    without a live Streamlit runtime — if row-click-to-chart ever stops
    working after this change, removing this .style.map() call is the fix.)
    """
    def _color(val):
        return {
            "BUY": "background-color: #DCFCE7; color: #166534; font-weight: 700;",
            "SELL": "background-color: #FFE1E6; color: #9F1239; font-weight: 700;",
            "WATCH": "background-color: #FEF3C7; color: #92400E; font-weight: 700;",
        }.get(val, "")

    if signal_col in styler.data.columns:
        return styler.map(_color, subset=[signal_col])
    return styler
    
