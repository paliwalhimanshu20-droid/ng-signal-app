"""
Tests for jarvis.interface.command_parser.CommandParser.
"""

from __future__ import annotations

from jarvis.interface.command_parser import CommandParser, CommandType


def test_exit_commands():
    parser = CommandParser()
    for word in ("exit", "quit", "bye", "EXIT"):
        assert parser.parse(word).command_type is CommandType.EXIT


def test_known_commands():
    parser = CommandParser()
    assert parser.parse("help").command_type is CommandType.HELP
    assert parser.parse("status").command_type is CommandType.STATUS
    assert parser.parse("history").command_type is CommandType.HISTORY
    assert parser.parse("clear").command_type is CommandType.CLEAR


def test_normal_request_when_not_awaiting_anything():
    parser = CommandParser()
    result = parser.parse("Analyze GitHub repository")
    assert result.command_type is CommandType.NORMAL_REQUEST


def test_empty_input_is_unknown():
    parser = CommandParser()
    assert parser.parse("").command_type is CommandType.UNKNOWN
    assert parser.parse("   ").command_type is CommandType.UNKNOWN


def test_unknown_input_when_not_awaiting_approval_is_normal_request():
    # Free text with no special meaning, outside an approval context, is
    # always a normal request — the parser never guesses it's "wrong,"
    # it hands it to the pipeline honestly.
    parser = CommandParser()
    result = parser.parse("gibberish nonsense text")
    assert result.command_type is CommandType.NORMAL_REQUEST


def test_approval_response_yes_variants():
    parser = CommandParser()
    for word in ("yes", "approve", "confirm", "YES"):
        result = parser.parse(word, awaiting_approval=True)
        assert result.command_type is CommandType.APPROVAL_RESPONSE
        assert result.approved is True


def test_approval_response_no_variants():
    parser = CommandParser()
    for word in ("no", "reject", "cancel"):
        result = parser.parse(word, awaiting_approval=True)
        assert result.command_type is CommandType.APPROVAL_RESPONSE
        assert result.approved is False


def test_unrecognized_input_while_awaiting_approval_is_unknown():
    parser = CommandParser()
    result = parser.parse("maybe later", awaiting_approval=True)
    assert result.command_type is CommandType.UNKNOWN
    assert result.approved is None


def test_confirmation_exact_phrase_required():
    parser = CommandParser()
    result = parser.parse(
        "DELETE PRODUCTION DATABASE",
        awaiting_confirmation=True,
        confirmation_phrase="DELETE PRODUCTION DATABASE",
    )
    assert result.command_type is CommandType.CONFIRMATION_RESPONSE
    assert result.approved is True


def test_confirmation_near_miss_is_unknown_not_accepted():
    parser = CommandParser()
    result = parser.parse(
        "delete production database",  # wrong case — no shortcuts
        awaiting_confirmation=True,
        confirmation_phrase="DELETE PRODUCTION DATABASE",
    )
    assert result.command_type is CommandType.UNKNOWN


def test_confirmation_cancel_is_accepted_as_rejection():
    parser = CommandParser()
    result = parser.parse(
        "cancel",
        awaiting_confirmation=True,
        confirmation_phrase="DELETE PRODUCTION DATABASE",
    )
    assert result.command_type is CommandType.CONFIRMATION_RESPONSE
    assert result.approved is False


def test_exit_still_recognized_while_awaiting_approval():
    parser = CommandParser()
    result = parser.parse("exit", awaiting_approval=True)
    assert result.command_type is CommandType.EXIT
