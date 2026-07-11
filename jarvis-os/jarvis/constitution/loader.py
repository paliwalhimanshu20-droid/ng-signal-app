"""
jarvis.constitution.loader

Structural loader and validator for the JARVIS Constitution reference.

Design reference: JARVIS-001 §2 (Relationship with the Constitution),
JARVIS-001 §7 (Bootstrap Process, Step 1).

This module deliberately validates STRUCTURE, not CONTENT — it confirms
all seven required Articles are present with the expected fields, not
that their summaries are "correct" in some semantic sense. Content
correctness is an owner/governance concern (Article VII), not something
software can or should adjudicate.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Final

# The seven Articles every valid Constitution reference must declare.
# Order matches JARVIS OS Constitution & Master Architecture Blueprint v1.0.
REQUIRED_ARTICLE_IDS: Final[tuple[str, ...]] = ("I", "II", "III", "IV", "V", "VI", "VII")


class ConstitutionValidationError(Exception):
    """
    Raised when a Constitution reference fails structural validation.

    Per JARVIS-001 §7 Step 1, this must be treated as fatal by the
    Bootstrap sequence — JARVIS Core must not reach a ready state while
    this exception is unresolved, under any circumstance.
    """


@dataclass(frozen=True)
class Article:
    """A single constitutional Article, as structurally declared."""

    id: str
    name: str
    summary: str


@dataclass(frozen=True)
class Constitution:
    """
    A validated, in-memory representation of the Constitution reference
    this JARVIS Core instance is running against.

    Frozen (immutable) deliberately: nothing downstream of the Bootstrap
    sequence should ever be able to mutate the Constitution reference at
    runtime. A change to the Constitution is always a new load, following
    Article VII's amendment process — never an in-place edit.
    """

    version: str
    articles: tuple[Article, ...]

    def has_article(self, article_id: str) -> bool:
        return any(article.id == article_id for article in self.articles)

    def article(self, article_id: str) -> Article:
        for article in self.articles:
            if article.id == article_id:
                return article
        raise KeyError(f"Article {article_id!r} not found in loaded Constitution.")


def load_constitution(path: str | Path) -> Constitution:
    """
    Load and structurally validate a Constitution reference from a JSON file.

    Raises ConstitutionValidationError if the file is missing, malformed,
    or missing any of the seven required Articles. Per JARVIS-001 §7, the
    caller (jarvis.core.bootstrap) MUST treat this as a fatal, unrecoverable
    startup condition — there is no partial or degraded mode of operation
    without a validated Constitution.
    """
    file_path = Path(path)

    if not file_path.exists():
        raise ConstitutionValidationError(
            f"Constitution reference not found at {file_path}. "
            "JARVIS Core cannot boot without a valid Constitution reference."
        )

    try:
        raw = json.loads(file_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ConstitutionValidationError(
            f"Constitution reference at {file_path} is not valid JSON: {exc}"
        ) from exc

    version = raw.get("constitution_version")
    if not version or not isinstance(version, str):
        raise ConstitutionValidationError(
            "Constitution reference is missing a valid 'constitution_version' string."
        )

    raw_articles = raw.get("articles")
    if not isinstance(raw_articles, list):
        raise ConstitutionValidationError(
            "Constitution reference is missing an 'articles' list."
        )

    articles: list[Article] = []
    seen_ids: set[str] = set()
    for entry in raw_articles:
        try:
            article = Article(id=entry["id"], name=entry["name"], summary=entry["summary"])
        except (KeyError, TypeError) as exc:
            raise ConstitutionValidationError(
                f"Malformed Article entry in Constitution reference: {entry!r}"
            ) from exc
        articles.append(article)
        seen_ids.add(article.id)

    missing = [aid for aid in REQUIRED_ARTICLE_IDS if aid not in seen_ids]
    if missing:
        raise ConstitutionValidationError(
            "Constitution reference is structurally incomplete. "
            f"Missing required Article(s): {', '.join(missing)}. "
            "Per JARVIS-001 §2, a Constitution missing any Article must fail "
            "the compatibility check even if its version string looks correct."
        )

    return Constitution(version=version, articles=tuple(articles))
