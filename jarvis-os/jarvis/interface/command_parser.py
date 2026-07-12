"""
jarvis.interface.command_parser

CommandParser: classifies raw owner input into one of this sprint's
named command types. Context-aware — the SAME word ("yes", "no") means
something entirely different depending on whether the session is
currently awaiting an approval or confirmation response, so `parse()`
takes that context explicitly rather than guessing from the text alone.

Design reference: this sprint's explicit requirement — "Never guess.
Unknown input must request clarification." Every branch below either
confidently classifies input against an explicit, named rule, or falls
through to UNKNOWN — there is no fuzzy/best-effort matching anywhere in
this file.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Optional

_EXIT_WORDS = frozenset({"exit", "quit", "bye"})
_APPROVAL_YES_WORDS = frozenset({"yes", "approve", "confirm"})
_APPROVAL_NO_WORDS = frozenset({"no", "reject", "cancel"})


class CommandType(str, Enum):
    NORMAL_REQUEST = "normal_request"
    APPROVAL_RESPONSE = "approval_response"
    CONFIRMATION_RESPONSE = "confirmation_response"
    EXIT = "exit"
    HELP = "help"
    STATUS = "status"
    HISTORY = "history"
    CONVERSATION = "conversation"
    CLEAR = "clear"
    UNKNOWN = "unknown"


@dataclass(frozen=True)
class ParsedCommand:
    """
    The Command Parser's structured output.

    `approved` is only meaningful for APPROVAL_RESPONSE and
    CONFIRMATION_RESPONSE — None otherwise. Kept as an explicit,
    always-present field (rather than encoding yes/no into the raw text
    the caller has to re-parse) so nothing downstream ever re-implements
    this classification logic — the "no duplicated logic" requirement,
    applied to the parser's own output shape.
    """

    command_type: CommandType
    raw_input: str
    normalized_input: str
    approved: Optional[bool] = None


class CommandParser:
    """
    Stateless — every call to `parse()` is independent, taking whatever
    context (awaiting approval, awaiting confirmation, the exact
    confirmation phrase required) the caller currently has. This mirrors
    jarvis.intake.intent_processor.IntentProcessor's own stateless,
    deterministic design.
    """

    def parse(
        self,
        raw_input: str,
        awaiting_approval: bool = False,
        awaiting_confirmation: bool = False,
        confirmation_phrase: Optional[str] = None,
    ) -> ParsedCommand:
        normalized = " ".join(raw_input.split())
        lowered = normalized.lower()

        if not normalized:
            return ParsedCommand(CommandType.UNKNOWN, raw_input, normalized)

        if lowered in _EXIT_WORDS:
            return ParsedCommand(CommandType.EXIT, raw_input, normalized)
        if lowered == "help":
            return ParsedCommand(CommandType.HELP, raw_input, normalized)
        if lowered == "status":
            return ParsedCommand(CommandType.STATUS, raw_input, normalized)
        if lowered == "history":
            return ParsedCommand(CommandType.HISTORY, raw_input, normalized)
        if lowered == "conversation":
            return ParsedCommand(CommandType.CONVERSATION, raw_input, normalized)
        if lowered == "clear":
            return ParsedCommand(CommandType.CLEAR, raw_input, normalized)

        if awaiting_confirmation:
            return self._parse_confirmation(raw_input, normalized, confirmation_phrase)

        if awaiting_approval:
            return self._parse_approval(raw_input, normalized, lowered)

        return ParsedCommand(CommandType.NORMAL_REQUEST, raw_input, normalized)

    @staticmethod
    def _parse_confirmation(raw_input: str, normalized: str, confirmation_phrase: Optional[str]) -> ParsedCommand:
        """
        Tier 3 confirmation requires the EXACT phrase, case-sensitive —
        per this sprint's explicit "No shortcuts" rule and JARVIS-001
        §15's requirement that a Tier 3 confirmation be specific, not a
        generic "yes". A near-miss is not accepted as a shortcut; it
        falls through to UNKNOWN, requiring the owner to retype it exactly.
        """
        if confirmation_phrase is not None and normalized == confirmation_phrase:
            return ParsedCommand(CommandType.CONFIRMATION_RESPONSE, raw_input, normalized, approved=True)
        if normalized.lower() in _APPROVAL_NO_WORDS:
            return ParsedCommand(CommandType.CONFIRMATION_RESPONSE, raw_input, normalized, approved=False)
        return ParsedCommand(CommandType.UNKNOWN, raw_input, normalized)

    @staticmethod
    def _parse_approval(raw_input: str, normalized: str, lowered: str) -> ParsedCommand:
        if lowered in _APPROVAL_YES_WORDS:
            return ParsedCommand(CommandType.APPROVAL_RESPONSE, raw_input, normalized, approved=True)
        if lowered in _APPROVAL_NO_WORDS:
            return ParsedCommand(CommandType.APPROVAL_RESPONSE, raw_input, normalized, approved=False)
        return ParsedCommand(CommandType.UNKNOWN, raw_input, normalized)
