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


def _validation_status_pill_html(status):
    """Small colored pill for a ValidationStatus — same visual language
    (pill shape, bold weight) as signal_badge_html()/trade_quality_badge_html()
    above, using inline styles so this doesn't require any dashboard.css
    changes to work."""
    label = status.value if hasattr(status, "value") else str(status)
    colors = {
        "PASS": ("#DCFCE7", "#166534"),
        "WARNING": ("#FEF3C7", "#92400E"),
        "FAIL": ("#FFE1E6", "#9F1239"),
        "SKIPPED": ("#F3F1F7", "#6B7280"),
    }
    bg, fg = colors.get(label, ("#F3F1F7", "#6B7280"))
    return (
        f'<span style="background:{bg};color:{fg};font-weight:700;'
        f'padding:2px 12px;border-radius:999px;font-size:0.8rem;'
        f'display:inline-block;">{label}</span>'
    )


def render_validation_summary(summary):
    """
    Renders a validation.validation_models.ValidationSummary (the return
    value of validation.run_validation()) as a full dashboard panel:
    overall health score + readiness verdict, a per-category status strip,
    and an expander per category with that validator's details/warnings/
    failures/skipped items/metrics.

    Purely a rendering function — matches the rest of this file's pattern
    (e.g. render_risk_card() for risk_engine's output). It does not run
    any checks itself and does not import from validation/ at module load
    time, so ui_components.py stays usable even in contexts where the
    validation package isn't relevant — the import happens lazily here,
    only when this function is actually called.
    """
    from validation.validation_models import ValidationCategory
    from validation.validation_report import build_report_text

    _OVERALL_STYLE = {
        "READY": ("✅", "#166534"),
        "READY WITH WARNINGS": ("⚠️", "#92400E"),
        "NOT READY": ("🛑", "#9F1239"),
    }
    icon, color = _OVERALL_STYLE.get(summary.overall_status.value, ("ℹ️", "#1C1B22"))

    top1, top2 = st.columns([1, 2])
    with top1:
        st.metric("System Health", f"{summary.health_score.percent:.0f}%")
    with top2:
        st.markdown(
            f'<div style="padding-top:14px;font-size:1.15rem;font-weight:700;color:{color};">'
            f'{icon} {summary.overall_status.value}</div>',
            unsafe_allow_html=True,
        )

    _CATEGORY_ORDER = [
        ValidationCategory.APPLICATION,
        ValidationCategory.DATABASE,
        ValidationCategory.DASHBOARD,
        ValidationCategory.CONFIGURATION,
        ValidationCategory.WAREHOUSE,
    ]

    st.markdown("")
    cat_cols = st.columns(len(_CATEGORY_ORDER))
    for col, category in zip(cat_cols, _CATEGORY_ORDER):
        result = summary.result_for(category)
        with col:
            st.markdown(f"**{category.value}**")
            if result is None:
                st.markdown(_validation_status_pill_html("SKIPPED"), unsafe_allow_html=True)
                st.caption("Not run.")
            else:
                st.markdown(_validation_status_pill_html(result.status), unsafe_allow_html=True)
                st.caption(result.summary)

    st.markdown("---")

    for category in _CATEGORY_ORDER:
        result = summary.result_for(category)
        if result is None:
            continue
        with st.expander(f"{category.value} — details"):
            if result.details:
                for d in result.details:
                    st.markdown(f"✓ {d}")
            for w in result.warnings:
                st.warning(w)
            for f in result.failures:
                st.error(f)
            for s in result.skipped:
                st.info(f"Skipped: {s}")
            if result.metrics:
                st.json(result.metrics)
            if not (result.details or result.warnings or result.failures or result.skipped or result.metrics):
                st.caption("No additional detail reported.")

    with st.expander("📄 Full text report (with recommendations)"):
        st.code(build_report_text(summary), language=None)
    
