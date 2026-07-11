"""
jarvis.intake.intent_processor

The Sprint-1A Intent Processor: converts raw owner text into a fully
populated Intent object.

Design reference: JARVIS-001 §10. Per that section, a real Intent
Processor must produce three things for every input: a structured
interpretation, a confidence score, and an explicit ambiguity flag when
confidence is insufficient.

HONESTY NOTE, stated as prominently here as in the module itself: this is
NOT a language model and does not attempt to be one. Classification is
deterministic keyword matching against small, fixed vocabularies defined
below. This is a deliberate Sprint-1A scope boundary (no LLM integration),
not a placeholder pretending to be smarter than it is — every confidence
score this module produces is directly traceable to which keywords
matched, and `confidence_reason` always says so explicitly, per Article
III.
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.intake.models import Intent, IntentType
from jarvis.logging_ import get_logger

logger = get_logger(__name__)

# Confidence below this value is treated as ambiguous, requiring
# clarification before Task Planning can proceed. Deliberately a module-
# level constant, not a magic number buried in logic, so it's the one
# place a future sprint needs to look to retune it.
CONFIDENCE_THRESHOLD = 0.5

# Recognized-but-explicitly-out-of-scope requests. Matching one of these
# does NOT mean the system is confused about what's being asked — it
# means the request is understood clearly and is honestly reported as
# unsupported, per this sprint's explicit exclusions (routing, execution,
# trading, GitHub actions, permissions, approvals).
UNSUPPORTED_KEYWORDS: tuple[str, ...] = (
    "deploy",
    "trade",
    "buy",
    "sell",
    "transfer money",
    "delete production",
    "send email",
    "merge to main",
    "push to production",
)

# Small, fixed keyword vocabulary per IntentType. Order matters only in
# that INTENT_KEYWORDS is scanned in full and every category's match
# count is computed — see _classify() for the tie-breaking rule when more
# than one category matches.
INTENT_KEYWORDS: dict[IntentType, tuple[str, ...]] = {
    IntentType.ANALYZE: ("analyze", "analyse", "review", "examine"),
    IntentType.INVESTIGATE: ("investigate", "debug", "diagnose", "troubleshoot", "why is"),
    IntentType.STATUS_CHECK: ("status", "health", "is it working", "how is"),
    IntentType.RESEARCH: ("research", "study", "look into"),
}

# Minimal, controlled-vocabulary entity spotting. Explicitly NOT named-
# entity recognition — a fixed substring-match table only, documented as
# such so nobody mistakes this for more than it is.
ENTITY_VOCABULARY: dict[str, str] = {
    "github": "github_repository",
    "repository": "github_repository",
    "repo": "github_repository",
    "pull request": "github_pull_request",
    "database": "database",
    "schema": "database_schema",
    "natural gas": "trading_instrument",
    "trading": "domain_trading",
    "deployment": "deployment",
}


class IntentProcessor:
    """
    Processes raw owner input into an Intent, deterministically.

    Every call to `process()` records two Audit Ledger entries — "intent
    received" and "intent parsed" — per this sprint's explicit requirement
    that every decision generate an Audit event, and emits the structured
    log lines the acceptance scenario names ("Intent Received", "Intent
    Parsed", "Confidence Calculated").
    """

    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit_ledger = audit_ledger

    def process(self, raw_input: str) -> Intent:
        logger.info("User Input Received: raw_input=%r", raw_input)
        self._audit_ledger.record(
            event_type="intent.received",
            message="Raw owner input received for intent processing.",
            details={"raw_input": raw_input},
        )

        normalized = self._normalize(raw_input)

        if not normalized:
            intent = Intent.new(
                raw_input=raw_input,
                normalized_input=normalized,
                intent_type=IntentType.UNKNOWN,
                confidence=0.0,
                confidence_reason=(
                    "Input was empty (or whitespace-only) after normalization; "
                    "there is no content to interpret."
                ),
                detected_entities={},
                is_ambiguous=True,
                requires_clarification=True,
            )
            return self._finalize(intent)

        unsupported_match = self._match_unsupported(normalized)
        if unsupported_match is not None:
            intent = Intent.new(
                raw_input=raw_input,
                normalized_input=normalized,
                intent_type=IntentType.UNSUPPORTED,
                confidence=0.75,
                confidence_reason=(
                    f"Input clearly matches the recognized-but-unsupported keyword "
                    f"'{unsupported_match}'. The request is understood; this system's "
                    f"current scope does not support acting on it."
                ),
                detected_entities=self._detect_entities(normalized),
                is_ambiguous=False,
                requires_clarification=False,
            )
            return self._finalize(intent)

        intent_type, confidence, reason, ambiguous = self._classify(normalized)
        intent = Intent.new(
            raw_input=raw_input,
            normalized_input=normalized,
            intent_type=intent_type,
            confidence=confidence,
            confidence_reason=reason,
            detected_entities=self._detect_entities(normalized),
            is_ambiguous=ambiguous,
            requires_clarification=ambiguous,
        )
        return self._finalize(intent)

    def _finalize(self, intent: Intent) -> Intent:
        # Log order deliberately matches the Sprint-1A acceptance scenario's
        # exact sequence (Intent Parsed, then Confidence Calculated), even
        # though both values are produced together by _classify() — this is
        # a presentation-order choice only, not a computation-order one.
        logger.info(
            "Intent Parsed: intent_id=%s intent_type=%s is_ambiguous=%s requires_clarification=%s",
            intent.intent_id,
            intent.intent_type.value,
            intent.is_ambiguous,
            intent.requires_clarification,
        )
        logger.info(
            "Confidence Calculated: intent_type=%s confidence=%.2f reason=%r",
            intent.intent_type.value,
            intent.confidence,
            intent.confidence_reason,
        )
        self._audit_ledger.record(
            event_type="intent.parsed",
            message="Intent processing complete.",
            details={
                "intent_id": intent.intent_id,
                "intent_type": intent.intent_type.value,
                "confidence": intent.confidence,
                "confidence_reason": intent.confidence_reason,
                "is_ambiguous": intent.is_ambiguous,
                "requires_clarification": intent.requires_clarification,
                "detected_entities": intent.detected_entities,
            },
        )
        return intent

    @staticmethod
    def _normalize(raw_input: str) -> str:
        """Trim and collapse whitespace. Case is preserved; matching below is case-insensitive separately."""
        return " ".join(raw_input.split())

    @staticmethod
    def _match_unsupported(normalized: str) -> str | None:
        lowered = normalized.lower()
        for keyword in UNSUPPORTED_KEYWORDS:
            if keyword in lowered:
                return keyword
        return None

    @staticmethod
    def _classify(normalized: str) -> tuple[IntentType, float, str, bool]:
        """
        Deterministic keyword classification.

        Returns (intent_type, confidence, confidence_reason, is_ambiguous).
        See module docstring: this is intentionally simple, deterministic,
        and fully explainable — never a black box, per Article III.
        """
        lowered = normalized.lower()
        matches: dict[IntentType, list[str]] = {}

        for intent_type, keywords in INTENT_KEYWORDS.items():
            hits = [kw for kw in keywords if kw in lowered]
            if hits:
                matches[intent_type] = hits

        if not matches:
            return (
                IntentType.UNKNOWN,
                0.15,
                "No recognized intent keywords were found in the input; "
                "the system cannot determine what action is being requested.",
                True,
            )

        if len(matches) > 1:
            best_count = max(len(hits) for hits in matches.values())
            tied = [it for it, hits in matches.items() if len(hits) == best_count]
            if len(tied) > 1:
                names = ", ".join(sorted(t.value for t in tied))
                return (
                    IntentType.UNKNOWN,
                    0.4,
                    f"Input matches keywords for multiple, equally plausible intent "
                    f"types ({names}); cannot confidently pick one without clarification.",
                    True,
                )
            intent_type = tied[0]
            hits = matches[intent_type]
        else:
            intent_type = next(iter(matches))
            hits = matches[intent_type]

        confidence = min(0.6 + 0.1 * min(len(hits), 3), 0.9)
        is_ambiguous = confidence < CONFIDENCE_THRESHOLD
        reason = (
            f"Matched keyword(s) {hits} for intent type '{intent_type.value}'. "
            f"Confidence scales with the number of matching keywords, capped at 0.9 "
            f"since this is deterministic keyword matching, not semantic understanding."
        )
        return intent_type, confidence, reason, is_ambiguous

    @staticmethod
    def _detect_entities(normalized: str) -> dict[str, str]:
        lowered = normalized.lower()
        found: dict[str, str] = {}
        for phrase, entity_type in ENTITY_VOCABULARY.items():
            if phrase in lowered:
                found[entity_type] = phrase
        return found
