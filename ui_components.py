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
