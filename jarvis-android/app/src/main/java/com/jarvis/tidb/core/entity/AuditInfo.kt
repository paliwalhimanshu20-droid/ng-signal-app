package com.jarvis.tidb.core.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import java.util.UUID

/**
 * Shared, embedded (not standalone-entity) column groups added in Revision 1. Every
 * production entity in this module embeds both [AuditMetadata] and [SoftDeleteMetadata] in
 * addition to its own local primary key and a [uuid][GlobalId] field.
 *
 * Why @Embedded and not a base class: Room has no first-class entity inheritance, and
 * @Embedded is the idiomatic way to share a column group across entities without copy-paste
 * drift while still producing flat, ordinary SQLite tables — no joins, no extra tables.
 */

/**
 * Audit trail + optimistic-locking support, required on every entity per Revision 1 §2.
 *
 * [version] increments on every update and should be used as a conditional in future
 * cloud-sync `UPDATE ... WHERE version = :expectedVersion` calls (optimistic locking).
 */
data class AuditMetadata(
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,

    /** Identity of the actor (user id, service name, or "SYSTEM") that created the row. */
    @ColumnInfo(name = "createdBy")
    val createdBy: String = "SYSTEM",

    /** Identity of the actor that last modified the row. */
    @ColumnInfo(name = "updatedBy")
    val updatedBy: String = "SYSTEM",

    /** Optimistic-locking / sync version counter. Starts at 1, increments on every update. */
    @ColumnInfo(name = "version")
    val version: Long = 1L
) {
    companion object {
        fun created(now: Long, actor: String = "SYSTEM") = AuditMetadata(
            createdAt = now,
            updatedAt = now,
            createdBy = actor,
            updatedBy = actor,
            version = 1L
        )
    }

    /** Returns a copy representing "this row updated now by actor", with version bumped. */
    fun touched(now: Long, actor: String = updatedBy): AuditMetadata =
        copy(updatedAt = now, updatedBy = actor, version = version + 1)
}

/**
 * Soft-delete support, required on every entity per Revision 1 §3. Business records are never
 * physically deleted through the normal repository API — [isDeleted] is flipped and
 * [deletedAt] stamped instead. Repositories filter `isDeleted = 0` by default; a physical
 * `hardDelete` remains available on DAOs for admin/GDPR/test-cleanup use only.
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

    fun markDeleted(now: Long) = SoftDeleteMetadata(isDeleted = true, deletedAt = now)
}

/** Generates a new random UUID string. Centralized so the format is consistent everywhere. */
object GlobalId {
    fun new(): String = UUID.randomUUID().toString()
}
