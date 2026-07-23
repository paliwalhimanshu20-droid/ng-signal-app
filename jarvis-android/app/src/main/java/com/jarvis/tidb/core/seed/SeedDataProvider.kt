package com.jarvis.tidb.core.seed

import com.jarvis.tidb.core.TidbDatabase
import com.jarvis.tidb.core.entity.AssetClass
import com.jarvis.tidb.core.entity.AuditMetadata
import com.jarvis.tidb.core.entity.ContractEntity
import com.jarvis.tidb.core.entity.ContractTradingStatus
import com.jarvis.tidb.core.entity.ExchangeEntity
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.InstrumentType
import com.jarvis.tidb.core.entity.RecordStatus
import java.util.Calendar
import java.util.TimeZone

/**
 * Default seed records inserted exactly once, on first-ever database creation
 * (see [TidbDatabase.buildDatabase]'s `onCreate` callback).
 *
 * Seeds:
 *  - Exchange: MCX (Multi Commodity Exchange of India)
 *  - Instrument: NATGASMINI
 *  - Contract: one active futures contract for NATGASMINI, expiring at the end of next month
 *
 * All seeded rows get a fresh [com.jarvis.tidb.core.entity.GlobalId] (default constructor
 * value), `createdBy`/`updatedBy` = "SYSTEM_SEED" so they're distinguishable from
 * user/sync-created rows, and start at `version = 1`, `isDeleted = false` — no code here needs
 * to touch those fields directly, [AuditMetadata.created] and the entity defaults handle it.
 */
object SeedDataProvider {

    private const val SEED_ACTOR = "SYSTEM_SEED"

    suspend fun seed(database: TidbDatabase) {
        val exchangeDao = database.exchangeDao()
        val instrumentDao = database.instrumentDao()
        val contractDao = database.contractDao()

        if (exchangeDao.count() > 0) return // already seeded — never re-seed

        val now = System.currentTimeMillis()
        val audit = AuditMetadata.created(now, SEED_ACTOR)

        val mcxId = exchangeDao.insert(
            ExchangeEntity(
                exchangeCode = "MCX",
                exchangeName = "Multi Commodity Exchange of India",
                timezone = "Asia/Kolkata",
                country = "India",
                currency = "INR",
                status = RecordStatus.ACTIVE,
                audit = audit
            )
        )

        val natGasMiniId = instrumentDao.insert(
            InstrumentEntity(
                symbol = "NATGASMINI",
                displayName = "Natural Gas Mini",
                exchangeId = mcxId,
                assetClass = AssetClass.COMMODITY,
                instrumentType = InstrumentType.FUTURE,
                tickSize = 0.1,
                lotSize = 250,
                multiplier = 250.0,
                quoteCurrency = "INR",
                tradingCurrency = "INR",
                tradingHours = "09:00-23:30",
                status = RecordStatus.ACTIVE,
                // External identifiers intentionally left null — populated later by a
                // broker/vendor onboarding step, not at seed time.
                audit = audit
            )
        )

        contractDao.insert(
            ContractEntity(
                instrumentId = natGasMiniId,
                expiryDate = endOfNextMonth(now),
                rollDate = endOfNextMonth(now) - DAY_MILLIS * 3,
                contractSize = 250.0,
                marginRequirement = 0.0, // to be populated by a future risk/margin module
                tradingStatus = ContractTradingStatus.ACTIVE,
                audit = audit
            )
        )
    }

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    private fun endOfNextMonth(fromEpochMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        calendar.timeInMillis = fromEpochMillis
        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 30)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
