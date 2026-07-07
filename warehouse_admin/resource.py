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

=== TEMPORARY DIAGNOSTIC EXPERIMENT (this session) ===
@st.cache_resource is COMMENTED OUT below, on this one function, for a
single diagnostic pass. Nothing else changed — same body, same timing
prints as the instrumented version from the previous step, so the two
runs are directly comparable.

*** REAL SIDE EFFECT OF THIS CHANGE (expected, not a bug) ***
Without the decorator, get_warehouse_handles() is no longer cached at
all. WarehouseBootstrap(config).run() will now execute in FULL on every
single rerun of the entire app — every widget interaction anywhere,
including unrelated tabs — not just once per process. Expect to see
"Bootstrapping warehouse" / "Metadata DuckDB connection opened" fire
repeatedly, once per rerun, in the logs. Each rerun also constructs a
brand-new DuckDBManager with a brand-new metadata connection; the
previous rerun's DuckDBManager/connection object is simply discarded
(nothing calls .close() on it), so old connections are only cleaned up
whenever Python's garbage collector gets to them — acceptable for a
short diagnostic session, not something to leave deployed. This is a
temporary experiment to isolate the cache_resource boundary, not a
permanent change — revert to the @st.cache_resource-decorated version
immediately after this test.

Put the decorator back (uncomment it) before doing anything else with
this file.
"""

from __future__ import annotations

import time as _time
from datetime import datetime as _dt

import streamlit as st

from warehouse import WarehouseConfig, load_config
from warehouse.bootstrap import WarehouseBootstrap, WarehouseHandles
from warehouse.downloader import DownloaderConfig


def _now_iso() -> str:
    return _dt.now().isoformat(timespec="milliseconds")


# @st.cache_resource(show_spinner="Initializing Historical Intelligence Warehouse...")
# ^^^ TEMPORARILY DISABLED FOR THIS DIAGNOSTIC PASS ONLY — see module
# docstring. Put this back immediately after the experiment.
def get_warehouse_handles() -> WarehouseHandles:
    """
    Bootstrap (or re-fetch the cached, already-bootstrapped) WarehouseHandles.

    Cached for the lifetime of the Streamlit process — a restart/redeploy
    re-bootstraps (idempotent, per NGWH-001), a rerun within the same
    session reuses the same handles and DuckDB connection.

    [DIAGNOSTIC PASS: caching is currently disabled — see module docstring.
    This docstring describes the NORMAL, cached behavior; while the
    decorator above is commented out, every call re-executes the full
    body below instead.]
    """
    _t_entry = _time.perf_counter()
    print(f"[TIMING] get_warehouse_handles(): FUNCTION BODY ENTERED           @ {_now_iso()}  [CACHE DISABLED]")

    config = load_config()
    _t_after_load_config = _time.perf_counter()
    print(f"[TIMING] get_warehouse_handles(): AFTER load_config()             @ {_now_iso()}  "
          f"(+{(_t_after_load_config - _t_entry) * 1000:.2f} ms since entry)")

    print(f"[TIMING] get_warehouse_handles(): BEFORE WarehouseBootstrap(config).run()  @ {_now_iso()}")
    _t_before_run = _time.perf_counter()
    result = WarehouseBootstrap(config).run()
    _t_after_run = _time.perf_counter()
    print(f"[TIMING] get_warehouse_handles(): AFTER run() returned            @ {_now_iso()}  "
          f"(run() itself took {(_t_after_run - _t_before_run) * 1000:.2f} ms)")

    print(f"[TIMING] get_warehouse_handles(): result type = {type(result).__name__}, "
          f"id = {id(result)}")

    _t_before_return = _time.perf_counter()
    print(f"[TIMING] get_warehouse_handles(): BEFORE OWN return statement     @ {_now_iso()}  "
          f"(+{(_t_before_return - _t_after_run) * 1000:.2f} ms since run() returned, "
          f"+{(_t_before_return - _t_entry) * 1000:.2f} ms total inside this function)")

    return result


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
    NOT part of normal page rendering.

    [DIAGNOSTIC PASS: with caching disabled above, get_warehouse_handles()
    has no .clear() method — this function is effectively inert until the
    decorator is restored. Left as-is rather than modified, per "don't
    change any other logic."]
    """
    try:
        handles = get_warehouse_handles()
        handles.duckdb_manager.close()
    except Exception:
        pass
    try:
        get_warehouse_handles.clear()
    except AttributeError:
        # Expected while the decorator is disabled — .clear() only exists
        # on a cache_resource-wrapped function. Not a real error; left
        # visible rather than silently working around it, since this
        # whole file is temporary.
        pass
