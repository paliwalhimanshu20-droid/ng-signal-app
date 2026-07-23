package com.jarvis.tidb.core.common

import androidx.room.ColumnInfo
import java.util.UUID

/**
 * Shared identity/audit/soft-delete building blocks used across every table in the unified
 * Trading Intelligence Database. These originated in Module 1 Revision 1 and are now the
 * single copy consumed by `core`, `signals`, and `analytics` alike — one of the concrete
 * benefits of merging into one physical database: no more near-duplicate metadata classes
 * per module.
 *
 * Note on `signals.*` entities: the original Module 2 build predates this shared class and
 * used flat `updatedAt`/`updatedBy`/`version` columns directly on `SignalEntity` rather than
 * `@Embedded AuditMetadata`. Per the "do not redesign the architecture, do not remove existing
 * functionality" instruction, those flat columns are preserved as-is on `SignalEntity` (see
 * `signals/entity/SignalEntities.kt`) rather than retrofitted to embed this class — the two
 * shapes are column-compatible (same names/types), so nothing downstream breaks.
 */
data class AuditMetadata(
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "createdBy")
    val createdBy: String = "SYSTEM",

    @ColumnInfo(name = "updatedBy")
    val updatedBy: String = "SYSTEM",

    /** Optimistic-locking / sync version counter. Starts at 1, increments on every update. */
    @ColumnInfo(name = "version")
    val version: Long = 1L
) {
    companion object {
        fun created(now: Long = System.currentTimeMillis(), actor: String = "SYSTEM") = AuditMetadata(
            createdAt = now,
            updatedAt = now,
            createdBy = actor,
            updatedBy = actor,
            version = 1L
        )
    }

    /** Returns a copy representing "this row updated now by actor", with version bumped. */
    fun touched(now: Long = System.currentTimeMillis(), actor: String = updatedBy): AuditMetadata =
        copy(updatedAt = now, updatedBy = actor, version = version + 1)
}

/**
 * Soft-delete support. Business records are never physically deleted through the normal
 * repository API — [isDeleted] is flipped and [deletedAt] stamped instead. Repositories filter
 * `isDeleted = 0` by default; a physical hard-delete remains available on DAOs for admin/GDPR/
 * test-cleanup use only.
 */
data class SoftDeleteMetadata(
    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deletedAt")
    val deletedAt: Long? = null
) {
    companion object {
        fun notDeleted() = SoftDeleteMetadata(isDeleted = false, deletedAt = null)
    }

    fun markDeleted(now: Long = System.currentTimeMillis()) = SoftDeleteMetadata(isDeleted = true, deletedAt = now)
}

/** Generates a new random UUID string. Centralized so the format is consistent everywhere in the unified database. */
object GlobalId {
    fun new(): String = UUID.randomUUID().toString()
}
