package com.jarvis.tidb.intelligence.pattern.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId

/**
 * TRADING-006 — Module 5 Phase 2: Pattern catalog.
 *
 * [com.jarvis.tidb.historical.evidence.entity.PatternOccurrenceEntity] (Module 4, schema v5)
 * already records *instances* of a detected pattern by free-text `patternName`. What was
 * missing — and is genuinely new here — is [PatternEntity]: a master catalog/definition table
 * so a pattern's canonical rules, family, and description exist exactly once instead of being
 * re-typed into every occurrence row. `pattern_occurrences` gains a nullable `patternId` column
 * via `MIGRATION_5_6` (additive; `patternName` is untouched and remains the display/legacy
 * field) as a logical-only reference — deliberately not a Room `@ForeignKey`, consistent with
 * how Module 4 already treats cross-cutting references, and so that Module 4's entity/table
 * definition itself never has to be edited.
 */

enum class PatternFamily(val value: String) {
    CANDLESTICK("CANDLESTICK"),
    CHART_FORMATION("CHART_FORMATION"),
    STATISTICAL("STATISTICAL"),
    VOLUME("VOLUME"),
    SEASONAL("SEASONAL"),
    CUSTOM("CUSTOM");

    companion object {
        fun from(value: String): PatternFamily = entries.firstOrNull { it.value == value } ?: CUSTOM
    }
}

/** The canonical, reusable definition of a named pattern. Occurrences (Module 4) point back at this by [patternKey]/[patternId]. */
@Entity(
    tableName = "patterns",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["patternKey"], unique = true),
        Index(value = ["family"]),
        Index(value = ["isActive"])
    ]
)
data class PatternEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "patternId") val patternId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    /** Stable machine slug — by convention matches `PatternOccurrenceEntity.patternName`. */
    @ColumnInfo(name = "patternKey") val patternKey: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "family") val family: PatternFamily,
    @ColumnInfo(name = "description") val description: String,
    /** Canonical rule parameters (thresholds, ratios, lookback) as JSON — interpretation is a future decision engine's job, not this table's. */
    @ColumnInfo(name = "definitionJson") val definitionJson: String? = null,
    @ColumnInfo(name = "isActive") val isActive: Boolean = true,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)
