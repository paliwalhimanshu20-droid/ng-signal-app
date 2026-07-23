package com.jarvis.tidb.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jarvis.tidb.core.dao.ContractDao
import com.jarvis.tidb.core.dao.ExchangeDao
import com.jarvis.tidb.core.dao.HistoricalCandleDao
import com.jarvis.tidb.core.dao.InstrumentDao
import com.jarvis.tidb.core.dao.LiveMarketSnapshotDao
import com.jarvis.tidb.core.dao.MarketEventDao
import com.jarvis.tidb.core.dao.MarketSessionDao
import com.jarvis.tidb.core.entity.ContractEntity
import com.jarvis.tidb.core.entity.ExchangeEntity
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.LiveMarketSnapshotEntity
import com.jarvis.tidb.core.entity.MarketEventEntity
import com.jarvis.tidb.core.entity.MarketSessionEntity
import com.jarvis.tidb.core.seed.SeedDataProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * JARVIS Trading Intelligence Database — Module 1: Core Market Foundation.
 *
 * This is the single source of truth for market structure and market data. Every future
 * module (Signals, Backtesting, Strategy Engine, AI Learning, Instrument DNA, Performance
 * Analytics) reads from and depends on the tables defined here; none of them own or
 * duplicate this data.
 *
 * Schema version 2 (Revision 1): adds universal UUIDs, audit columns, soft delete, external
 * provider identifiers, and candle data-provenance columns to every table — see
 * [MIGRATION_1_2] and docs/database/TRADING-001-Core-Market-Foundation.md §10.
 *
 * Destructive fallback is disabled on purpose in every environment: historical candle data
 * must never be silently wiped by a schema change. Every version bump ships a real
 * [Migration].
 */
@Database(
    entities = [
        ExchangeEntity::class,
        InstrumentEntity::class,
        ContractEntity::class,
        MarketSessionEntity::class,
        HistoricalCandleEntity::class,
        LiveMarketSnapshotEntity::class,
        MarketEventEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TidbDatabase : RoomDatabase() {

    abstract fun exchangeDao(): ExchangeDao
    abstract fun instrumentDao(): InstrumentDao
    abstract fun contractDao(): ContractDao
    abstract fun marketSessionDao(): MarketSessionDao
    abstract fun historicalCandleDao(): HistoricalCandleDao
    abstract fun liveMarketSnapshotDao(): LiveMarketSnapshotDao
    abstract fun marketEventDao(): MarketEventDao

    companion object {
        private const val DATABASE_NAME = "jarvis_tidb.db"

        @Volatile
        private var INSTANCE: TidbDatabase? = null

        fun getInstance(context: Context): TidbDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): TidbDatabase {
            var freshlyCreated = false

            val database = Room.databaseBuilder(context, TidbDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                // Historical candle data is precious; never destructively wipe on schema drift.
                // A real migration must be written for every version bump.
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // onCreate fires synchronously during the first query, before this
                        // method returns and before INSTANCE is assigned — so we can't touch
                        // INSTANCE here. Just flag it; seeding happens below once we have a
                        // safe reference to the built instance.
                        freshlyCreated = true
                    }
                })
                .build()

            if (freshlyCreated) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    SeedDataProvider.seed(database)
                }
            }

            return database
        }

        /**
         * v1 -> v2 (Revision 1): adds uuid / audit columns (createdBy, updatedBy, version) /
         * soft-delete columns to every table, external-provider columns to instruments and
         * contracts, and data-provenance columns to historical_candles.
         *
         * `createdAt`/`updatedAt` already existed in v1 under the same names, so they are left
         * untouched — only genuinely new columns are added. Every ADD COLUMN uses a concrete
         * default so existing rows (including the seeded MCX/NATGASMINI records) become valid
         * immediately; UUIDs are then backfilled per-row since SQLite can't express "random
         * default" in a column default clause.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val tablesNeedingCoreRevisionColumns = listOf(
                    "exchanges", "instruments", "contracts", "market_sessions",
                    "historical_candles", "live_market_snapshots", "market_events"
                )

                for (table in tablesNeedingCoreRevisionColumns) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN createdBy TEXT NOT NULL DEFAULT 'SYSTEM'")
                    db.execSQL("ALTER TABLE $table ADD COLUMN updatedBy TEXT NOT NULL DEFAULT 'SYSTEM'")
                    db.execSQL("ALTER TABLE $table ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE $table ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                }

                // §4 External provider mapping — instruments and contracts only.
                for (table in listOf("instruments", "contracts")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN brokerInstrumentKey TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE $table ADD COLUMN exchangeToken TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE $table ADD COLUMN isin TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE $table ADD COLUMN vendorMetadata TEXT DEFAULT NULL")
                }

                // §5 Data provenance — historical_candles only.
                db.execSQL("ALTER TABLE historical_candles ADD COLUMN sourceId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE historical_candles ADD COLUMN importBatchId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE historical_candles ADD COLUMN checksum TEXT DEFAULT NULL")

                // Backfill a real UUID v4 into every existing row (ADD COLUMN can't generate
                // one per-row via DEFAULT). abs(random()) is evaluated fresh per row in SQLite.
                for (table in tablesNeedingCoreRevisionColumns) {
                    db.execSQL(
                        """
                        UPDATE $table SET uuid =
                          lower(hex(randomblob(4))) || '-' ||
                          lower(hex(randomblob(2))) || '-4' ||
                          substr(lower(hex(randomblob(2))), 2) || '-' ||
                          substr('89ab', abs(random()) % 4 + 1, 1) ||
                          substr(lower(hex(randomblob(2))), 2) || '-' ||
                          lower(hex(randomblob(6)))
                        WHERE uuid = ''
                        """.trimIndent()
                    )
                }

                // Unique indexes on the newly-backfilled uuid columns.
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exchanges_uuid ON exchanges(uuid)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_instruments_uuid ON instruments(uuid)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contracts_uuid ON contracts(uuid)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_market_sessions_uuid ON market_sessions(uuid)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_historical_candles_uuid ON historical_candles(uuid)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_live_market_snapshots_uuid ON live_market_snapshots(uuid)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_market_events_uuid ON market_events(uuid)")

                // isDeleted filter indexes.
                for (table in tablesNeedingCoreRevisionColumns) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_isDeleted ON $table(isDeleted)")
                }

                // Remaining new indexes matching the entity definitions exactly.
                db.execSQL("CREATE INDEX IF NOT EXISTS index_instruments_brokerInstrumentKey ON instruments(brokerInstrumentKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_instruments_exchangeToken ON instruments(exchangeToken)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contracts_brokerInstrumentKey ON contracts(brokerInstrumentKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contracts_exchangeToken ON contracts(exchangeToken)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_historical_candles_importBatchId ON historical_candles(importBatchId)")
            }
        }
    }
}
