"""
warehouse_admin/resource.py

The one place the Streamlit app obtains a live WarehouseHandles bundle.
Uses st.cache_resource (not st.cache_data — a DuckDB connection can't be
pickled/copied, it must be the SAME object reused across reruns) so the
warehouse is bootstrapped exactly once per Streamlit session process, not
on every script rerun. This is the first use of st.cache_resource in this
app — st.cache_data is used elsewhere (upstox_client.py) for things that
ARE safe to copy (plain dicts/DataFrames); a live DB connection is not.

Nothing else in warehouse_admin/ should call WarehouseBootstrap directly —
route through get_warehouse_handles() so there's only ever one bootstrapped
instance per process.
"""

from __future__ import annotations

import streamlit as st

from warehouse import WarehouseConfig, load_config
from warehouse.bootstrap import WarehouseBootstrap, WarehouseHandles
from warehouse.downloader import DownloaderConfig


@st.cache_resource(show_spinner="Initializing Historical Intelligence Warehouse...")
def get_warehouse_handles() -> WarehouseHandles:
    """
    Bootstrap (or re-fetch the cached, already-bootstrapped) WarehouseHandles.

    Cached for the lifetime of the Streamlit process — a restart/redeploy
    re-bootstraps (idempotent, per NGWH-001), a rerun within the same
    session reuses the same handles and DuckDB connection.
    """
    config = load_config()
    return WarehouseBootstrap(config).run()


def get_downloader_config() -> DownloaderConfig:
    """
    Builds a DownloaderConfig from Streamlit session_state overrides (set
    by the Historical Downloader page's sidebar controls), falling back to
    DownloaderConfig's own defaults for anything not overridden.

    Kept intentionally simple (flat session_state keys, not a nested
    form) — the Historical Downloader page is the only writer of these
    keys; this is the only reader.
    """
    overrides = {}
    if "wh_max_parallel_downloads" in st.session_state:
        overrides["max_parallel_downloads"] = st.session_state.wh_max_parallel_downloads
    if "wh_retry_max_retries" in st.session_state or "wh_retry_backoff_base" in st.session_state:
        overrides["retry"] = {}
        if "wh_retry_max_retries" in st.session_state:
            overrides["retry"]["max_retries"] = st.session_state.wh_retry_max_retries
        if "wh_retry_backoff_base" in st.session_state:
            overrides["retry"]["backoff_base_seconds"] = st.session_state.wh_retry_backoff_base
    if "wh_rate_limit_rps" in st.session_state:
        overrides["rate_limit"] = {"requests_per_second": st.session_state.wh_rate_limit_rps}

    return DownloaderConfig(**overrides)


def reset_warehouse_cache() -> None:
    """Clears the cached WarehouseHandles (closing its DuckDB connection
    first). Used only by the 'Re-initialize Warehouse' diagnostics action —
    NOT part of normal page rendering."""
    try:
        handles = get_warehouse_handles()
        handles.duckdb_manager.close()
    except Exception:
        pass
    get_warehouse_handles.clear()
