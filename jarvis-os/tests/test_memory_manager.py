"""
Tests for jarvis.memory — Part 11 (Testing) coverage: Working, Session,
Conversation, Preference memory; persistence integrity already covered
in test_memory_persistence.py; recovery and health covered here too.
"""

from __future__ import annotations

import pytest

from jarvis.audit import AuditLedger
from jarvis.memory import MemoryManager
from jarvis.memory.models import ConversationRecord, MemoryValidationError, SessionMemoryRecord


@pytest.fixture()
def memory_manager(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    manager = MemoryManager(storage_dir=tmp_path / "memory", audit_ledger=ledger)
    manager.connect()
    return manager


# -- Working Memory ------------------------------------------------------------


def test_working_memory_store_update_delete(memory_manager):
    memory_manager.working.set_current_task({"task_id": "t-1"})
    assert memory_manager.working.get_current_task() == {"task_id": "t-1"}

    memory_manager.working.set_current_task({"task_id": "t-2"})
    assert memory_manager.working.get_current_task()["task_id"] == "t-2"

    memory_manager.working.set_variable("x", 42)
    assert memory_manager.working.get_variable("x") == 42
    memory_manager.working.delete_variable("x")
    assert memory_manager.working.get_variable("x") is None


def test_working_memory_clear(memory_manager):
    memory_manager.working.set_current_task({"task_id": "t-1"})
    memory_manager.working.set_pending_approval({"approval_id": "a-1"})
    memory_manager.working.clear()

    snapshot = memory_manager.working.snapshot()
    assert snapshot.current_task is None
    assert snapshot.pending_approval is None
    assert snapshot.temporary_variables == {}


# -- Session Memory --------------------------------------------------------------


def test_session_memory_persist_and_restore(memory_manager):
    record = SessionMemoryRecord(
        session_id="session-1",
        session_status="open",
        current_task={"task_id": "t-1"},
        pending_approval=None,
        workflow_position={"step": 2},
        last_activity="2026-01-01T00:00:00+00:00",
        console_state={"last_command": "status"},
    )
    memory_manager.save_session(record)

    loaded = memory_manager.load_session()
    assert loaded is not None
    assert loaded.session_id == "session-1"
    assert loaded.current_task == {"task_id": "t-1"}
    assert loaded.workflow_position == {"step": 2}


def test_session_memory_survives_new_manager_instance(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    storage_dir = tmp_path / "memory"

    manager1 = MemoryManager(storage_dir=storage_dir, audit_ledger=ledger)
    manager1.connect()
    manager1.save_session(
        SessionMemoryRecord(
            session_id="session-restart",
            session_status="open",
            current_task=None,
            pending_approval=None,
            workflow_position=None,
            last_activity="2026-01-01T00:00:00+00:00",
            console_state={},
        )
    )

    # Simulate process restart: a brand new MemoryManager over the same directory.
    manager2 = MemoryManager(storage_dir=storage_dir, audit_ledger=ledger)
    manager2.connect()
    loaded = manager2.load_session()
    assert loaded is not None
    assert loaded.session_id == "session-restart"


def test_session_memory_rejects_invalid_record(memory_manager):
    with pytest.raises(MemoryValidationError):
        memory_manager.save_session(
            SessionMemoryRecord(
                session_id="",
                session_status="open",
                current_task=None,
                pending_approval=None,
                workflow_position=None,
                last_activity="x",
                console_state={},
            )
        )


def test_clear_session_clears_both_session_and_working_memory(memory_manager):
    memory_manager.save_session(
        SessionMemoryRecord(
            session_id="session-1",
            session_status="open",
            current_task=None,
            pending_approval=None,
            workflow_position=None,
            last_activity="2026-01-01T00:00:00+00:00",
            console_state={},
        )
    )
    memory_manager.working.set_current_task({"task_id": "t-1"})

    memory_manager.clear_session()

    assert memory_manager.load_session() is None
    assert memory_manager.working.get_current_task() is None


# -- Conversation Memory --------------------------------------------------------------


def test_conversation_append_and_history(memory_manager):
    memory_manager.append_conversation(
        ConversationRecord.new(session_id="s-1", user_input="hello")
    )
    memory_manager.append_conversation(
        ConversationRecord.new(session_id="s-1", user_input="status")
    )

    history = memory_manager.conversations.history()
    assert [r.user_input for r in history] == ["hello", "status"]


def test_conversation_filtering_by_session(memory_manager):
    memory_manager.append_conversation(ConversationRecord.new(session_id="s-1", user_input="a"))
    memory_manager.append_conversation(ConversationRecord.new(session_id="s-2", user_input="b"))

    assert len(memory_manager.conversations.by_session("s-1")) == 1
    assert len(memory_manager.conversations.by_session("s-2")) == 1


def test_conversation_rejects_empty_user_input(memory_manager):
    with pytest.raises(MemoryValidationError):
        memory_manager.append_conversation(ConversationRecord.new(session_id="s-1", user_input=""))


def test_conversation_history_survives_restart(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    storage_dir = tmp_path / "memory"

    manager1 = MemoryManager(storage_dir=storage_dir, audit_ledger=ledger)
    manager1.connect()
    manager1.append_conversation(ConversationRecord.new(session_id="s-1", user_input="Hello"))
    manager1.append_conversation(
        ConversationRecord.new(session_id="s-1", user_input="Analyze GitHub repository")
    )

    manager2 = MemoryManager(storage_dir=storage_dir, audit_ledger=ledger)
    manager2.connect()
    history = manager2.conversations.history()
    assert [r.user_input for r in history] == ["Hello", "Analyze GitHub repository"]


# -- Preference Memory --------------------------------------------------------------


def test_preference_store_update_retrieve(memory_manager):
    memory_manager.set_preference("interface.theme", "dark")
    assert memory_manager.get_preference("interface.theme") == "dark"

    memory_manager.set_preference("interface.theme", "light")
    assert memory_manager.get_preference("interface.theme") == "light"


def test_preference_missing_key_returns_default(memory_manager):
    assert memory_manager.get_preference("does.not.exist", default="fallback") == "fallback"


def test_preference_survives_restart(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    storage_dir = tmp_path / "memory"

    manager1 = MemoryManager(storage_dir=storage_dir, audit_ledger=ledger)
    manager1.connect()
    manager1.set_preference("language", "en")

    manager2 = MemoryManager(storage_dir=storage_dir, audit_ledger=ledger)
    manager2.connect()
    assert manager2.get_preference("language") == "en"


# -- Recovery --------------------------------------------------------------------------


def test_recovery_with_no_prior_session(memory_manager):
    report = memory_manager.recover()
    assert report.succeeded
    assert not report.session_restored


def test_recovery_restores_pending_approval_and_current_task(memory_manager):
    memory_manager.save_session(
        SessionMemoryRecord(
            session_id="session-1",
            session_status="open",
            current_task={"task_id": "t-1"},
            pending_approval={"approval_id": "a-1"},
            workflow_position={"step": 1},
            last_activity="2026-01-01T00:00:00+00:00",
            console_state={},
        )
    )

    report = memory_manager.recover()
    assert report.succeeded
    assert report.session_restored
    assert report.pending_approval_restored
    assert report.current_task_restored
    assert memory_manager.working.get_pending_approval() == {"approval_id": "a-1"}
    assert memory_manager.working.get_current_task() == {"task_id": "t-1"}


def test_recovery_never_auto_resolves_pending_approval(memory_manager):
    """Recovery rehydrates the pending approval into Working Memory but must never itself mark it approved/executed."""
    memory_manager.save_session(
        SessionMemoryRecord(
            session_id="session-1",
            session_status="open",
            current_task=None,
            pending_approval={"approval_id": "a-1", "approved": None},
            workflow_position=None,
            last_activity="2026-01-01T00:00:00+00:00",
            console_state={},
        )
    )
    memory_manager.recover()
    restored = memory_manager.working.get_pending_approval()
    assert restored["approved"] is None


# -- Health --------------------------------------------------------------------------


def test_health_check_all_green_on_fresh_manager(memory_manager):
    report = memory_manager.health_check()
    assert report.healthy
    assert set(report.checks) == {
        "memory_manager",
        "working_memory",
        "session_memory",
        "conversation_memory",
        "preference_memory",
        "knowledge_memory",
        "persistence",
        "recovery",
    }
    assert all(report.checks.values())


def test_health_check_detects_corrupted_session_store(memory_manager):
    memory_manager.save_session(
        SessionMemoryRecord(
            session_id="session-1",
            session_status="open",
            current_task=None,
            pending_approval=None,
            workflow_position=None,
            last_activity="2026-01-01T00:00:00+00:00",
            console_state={},
        )
    )
    # Corrupt the underlying file directly, simulating disk corruption.
    session_file = memory_manager._persistence._base_dir / "session.json"
    session_file.write_text("{not valid json", encoding="utf-8")
    # Also corrupt the backup so fallback can't mask it, to test the FAIL path.
    backup_file = memory_manager._persistence._base_dir / "session.json.bak"
    if backup_file.exists():
        backup_file.write_text("{also not valid", encoding="utf-8")

    report = memory_manager.health_check()
    assert not report.checks["session_memory"]
    assert not report.healthy


def test_manager_not_connected_reports_unhealthy(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    manager = MemoryManager(storage_dir=tmp_path / "memory", audit_ledger=ledger)
    # Deliberately not calling connect().
    report = manager.health_check()
    assert not report.checks["memory_manager"]
    assert not report.healthy


# -- Validate --------------------------------------------------------------------------


def test_validate_reports_per_store_status(memory_manager):
    result = memory_manager.validate()
    assert result == {
        "session_memory": True,
        "conversation_memory": True,
        "preference_memory": True,
        "knowledge_memory": True,
    }


# -- Knowledge Memory placeholder --------------------------------------------------------


def test_knowledge_memory_is_placeholder_only(memory_manager):
    structure = memory_manager.knowledge.structure()
    assert structure["implemented"] is False

    memory_manager.knowledge.store("k1", {"anything": True})
    assert memory_manager.knowledge.retrieve("k1") == {"anything": True}
    assert memory_manager.knowledge.retrieve("missing") is None


# -- Audit integration --------------------------------------------------------------------


def test_every_mutation_is_audited(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    manager = MemoryManager(storage_dir=tmp_path / "memory", audit_ledger=ledger)
    manager.connect()

    manager.save_session(
        SessionMemoryRecord(
            session_id="s-1",
            session_status="open",
            current_task=None,
            pending_approval=None,
            workflow_position=None,
            last_activity="2026-01-01T00:00:00+00:00",
            console_state={},
        )
    )
    manager.append_conversation(ConversationRecord.new(session_id="s-1", user_input="hi"))
    manager.set_preference("k", "v")
    manager.recover()

    event_types = [e.event_type for e in ledger.read_all()]
    assert "memory.saved" in event_types
    assert "memory.created" in event_types
    assert "memory.recovery_started" in event_types
    assert "memory.recovery_completed" in event_types
