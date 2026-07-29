# TRADING-007A.1 — News & Sentiment Intelligence Platform

Schema version: **v7** (migrated from v6 via `MIGRATION_6_7`, purely additive).

## 1. Reconciliation against Modules 4 & 5 — read this first

The approved blueprint was explicit: this is an **extension of the Evidence Engine, not a
parallel subsystem.** Before writing any entity, the existing schema was statically audited
(same discipline as TRADING-005 §0 and TRADING-006 §1 — parsed entities, cross-referenced DAO
SQL) to confirm what already covers "news as evidence" and what genuinely doesn't exist yet.
The outcome:

| Blueprint asked for | Resolution |
|---|---|
| A way to cite a news article as evidence | **Reused.** `historical.evidence.entity.EvidenceRecordEntity` + `SourceReferenceEntity` (Module 4) already do this — a news item becomes one `EvidenceRecordEntity` row per relevant instrument (evidence is single-instrument-scoped in this schema), with `SourceReferenceEntity(sourceType = "NEWS_ARTICLE", sourceRowId = articleId, url = article.url)` pointing back at it. **Not redefined.** |
| A way to link that evidence to a trade/signal/hypothesis/etc. | **Reused.** `intelligence.evidence.entity.EvidenceLinkEntity` (Module 5) already generalizes "link evidence to anything." **Not redefined.** |
| A way to track whether the news's implied move happened | **Reused.** `intelligence.evidence.entity.EvidenceOutcomeEntity` (Module 5), including its `actualMovePercent` field — exactly the shape "impact measurement" needs. **Not redefined.** |
| A confidence score for news-derived evidence | **Reused.** Once a news item is an `EvidenceRecordEntity`, it can receive a `ConfidenceScoreEntity` (Module 5) via `scoredEntityType = EVIDENCE_RECORD` like any other evidence. **Not redefined.** |
| Source credibility / trust | **Two-tier, not reused directly.** `intelligence.evidence.entity.EvidenceSourceEntity.reliabilityWeight` (Module 5) is the trust multiplier actually used in confidence composition — that's reused. `NewsSourceEntity` (new, this module) sits above it at the specific-publication grain (Reuters vs. an unverified RSS feed) that `EvidenceSourceEntity`'s coarse `EvidenceSourceKind` enum doesn't capture. `NewsSourceEntity.evidenceSourceCode` references `evidence_sources.sourceCode` logically (not a Room FK — see entity doc). |
| `NewsCategoryEntity` (taxonomy) | **New table, reused shape.** A news-scoped instance of the exact ROOT/SUBCATEGORY structure `intelligence.evidence.entity.EvidenceCategoryEntity` already established. The *enum* (`EvidenceCategoryLevel`) is reused directly rather than duplicated; the *table* is new because news categories (SUPPLY_DISRUPTION, WEATHER_EVENT, ...) are a different vocabulary from evidence categories (PRICE_STRUCTURE, VOLUME_ANOMALY, ...). |
| Sentiment scoring | **New — `SentimentScoreEntity`.** Deliberately NOT built on `ConfidenceScoreEntity`: sentiment (direction/magnitude of an opinion) and confidence (trust in evidence) are different measurements on different objects, and `ConfidenceScoreEntity.scoredEntityType` is a closed enum that doesn't include "news article." The two coexist once an article is promoted to evidence — see §4.10 below. |
| Article storage, source registry, category/instrument linking, duplicate detection | **All new** — nothing in Modules 1–6 stored article content, so `NewsSourceEntity`, `NewsArticleEntity`, `NewsCategoryLinkEntity`, `NewsInstrumentLinkEntity`, and `NewsDuplicateEntity` are genuinely new. |

**Net result: 7 new tables**, none of them evidence, confidence, link, or outcome tables — those
four concepts are entirely reused from Modules 4/5, per the implementation brief's explicit
"do not create" list.

## 2. What this platform adds

**7 new tables**, one new top-level package `com.jarvis.tidb.news`, plus two additive enum
values on an existing Module 4 enum:

```
news/
  entity/       NewsSourceEntity, NewsArticleEntity, NewsCategoryEntity,
                NewsCategoryLinkEntity, NewsInstrumentLinkEntity, SentimentScoreEntity,
                NewsDuplicateEntity
  dao/          one @Dao interface per entity above
  repository/   NewsRepository
  repository/impl/  NewsRepositoryImpl
```

Plus, in `historical.ingestion.entity.IngestionEntities.kt` (Module 4, additive only):
- `ProviderType.NEWS_FEED` — a news/RSS feed provider, reusing the existing
  `DataProviderEntity → IngestionJobEntity → IngestionCheckpointEntity → IngestionJobLogEntity`
  pipeline rather than building a parallel connector abstraction.
- `IngestionJobType.NEWS_PULL` — the job type a news ingestion run uses.

Both are String-persisted enum values (per this schema's established convention), so neither
requires an `ALTER TABLE` — only the Kotlin-side addition, applied in this same migration cycle
for consistency.

## 3. Data flow

```
DataProviderEntity (ProviderType.NEWS_FEED)
        │
        ▼
IngestionJobEntity (jobType=NEWS_PULL) ──creates──▶ IngestionJobLogEntity (retry/failure trail)
        │
        ▼ (fetch, validate, exact-dedup on (sourceId, externalArticleId))
NewsArticleEntity (status=PENDING → VALIDATED or QUARANTINED)
        │
        ├──▶ NewsCategoryLinkEntity[] ──▶ NewsCategoryEntity
        ├──▶ NewsInstrumentLinkEntity[] ──▶ instruments (Module 1), optionally contracts
        ├──▶ SentimentScoreEntity[] (one or more, per model/version — insert-only, "store don't recompute")
        └──▶ (near-dup pass) NewsDuplicateEntity, when a syndicated match is found
        │
        ▼ (when judged citable — a repository-layer decision, not automatic)
EvidenceRecordEntity (Module 4, one per relevant instrument)
        │
        ├──▶ SourceReferenceEntity (sourceType="NEWS_ARTICLE", sourceRowId=articleId, url=article.url)
        ├──▶ EvidenceLinkEntity (Module 5) — linked to whatever it supports/contradicts
        ├──▶ ConfidenceScoreEntity (Module 5) — trust in this evidence, separate from its sentiment
        └──▶ EvidenceOutcomeEntity (Module 5) — did the implied move happen? (multi-checkpoint)
                     │
                     ▼
        Feedback: outcome accuracy per NewsSourceEntity, aggregated over time,
        adjusts the referenced EvidenceSourceEntity.reliabilityWeight
```

A correction is a new `NewsArticleEntity` row with `correctsArticleId` pointing at the original;
the original is marked `SUPERSEDED` — never edited in place, matching the "no destructive
migrations" principle extended to data, not just schema (same shape as
`historical.candle.entity.CandleVersionEntity`).

## 4. Entity relationships (ER summary)

```
news_sources ─┬─▶ news_articles ─┬─▶ news_category_links ◀── news_categories
              │  (RESTRICT del.)  ├─▶ news_instrument_links ──▶ instruments (Module 1)
              │                   │        └──────────────────▶ contracts (Module 1, nullable)
              │                   ├─▶ sentiment_scores ◀── news_instrument_links (nullable)
              │                   ├─▶ news_articles (self, correctsArticleId, SET NULL)
              │                   └─▶ news_duplicates (primaryArticleId / duplicateArticleId, both CASCADE)
              │
              └── (logical only) evidence_sources.sourceCode

news_articles ─ (logical, via SourceReferenceEntity.sourceRowId) ─▶ evidence_records (Module 4)
```

## 5. Migration notes

- `MIGRATION_6_7` (`database/migration/NewsSentimentPlatformMigration.kt`) is purely additive:
  7 `CREATE TABLE IF NOT EXISTS` blocks with their indices, no `ALTER TABLE` (the two
  `ProviderType`/`IngestionJobType` additions are enum-only, no column change). Registered in
  `TidbMigrations.ALL = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)`.
- Every column, index, and foreign key in the migration SQL was cross-checked programmatically
  against the corresponding Kotlin entity definitions before delivery (7/7 tables match exactly
  on column set and index count), following the same audit discipline
  TRADING-005 §5 established.
- No naming collisions were found during this cross-check (unlike TRADING-005's
  `IndicatorDefinitionEntity.version` collision) — none of the new entities' own fields collide
  with `AuditMetadata`'s embedded columns.

## 6. What's deliberately out of scope

- No sentiment-scoring model or NLP pipeline — `SentimentScoreEntity` is pure storage for a
  score computed elsewhere, matching the "store, don't recompute" principle already used by
  `historical.indicator.entity.IndicatorValueEntity`.
- No automatic promotion of articles to `EvidenceRecordEntity` — that decision (which articles
  are citable) is a repository-call-site / future Decision Engine concern, not something this
  module does automatically on ingest.
- No content-level fact-checking or "fake news" classification — mitigated upstream via
  registered-source-only ingestion and the reliability-weight feedback loop, not in-house
  detection (per the approved blueprint §10).
- No live connectors implemented — `ProviderType.NEWS_FEED` / `IngestionJobType.NEWS_PULL`
  prepare the taxonomy only; RSS/NewsAPI/Reuters/Bloomberg/Trading Economics integrations are
  future work per the blueprint's connector sequencing (§5 of the blueprint).
- No `Sector` taxonomy table — `NewsInstrumentLinkEntity.sector` is free text; this schema has
  no existing sector concept to extend and inventing one was judged out of scope for a platform
  whose job is storing news, not building a sector classification system.
- No separate "importance" scoring table — folded into `NewsArticleEntity.importanceScore` as a
  single nullable scalar per the blueprint's explicit deferral (§4.8), to be split out later
  only if it needs independent model-versioning the way sentiment does.
