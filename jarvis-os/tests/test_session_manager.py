"""
Tests for jarvis.interface.session.SessionManager: open, close, reset,
idle timeout.
"""

from __future__ import annotations

import time

import pytest

from jarvis.interface.session import SessionError, SessionManager, SessionStatus


def test_open_session_creates_active_session():
    manager = SessionManager()
    session = manager.open_session()

    assert session.status is SessionStatus.OPEN
    assert manager.current is session


def test_cannot_open_second_session_while_one_active():
    manager = SessionManager()
    manager.open_session()

    with pytest.raises(SessionError):
        manager.open_session()


def test_close_session():
    manager = SessionManager()
    manager.open_session()
    manager.close_session()

    assert manager.current.status is SessionStatus.CLOSED


def test_close_without_open_raises():
    manager = SessionManager()
    with pytest.raises(SessionError):
        manager.close_session()


def test_can_reopen_after_close():
    manager = SessionManager()
    first = manager.open_session()
    manager.close_session()
    second = manager.open_session()

    assert second.session_id != first.session_id
    assert second.status is SessionStatus.OPEN


def test_reset_session_clears_current_task():
    manager = SessionManager()
    manager.open_session()
    manager.set_current_task(task="fake-task-placeholder")  # type: ignore[arg-type]

    new_session = manager.reset_session()

    assert new_session.current_task is None
    assert manager.get_current_task() is None


def test_touch_updates_last_activity():
    manager = SessionManager()
    session = manager.open_session()
    original = session.last_activity
    time.sleep(0.01)
    manager.touch()

    assert session.last_activity != original


def test_idle_detection_triggers_after_timeout():
    manager = SessionManager(idle_timeout_seconds=0.05)
    manager.open_session()

    assert manager.check_idle() is False  # not idle yet
    time.sleep(0.1)
    assert manager.check_idle() is True


def test_touch_clears_idle_status():
    manager = SessionManager(idle_timeout_seconds=0.05)
    manager.open_session()
    time.sleep(0.1)
    assert manager.check_idle() is True

    manager.touch()
    assert manager.current.status is SessionStatus.OPEN


def test_operations_without_session_raise():
    manager = SessionManager()
    with pytest.raises(SessionError):
        manager.touch()
    with pytest.raises(SessionError):
        manager.check_idle()
    with pytest.raises(SessionError):
        manager.get_current_task()
