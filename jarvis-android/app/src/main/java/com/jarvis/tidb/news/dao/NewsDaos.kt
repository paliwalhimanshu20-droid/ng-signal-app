package com.jarvis.tidb.news.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.news.entity.NewsArticleEntity
import com.jarvis.tidb.news.entity.NewsArticleStatus
import com.jarvis.tidb.news.entity.NewsCategoryEntity
import com.jarvis.tidb.news.entity.NewsCategoryLinkEntity
import com.jarvis.tidb.news.entity.NewsDuplicateEntity
import com.jarvis.tidb.news.entity.NewsInstrumentLinkEntity
import com.jarvis.tidb.news.entity.NewsSourceEntity
import com.jarvis.tidb.news.entity.SentimentScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsSourceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(source: NewsSourceEntity): Long

    @Update
    suspend fun update(source: NewsSourceEntity)

    @Query("SELECT * FROM news_sources WHERE sourceId = :sourceId")
    suspend fun findById(sourceId: Long): NewsSourceEntity?

    @Query("SELECT * FROM news_sources WHERE sourceKey = :sourceKey")
    suspend fun findByKey(sourceKey: String): NewsSourceEntity?

    @Query("SELECT * FROM news_sources WHERE isActive = 1 ORDER BY displayName ASC")
    fun observeActive(): Flow<List<NewsSourceEntity>>
}

@Dao
interface NewsArticleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(article: NewsArticleEntity): Long

    @Update
    suspend fun update(article: NewsArticleEntity)

    @Query("SELECT * FROM news_articles WHERE articleId = :articleId")
    suspend fun findById(articleId: Long): NewsArticleEntity?

    @Query("SELECT * FROM news_articles WHERE sourceId = :sourceId AND externalArticleId = :externalArticleId LIMIT 1")
    suspend fun findByExternalId(sourceId: Long, externalArticleId: String): NewsArticleEntity?

    @Query("SELECT * FROM news_articles WHERE contentHash = :contentHash AND isDeleted = 0")
    suspend fun findByContentHash(contentHash: String): List<NewsArticleEntity>

    @Query("SELECT * FROM news_articles WHERE status = :status ORDER BY publishedAt DESC LIMIT :limit")
    fun observeByStatus(status: NewsArticleStatus, limit: Int = 200): Flow<List<NewsArticleEntity>>

    @Query("SELECT * FROM news_articles WHERE isDeleted = 0 ORDER BY publishedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NewsArticleEntity>>

    @Query("SELECT * FROM news_articles WHERE correctsArticleId = :originalArticleId")
    fun observeCorrections(originalArticleId: Long): Flow<List<NewsArticleEntity>>
}

@Dao
interface NewsCategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: NewsCategoryEntity): Long

    @Query("SELECT * FROM news_categories WHERE categoryId = :categoryId")
    suspend fun findById(categoryId: Long): NewsCategoryEntity?

    @Query("SELECT * FROM news_categories WHERE code = :code")
    suspend fun findByCode(code: String): NewsCategoryEntity?

    @Query("SELECT * FROM news_categories WHERE isActive = 1 ORDER BY displayName ASC")
    fun observeActive(): Flow<List<NewsCategoryEntity>>

    @Query("SELECT * FROM news_categories WHERE parentCategoryId = :parentCategoryId")
    fun observeChildren(parentCategoryId: Long): Flow<List<NewsCategoryEntity>>
}

@Dao
interface NewsCategoryLinkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(links: List<NewsCategoryLinkEntity>): List<Long>

    @Query("SELECT * FROM news_category_links WHERE articleId = :articleId")
    fun observeForArticle(articleId: Long): Flow<List<NewsCategoryLinkEntity>>

    @Query("SELECT * FROM news_category_links WHERE categoryId = :categoryId")
    fun observeForCategory(categoryId: Long): Flow<List<NewsCategoryLinkEntity>>
}

@Dao
interface NewsInstrumentLinkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(links: List<NewsInstrumentLinkEntity>): List<Long>

    @Query("SELECT * FROM news_instrument_links WHERE linkId = :linkId")
    suspend fun findById(linkId: Long): NewsInstrumentLinkEntity?

    @Query("SELECT * FROM news_instrument_links WHERE articleId = :articleId")
    fun observeForArticle(articleId: Long): Flow<List<NewsInstrumentLinkEntity>>

    /** Primary read pattern per the approved blueprint §9.3 — "recent news for instrument X". */
    @Query(
        """
        SELECT l.* FROM news_instrument_links l
        INNER JOIN news_articles a ON a.articleId = l.articleId
        WHERE l.instrumentId = :instrumentId AND a.isDeleted = 0
        ORDER BY a.publishedAt DESC LIMIT :limit
        """
    )
    fun observeRecentForInstrument(instrumentId: Long, limit: Int = 100): Flow<List<NewsInstrumentLinkEntity>>
}

@Dao
interface SentimentScoreDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(score: SentimentScoreEntity): Long

    @Query("SELECT * FROM sentiment_scores WHERE articleId = :articleId ORDER BY computedAt DESC")
    fun observeForArticle(articleId: Long): Flow<List<SentimentScoreEntity>>

    @Query("SELECT * FROM sentiment_scores WHERE articleId = :articleId AND modelKey = :modelKey ORDER BY computedAt DESC LIMIT 1")
    suspend fun findLatestForModel(articleId: Long, modelKey: String): SentimentScoreEntity?

    @Query("SELECT * FROM sentiment_scores WHERE instrumentLinkId = :instrumentLinkId ORDER BY computedAt DESC")
    fun observeForInstrumentLink(instrumentLinkId: Long): Flow<List<SentimentScoreEntity>>
}

@Dao
interface NewsDuplicateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(duplicate: NewsDuplicateEntity): Long

    @Query("SELECT * FROM news_duplicates WHERE primaryArticleId = :primaryArticleId")
    fun observeDuplicatesOf(primaryArticleId: Long): Flow<List<NewsDuplicateEntity>>

    @Query("SELECT * FROM news_duplicates WHERE duplicateArticleId = :duplicateArticleId LIMIT 1")
    suspend fun findPrimaryFor(duplicateArticleId: Long): NewsDuplicateEntity?
}
