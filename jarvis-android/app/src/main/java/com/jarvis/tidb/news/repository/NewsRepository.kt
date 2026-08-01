package com.jarvis.tidb.news.repository

import com.jarvis.tidb.news.entity.NewsArticleEntity
import com.jarvis.tidb.news.entity.NewsArticleStatus
import com.jarvis.tidb.news.entity.NewsCategoryEntity
import com.jarvis.tidb.news.entity.NewsCategoryLinkEntity
import com.jarvis.tidb.news.entity.NewsDuplicateEntity
import com.jarvis.tidb.news.entity.NewsInstrumentLinkEntity
import com.jarvis.tidb.news.entity.NewsSourceEntity
import com.jarvis.tidb.news.entity.SentimentScoreEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single facade over the News & Sentiment Intelligence Platform's 7 tables. No decisioning,
 * no scoring logic, no evidence/confidence/outcome concepts implemented here — pure storage and
 * retrieval, matching every other module's convention. Promoting an article to evidence is
 * explicitly NOT a method on this repository; that's a cross-module operation the caller
 * performs against `historical.evidence.repository.EvidenceRepository` and
 * `intelligence.evidence.repository.IntelligenceEvidenceRepository` directly, using this
 * repository's [getArticle] / [getInstrumentLinks] to read what it needs first. Keeping that
 * orchestration out of this repository avoids a circular module dependency (news depending on
 * evidence depending on... news) and matches how `analytics.repository.LearningRepository`
 * resolves its own polymorphic evidence links at the repository-call-site, not inside a
 * cross-module repository method.
 */
interface NewsRepository {

    // ---- sources ----
    suspend fun registerSource(source: NewsSourceEntity): Long
    suspend fun updateSource(source: NewsSourceEntity)
    suspend fun getSource(sourceId: Long): NewsSourceEntity?
    suspend fun getSourceByKey(sourceKey: String): NewsSourceEntity?
    fun observeActiveSources(): Flow<List<NewsSourceEntity>>

    // ---- articles ----
    /**
     * Inserts [article] along with its category links and instrument links as one unit, wiring
     * the generated articleId into each — mirrors
     * `EvidenceRepository.recordEvidence`'s "record the parent, then wire children" shape.
     * Returns null instead of inserting if [article]'s `(sourceId, externalArticleId)` pair
     * already exists (exact-dedup, per the approved blueprint §6.2) — callers should check
     * [findByExternalId] first if they need to distinguish "already existed" from "failed".
     */
    suspend fun ingestArticle(
        article: NewsArticleEntity,
        categoryLinks: List<NewsCategoryLinkEntity> = emptyList(),
        instrumentLinks: List<NewsInstrumentLinkEntity> = emptyList()
    ): Long

    suspend fun updateArticleStatus(articleId: Long, status: NewsArticleStatus)
    suspend fun getArticle(articleId: Long): NewsArticleEntity?
    suspend fun findByExternalId(sourceId: Long, externalArticleId: String): NewsArticleEntity?
    suspend fun findByContentHash(contentHash: String): List<NewsArticleEntity>
    fun observeRecentArticles(limit: Int = 100): Flow<List<NewsArticleEntity>>
    fun observeArticlesByStatus(status: NewsArticleStatus, limit: Int = 200): Flow<List<NewsArticleEntity>>

    /** Records [correction] with `correctsArticleId` set, and marks the original article SUPERSEDED — the "supersede, don't mutate" pattern per the approved blueprint §4.9. */
    suspend fun recordCorrection(originalArticleId: Long, correction: NewsArticleEntity): Long
    fun observeCorrections(originalArticleId: Long): Flow<List<NewsArticleEntity>>

    // ---- categories ----
    suspend fun registerCategory(category: NewsCategoryEntity): Long
    suspend fun getCategoryByCode(code: String): NewsCategoryEntity?
    fun observeActiveCategories(): Flow<List<NewsCategoryEntity>>
    fun observeCategoryLinksForArticle(articleId: Long): Flow<List<NewsCategoryLinkEntity>>

    // ---- instrument links ----
    fun observeInstrumentLinksForArticle(articleId: Long): Flow<List<NewsInstrumentLinkEntity>>
    /** Primary read pattern per the approved blueprint §9.3 — recent news for one instrument, most-recent first. */
    fun observeRecentNewsForInstrument(instrumentId: Long, limit: Int = 100): Flow<List<NewsInstrumentLinkEntity>>

    // ---- sentiment ----
    suspend fun recordSentimentScore(score: SentimentScoreEntity): Long
    fun observeSentimentForArticle(articleId: Long): Flow<List<SentimentScoreEntity>>
    suspend fun getLatestSentiment(articleId: Long, modelKey: String): SentimentScoreEntity?
    fun observeSentimentForInstrumentLink(instrumentLinkId: Long): Flow<List<SentimentScoreEntity>>

    // ---- duplicates ----
    suspend fun recordDuplicate(duplicate: NewsDuplicateEntity): Long
    fun observeDuplicatesOf(primaryArticleId: Long): Flow<List<NewsDuplicateEntity>>
    suspend fun findPrimaryFor(duplicateArticleId: Long): NewsDuplicateEntity?
}
