"""
warehouse_admin/

NGWH-003 — Warehouse Integration & Operations Center.

Exposes the warehouse as a first-class module inside NG Signal Pro's
existing Streamlit app, built entirely on the frozen NGWH-001 (Historical
Warehouse Foundation) and NGWH-002 (Historical Downloader) packages —
nothing in this package modifies either.

Public surface (mirrors validation/__init__.py's "one function" pattern):

    from warehouse_admin import render_warehouse_center
    render_warehouse_center()   # call once, inside the Admin Center tab

Everything else here (stats.py, job_management.py, progress_monitor.py —
all streamlit-free logic; resource.py and the render_*.py / downloader_page.py
modules — the streamlit-dependent layer) is available directly for testing
or for a future page that wants finer-grained control.

Does NOT touch: scanner.py, signal_logic.py, risk_engine.py, strategy_lab/,
signal_log.py, or any trading/signal logic whatsoever.
"""

from warehouse_admin.render import render_warehouse_center

__all__ = ["render_warehouse_center"]
