"""
Tests for jarvis.agents.models.ExecutionResult: serialization and
structural validation.
"""

from __future__ import annotations

import pytest

from jarvis.agents.models import ExecutionResult, ExecutionStatus


def test_new_success_result_is_valid():
    result = ExecutionResult.new(
        status=ExecutionStatus.SUCCESS,
        message="ok",
        executed_by="agent-1",
        execution_time=0.01,
        evidence=("evidence 1",),
    )
    assert result.result_id.startswith("result-")
    assert result.status is ExecutionStatus.SUCCESS


def test_serialization_round_trip_shape():
    result = ExecutionResult.new(
        status=ExecutionStatus.SUCCESS,
        message="ok",
        executed_by="agent-1",
        execution_time=0.02,
        evidence=("a", "b"),
        warnings=("w1",),
        metadata={"task_id": "task-1"},
    )
    data = result.to_dict()

    assert data["status"] == "success"
    assert data["evidence"] == ["a", "b"]
    assert data["warnings"] == ["w1"]
    assert data["errors"] == []
    assert data["metadata"] == {"task_id": "task-1"}
    assert isinstance(data["execution_time"], float)


def test_empty_result_id_rejected():
    with pytest.raises(ValueError):
        ExecutionResult(
            result_id="",
            status=ExecutionStatus.SUCCESS,
            message="ok",
            executed_by="agent-1",
            execution_time=0.0,
            evidence=(),
            warnings=(),
            errors=(),
        )


def test_empty_message_rejected():
    with pytest.raises(ValueError):
        ExecutionResult(
            result_id="result-1",
            status=ExecutionStatus.SUCCESS,
            message="   ",
            executed_by="agent-1",
            execution_time=0.0,
            evidence=(),
            warnings=(),
            errors=(),
        )


def test_negative_execution_time_rejected():
    with pytest.raises(ValueError):
        ExecutionResult(
            result_id="result-1",
            status=ExecutionStatus.SUCCESS,
            message="ok",
            executed_by="agent-1",
            execution_time=-0.5,
            evidence=(),
            warnings=(),
            errors=(),
        )


def test_failed_status_without_errors_rejected():
    with pytest.raises(ValueError):
        ExecutionResult(
            result_id="result-1",
            status=ExecutionStatus.FAILED,
            message="something went wrong",
            executed_by="agent-1",
            execution_time=0.0,
            evidence=(),
            warnings=(),
            errors=(),  # empty — must be rejected per Article III
        )


def test_failed_status_with_errors_is_valid():
    result = ExecutionResult.new(
        status=ExecutionStatus.FAILED,
        message="failed",
        executed_by="agent-1",
        execution_time=0.0,
        errors=("capability_mismatch",),
    )
    assert result.status is ExecutionStatus.FAILED
    assert "capability_mismatch" in result.errors
