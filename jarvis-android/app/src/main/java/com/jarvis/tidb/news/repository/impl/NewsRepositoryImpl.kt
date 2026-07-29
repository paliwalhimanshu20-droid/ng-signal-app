package com.jarvis.tidb.news.repository.impl

import com.jarvis.tidb.news.dao.NewsArticleDao
import com.jarvis.tidb.news.dao.NewsCategoryDao
import com.jarvis.tidb.news.dao.NewsCategoryLinkDao
import com.jarvis.tidb.news.dao.NewsDuplicateDao
import com.jarvis.tidb.news.dao.NewsInstrumentLinkDao
import com.jarvis.tidb.news.dao.NewsSourceDao
import com.jarvis.tidb.news.dao.SentimentScoreDao
import com.jarvis.tidb.news.entity.NewsArticleEntity
import com.jarvis.tidb.news.entity.NewsArticleStatus
import com.jarvis.tidb.news.entity.NewsCategoryEntity
import com.jarvis.tidb.news.entity.NewsCategoryLinkEntity
import com.jarvis.tidb.news.entity.NewsDuplicateEntity
import com.jarvis.tidb.news.entity.NewsInstrumentLinkEntity
import com.jarvis.tidb.news.entity.NewsSourceEntity
import com.jarvis.tidb.news.entity.SentimentScoreEntity
import com.jarvis.tidb.news.repository.NewsRepository
import kotlinx.coroutines.flow.Flow

class NewsRepositoryImpl(
    private val sourceDao: NewsSourceDao,
    private val articleDao: NewsArticleDao,
    private val categoryDao: NewsCategoryDao,
    private val categoryLinkDao: NewsCategoryLinkDao,
    private val instrumentLinkDao: NewsInstrumentLinkDao,
    private val sentimentScoreDao: SentimentScoreDao,
    private val duplicateDao: NewsDuplicateDao
) : NewsRepository {

    // ---- sources ----

    override suspend fun registerSource(source: NewsSourceEntity): Long = sourceDao.insert(source)

    override suspend fun updateSource(source: NewsSourceEntity) = sourceDao.update(source)

    override suspend fun getSource(sourceId: Long): NewsSourceEntity? = sourceDao.findById(sourceId)

    override suspend fun getSourceByKey(sourceKey: String): NewsSourceEntity? = sourceDao.findByKey(sourceKey)

    override fun observeActiveSources(): Flow<List<NewsSourceEntity>> = sourceDao.observeActive()

    // ---- articles ----

    override suspend fun ingestArticle(
        article: NewsArticleEntity,
        categoryLinks: List<NewsCategoryLinkEntity>,
        instrumentLinks: List<NewsInstrumentLinkEntity>
    ): Long {
        val existing = articleDao.findByExternalId(article.sourceId, article.externalArticleId)
        if (existing != null) return existing.articleId

        val articleId = articleDao.insert(article)
        if (categoryLinks.isNotEmpty()) {
            categoryLinkDao.insertAll(categoryLinks.map { it.copy(articleId = articleId) })
        }
        if (instrumentLinks.isNotEmpty()) {
            instrumentLinkDao.insertAll(instrumentLinks.map { it.copy(articleId = articleId) })
        }
        return articleId
    }

    override suspend fun updateArticleStatus(articleId: Long, status: NewsArticleStatus) {
        val article = articleDao.findById(articleId) ?: return
        articleDao.update(article.copy(status = status, audit = article.audit.touched()))
    }

    override suspend fun getArticle(articleId: Long): NewsArticleEntity? = articleDao.findById(articleId)

    override suspend fun findByExternalId(sourceId: Long, externalArticleId: String): NewsArticleEntity? =
        articleDao.findByExternalId(sourceId, externalArticleId)

    override suspend fun findByContentHash(contentHash: String): List<NewsArticleEntity> =
        articleDao.findByContentHash(contentHash)

    override fun observeRecentArticles(limit: Int): Flow<List<NewsArticleEntity>> = articleDao.observeRecent(limit)

    override fun observeArticlesByStatus(status: NewsArticleStatus, limit: Int): Flow<List<NewsArticleEntity>> =
        articleDao.observeByStatus(status, limit)

    override suspend fun recordCorrection(originalArticleId: Long, correction: NewsArticleEntity): Long {
        val original = articleDao.findById(originalArticleId)
        val correctionId = articleDao.insert(correction.copy(correctsArticleId = originalArticleId))
        if (original != null) {
            articleDao.update(
                original.copy(status = NewsArticleStatus.SUPERSEDED, audit = original.audit.touched())
            )
        }
        return correctionId
    }

    override fun observeCorrections(originalArticleId: Long): Flow<List<NewsArticleEntity>> =
        articleDao.observeCorrections(originalArticleId)

    // ---- categories ----

    override suspend fun registerCategory(category: NewsCategoryEntity): Long = categoryDao.insert(category)

    override suspend fun getCategoryByCode(code: String): NewsCategoryEntity? = categoryDao.findByCode(code)

    override fun observeActiveCategories(): Flow<List<NewsCategoryEntity>> = categoryDao.observeActive()

    override fun observeCategoryLinksForArticle(articleId: Long): Flow<List<NewsCategoryLinkEntity>> =
        categoryLinkDao.observeForArticle(articleId)

    // ---- instrument links ----

    override fun observeInstrumentLinksForArticle(articleId: Long): Flow<List<NewsInstrumentLinkEntity>> =
        instrumentLinkDao.observeForArticle(articleId)

    override fun observeRecentNewsForInstrument(instrumentId: Long, limit: Int): Flow<List<NewsInstrumentLinkEntity>> =
        instrumentLinkDao.observeRecentForInstrument(instrumentId, limit)

    // ---- sentiment ----

    override suspend fun recordSentimentScore(score: SentimentScoreEntity): Long = sentimentScoreDao.insert(score)

    override fun observeSentimentForArticle(articleId: Long): Flow<List<SentimentScoreEntity>> =
        sentimentScoreDao.observeForArticle(articleId)

    override suspend fun getLatestSentiment(articleId: Long, modelKey: String): SentimentScoreEntity? =
        sentimentScoreDao.findLatestForModel(articleId, modelKey)

    override fun observeSentimentForInstrumentLink(instrumentLinkId: Long): Flow<List<SentimentScoreEntity>> =
        sentimentScoreDao.observeForInstrumentLink(instrumentLinkId)

    // ---- duplicates ----

    override suspend fun recordDuplicate(duplicate: NewsDuplicateEntity): Long = duplicateDao.insert(duplicate)

    override fun observeDuplicatesOf(primaryArticleId: Long): Flow<List<NewsDuplicateEntity>> =
        duplicateDao.observeDuplicatesOf(primaryArticleId)

    override suspend fun findPrimaryFor(duplicateArticleId: Long): NewsDuplicateEntity? =
        duplicateDao.findPrimaryFor(duplicateArticleId)
}
