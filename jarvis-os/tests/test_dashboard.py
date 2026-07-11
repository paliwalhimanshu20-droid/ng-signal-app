"""
Tests for jarvis.interface.dashboard.SystemDashboard: healthy snapshot
and a component-failure scenario.
"""

from __future__ import annotations

from jarvis.interface.dashboard import SystemDashboard
from jarvis.interface.health import run_interface_health_check
from jarvis.interface.session import SessionManager
from jarvis.interface.command_parser import CommandParser
from jarvis.kernel import ExecutiveKernel


def _make_kernel(core) -> ExecutiveKernel:
    return ExecutiveKernel(constitution=core.constitution, registry=core.registry, audit_ledger=core.audit_ledger)


def test_dashboard_reports_healthy_when_everything_wired(booted_core):
    kernel = _make_kernel(booted_core)
    session_manager = SessionManager()
    session_manager.open_session()
    command_parser = CommandParser()

    dashboard = SystemDashboard()
    interface_health = run_interface_health_check(session_manager, command_parser)
    snapshot = dashboard.snapshot(booted_core, kernel, interface_health, session_manager)

    assert snapshot.overall_healthy is True
    assert snapshot.core_healthy is True
    assert snapshot.agent_count == 1

    rendered = dashboard.render(snapshot)
    assert "OVERALL STATUS:     HEALTHY" in rendered
    assert "Engineering Agent" in rendered


def test_dashboard_reports_unhealthy_when_no_session_active(booted_core):
    kernel = _make_kernel(booted_core)
    session_manager = SessionManager()  # deliberately not opened
    command_parser = CommandParser()

    dashboard = SystemDashboard()
    interface_health = run_interface_health_check(session_manager, command_parser)

    assert interface_health.healthy is False  # session_active check fails
    assert interface_health.checks["session_active"] is False


def test_dashboard_snapshot_reflects_session_state(booted_core):
    kernel = _make_kernel(booted_core)
    session_manager = SessionManager()
    session = session_manager.open_session()
    command_parser = CommandParser()

    dashboard = SystemDashboard()
    interface_health = run_interface_health_check(session_manager, command_parser)
    snapshot = dashboard.snapshot(booted_core, kernel, interface_health, session_manager)

    assert snapshot.session_id == session.session_id
    assert snapshot.session_status == "open"
