# TRADING-001 — Core Market Foundation

**Module:** 1 of N — Trading Intelligence Database (TIDB), JARVIS AI Operating System
**Status:** Implemented, Revision 1 applied
**Schema version:** 2
**Depends on:** nothing (this is the foundation layer)
**Depended on by:** Signals, Backtesting, Strategy Engine, AI Learning, Instrument DNA, Performance Analytics (all future modules)

**Revision history**
| Version | Schema version | Summary |
|---|---|---|
| Initial | 1 | Core entities, DAOs, repositories, seed data. |
| Revision 1 | 2 | Universal UUIDs, full audit trail, soft delete, external provider IDs, candle data provenance, expanded docs. Fully backward-compatible — see §10. |

---

## 1. Architecture Overview

The Core Market Foundation is the single source of truth for market structure and market
data inside JARVIS. It is implemented as one Room database (`TidbDatabase`) with seven
entities, a DAO per entity, and a Repository per entity exposing Kotlin `Flow` APIs — Clean
Architecture, data layer only (no ViewModel/UI layer is part of this module).

```
UI / ViewModels (future modules)
        |
        v
  Repositories (interface -> impl, Flow<T>)
        |
        v
      DAOs
        |
        v
  Room / SQLite  (jarvis_tidb.db)
```

Every entity is asset-class-agnostic. Natural Gas is the current priority, but nothing in the
schema hardcodes commodities — `Instrument.assetClass` and `Instrument.instrumentType` are
what differentiate a commodity future from an equity, an option, a forex pair, or a crypto
perpetual. Adding a new asset class is a matter of inserting new `Exchange`/`Instrument` rows,
not changing the schema.

As of Revision 1, every entity also carries a **globally unique identity, a full audit trail,
and soft-delete semantics** (§4), making the module ready for multi-device sync and a future
cloud/Postgres backend without another structural pass.

---

## 2. Entity Relationship Diagram

```
Exchange
  |- exchangeId PK, uuid, exchangeCode, exchangeName, timezone, country, currency, status
  |- audit { createdAt, updatedAt, createdBy, updatedBy, version }
  |- softDelete { isDeleted, deletedAt }
  |
  |--1:N--> Market Session
  |           |- sessionId PK, uuid, exchangeId FK
  |           |- sessionName, openTime, closeTime, timezone
  |           |- holidayFlag, earlyCloseFlag, remarks
  |           `- audit, softDelete
  |
  `--1:N--> Instrument
              |- instrumentId PK, uuid, symbol (unique), displayName, exchangeId FK
              |- assetClass, instrumentType
              |- tickSize, lotSize, multiplier
              |- quoteCurrency, tradingCurrency, tradingHours, status
              |- brokerInstrumentKey, exchangeToken, isin, vendorMetadata  (external IDs)
              |- audit, softDelete
              |
              |--1:N--> Contract
              |           |- contractId PK, uuid, instrumentId FK
              |           |- expiryDate, rollDate, contractSize
              |           |- marginRequirement, tradingStatus
              |           |- brokerInstrumentKey, exchangeToken, isin, vendorMetadata
              |           `- audit, softDelete
              |
              |--1:N--> Historical Candle
              |           |- candleId PK, uuid, instrumentId FK, timeframe, timestamp
              |           |- open, high, low, close, volume, openInterest
              |           |- source, qualityScore, importedAt
              |           |- sourceId, importBatchId, checksum  (data provenance)
              |           `- audit, softDelete
              |
              |--1:1--> Live Market Snapshot
              |           |- instrumentId PK + FK, uuid
              |           |- lastPrice, bid, ask, spread
              |           |- volume, openInterest, vwap
              |           |- dayHigh, dayLow, previousClose, marketStatus
              |           `- audit, softDelete
              |
              `--1:N--> Market Event
                          |- eventId PK, uuid, instrumentId FK, eventType
                          |- title, description, severity
                          |- timestamp, source, metadata (JSON)
                          `- audit, softDelete
```

*Contract, Historical Candle, Live Market Snapshot, and Market Event are siblings under
Instrument — each has a direct FK to `instrumentId`, not to one another. The linear
"Exchange -> Instrument -> Contract -> Candle -> Snapshot -> Event" ordering in the original
implementation prompt describes data lineage / dependency order, not literal foreign-key
chaining.*

---

## 3. Entity Descriptions

| Entity | Purpose | Revision 1 additions |
|---|---|---|
| **Exchange** | Where an instrument trades — code, name, timezone, country, currency, operational status. Root of the hierarchy. | uuid, audit, soft delete |
| **Instrument** | A tradable symbol (NATGASMINI, CRUDEOIL, GOLD, ...). Asset-class-agnostic; tick size, lot size, multiplier, and currencies live here. | uuid, audit, soft delete, external provider IDs |
| **Contract** | A dated futures/options contract for an instrument — expiry, roll date, contract size, margin, trading status. Unlimited contracts per instrument over time. | uuid, audit, soft delete, external provider IDs |
| **Market Session** | Trading session windows for an exchange, including holiday and early-close flags. | uuid, audit, soft delete |
| **Historical Candle** | OHLCV bars across ten timeframes (1m through Monthly). Expected to be the largest table by row count. | uuid, audit, soft delete, data provenance (sourceId, importBatchId, checksum) |
| **Live Market Snapshot** | The single latest quote per instrument (one row per instrument, upserted continuously). | uuid, audit, soft delete |
| **Market Event** | Discrete timestamped events: expiry, rollover, halt, circuit, high volatility, volume spike, news shock, gap open. | uuid, audit, soft delete |

---

## 4. Universal Policies (Revision 1)

These four policies now apply uniformly to all seven entities. They're implemented as two
shared `@Embedded` column groups (`AuditMetadata`, `SoftDeleteMetadata`) plus a per-entity
`uuid` field, rather than duplicated per-entity — see `entity/AuditInfo.kt`.

### 4.1 Universal identifiers
Every entity has both:
- its existing local `Long` autoIncrement primary key (unchanged — every existing foreign key
  still points at these, so no relationship needed to change), and
- a new `uuid: String` column (`GlobalId.new()`, backed by `java.util.UUID`), unique-indexed,
  generated once at row creation and never reassigned.

The local integer PK stays the fast, storage-cheap key for on-device joins and FKs. The UUID
is the key that survives export to Postgres, a cloud backend, or a peer-to-peer sync protocol
— it means two devices can each create a row offline with zero risk of a colliding identity
once both sync to a shared store.

### 4.2 Audit columns
`AuditMetadata { createdAt, updatedAt, createdBy, updatedBy, version }`, embedded into every
entity. `createdAt`/`updatedAt` keep their original v1 column names and semantics (epoch
millis) — only `createdBy`, `updatedBy`, and `version` are new. `version` starts at 1 and
increments on every update; it's what a future sync layer uses for optimistic locking
(`UPDATE ... WHERE version = :expectedVersion`) instead of last-write-wins.

### 4.3 Soft delete
`SoftDeleteMetadata { isDeleted, deletedAt }`, embedded into every entity. Every DAO exposes
a `softDelete(id, now, actor)` query (`UPDATE ... SET isDeleted = 1, deletedAt = :now, ...`)
and every existing read query now filters `WHERE isDeleted = 0`. The original `@Delete`
physical-delete methods are preserved on every DAO/repository unchanged — they remain
available for admin tooling and test cleanup, but application code should call `softDelete`.

### 4.4 External provider identifiers
`Instrument` and `Contract` gained four optional columns: `brokerInstrumentKey`,
`exchangeToken`, `isin`, `vendorMetadata` (free-form JSON). All nullable, all unpopulated by
seed data — a future broker/vendor-integration module owns filling these in. A contract can
carry its own broker key distinct from its parent instrument's, which matters for futures
where a broker often mints a fresh instrument key per expiry.

---

## 5. Data Provenance (Historical Candle)

`HistoricalCandleEntity` gained three columns beyond the universal policies:
- **`sourceId`** — identifies the *specific* feed/vendor connection that produced a bar
  (finer-grained than the existing `source` enum, which only names the category).
- **`importBatchId`** — groups every row written by one import/backfill run.
  `HistoricalCandleDao.softDeleteByImportBatch(importBatchId, ...)` gives a one-call rollback
  path for a bad batch, without touching unrelated rows.
- **`checksum`** — optional hash of the raw upstream payload, for future dedup/integrity
  verification against the vendor.

The pre-existing `importedAt` field is untouched and keeps meaning "first time this bar was
imported"; `audit.updatedAt` now separately tracks any later correction/re-import.

---

## 6. Timeline Integration Readiness (Revision 1 Section 6)

The Timeline-First Executive Memory engine itself is **not** implemented here, by design. What
this revision guarantees is that every entity already carries what a future timeline generator
needs, with no further schema changes:

| Timeline event type | Sourced from |
|---|---|
| Creation | `audit.createdAt`, `audit.createdBy`, `uuid` |
| Update | `audit.updatedAt`, `audit.updatedBy`, `audit.version` (detects "what changed since last timeline sync" via version delta) |
| Import | `HistoricalCandle.importedAt`, `.sourceId`, `.importBatchId` |
| Contract Expiry | `Contract.expiryDate`, `.rollDate`, `.tradingStatus` |
| Market Events | `MarketEvent` rows are already natural timeline entries — `eventType`, `severity`, `timestamp`, `title`/`description` map directly |
| Deletion / archival | `softDelete.isDeleted`, `softDelete.deletedAt` |

A future Timeline module can therefore build entirely off existing columns via periodic diff
queries (e.g. `WHERE updatedAt > :lastSyncTimestamp`) — no new columns, no backfill, no schema
migration required when that module ships.

---

## 7. Relationships

- `Exchange (1) -> Instrument (N)` — `Instrument.exchangeId` FK, `RESTRICT` on delete.
- `Exchange (1) -> Market Session (N)` — `MarketSession.exchangeId` FK, `CASCADE` on delete.
- `Instrument (1) -> Contract (N)` — `Contract.instrumentId` FK, `CASCADE` on delete.
- `Instrument (1) -> Historical Candle (N)` — `HistoricalCandle.instrumentId` FK, `CASCADE`.
- `Instrument (1) -> Live Market Snapshot (1)` — `LiveMarketSnapshot.instrumentId` is both PK
  and FK, enforcing exactly one snapshot row per instrument. `CASCADE`.
- `Instrument (1) -> Market Event (N)` — `MarketEvent.instrumentId` FK, `CASCADE`.

*(FK cascade behavior is a physical-delete concern; since application code now soft-deletes,
these CASCADE/RESTRICT rules mainly guard against accidental hard deletes via the preserved
admin-only `@Delete` methods.)*

Room relationship POJOs (`Relations.kt`, unchanged by Revision 1) provide hydrated read
models: `ExchangeWithInstruments`, `ExchangeWithSessions`, `InstrumentWithContracts`,
`InstrumentWithLiveSnapshot`, `InstrumentWithEvents`, and `InstrumentFullDetail`.

---

## 8. Indexing Strategy

| Table | Index | Why |
|---|---|---|
| exchanges | unique(`exchangeCode`), unique(`uuid`) | Natural key lookup by short code; global-identity lookup for sync. |
| exchanges | (`status`), (`isDeleted`) | Filtering active/suspended and excluding soft-deleted rows without a scan. |
| instruments | unique(`symbol`), unique(`uuid`) | Symbol is the natural trading key; uuid is the sync/external key. |
| instruments | (`exchangeId`), (`assetClass`), (`status`), (`isDeleted`) | Listing per exchange/asset-class/active-only. |
| instruments | (`brokerInstrumentKey`), (`exchangeToken`) | Resolving inbound broker/exchange feed messages back to a local instrument. |
| contracts | (`instrumentId`), (`expiryDate`), (`instrumentId`,`tradingStatus`), (`isDeleted`) | "Active contract for instrument" and "expiring soon" are the dominant queries. |
| contracts | unique(`uuid`), (`brokerInstrumentKey`), (`exchangeToken`) | Same rationale as instruments. |
| historical_candles | **unique(`instrumentId`,`timeframe`,`timestamp`)** | The core index — doubles as natural key (dedup on re-import) and the primary chart range-scan path. |
| historical_candles | (`timestamp`), (`instrumentId`), (`timeframe`), (`isDeleted`) | Secondary access paths for queries that don't use the full composite key. |
| historical_candles | unique(`uuid`), (`importBatchId`) | External identity; batch-scoped rollback. |
| market_events | (`instrumentId`), (`timestamp`), (`eventType`), (`instrumentId`,`timestamp`), (`isDeleted`) | Per-instrument event timelines and severity/type filtering for AI Learning's future pattern mining. |
| market_events | unique(`uuid`) | Sync/external identity. |
| live_market_snapshots | unique(`uuid`), (`isDeleted`) | Table is capped at one row per instrument; PK lookup is already O(1), these support sync/soft-delete only. |

**Design principle, unchanged from v1:** `historical_candles` is the only table expected to
reach millions of rows, so it gets the most deliberate indexing. Every other table is small
(hundreds to low thousands of rows) and relies on FK/status/isDeleted indexes.

---

## 9. Expected Record Growth

Rough sizing for planning purposes, assuming JARVIS eventually tracks a modest multi-asset
universe (tens of instruments, not thousands):

| Table | Growth driver | Rough scale at 3 years, 50 instruments |
|---|---|---|
| exchanges | Static, one row per venue | tens |
| instruments | Manual/onboarding-driven | hundreds |
| contracts | Monthly/quarterly expiries per futures instrument | low thousands |
| market_sessions | Static per exchange | tens |
| **historical_candles** | 50 instruments x 10 timeframes x years of 1-minute bars | **tens to low hundreds of millions** — dominant table by 2-3 orders of magnitude |
| live_market_snapshots | Exactly one row per instrument, always | hundreds |
| market_events | Event-driven, bursty around volatility/expiry | low hundreds of thousands |

`historical_candles` is the only table that needs partition/archive planning (§11); everything
else stays small enough that a single unpartitioned SQLite table is fine indefinitely.

---

## 10. Migration Path: v1 to v2 (Revision 1)

Implemented as `TidbDatabase.MIGRATION_1_2`, a single `Migration(1, 2)` registered via
`.addMigrations(MIGRATION_1_2)`. No destructive fallback is configured anywhere in this
module — a real migration is mandatory for every version bump, by design, because historical
candle data is irreplaceable.

Steps, in order:
1. `ALTER TABLE ... ADD COLUMN` for `uuid`, `createdBy`, `updatedBy`, `version`, `isDeleted`,
   `deletedAt` on all seven tables, each with a concrete default so every pre-existing row
   (including seed data) is valid the instant the column exists.
2. `ALTER TABLE ... ADD COLUMN` for `brokerInstrumentKey`, `exchangeToken`, `isin`,
   `vendorMetadata` on `instruments` and `contracts` (nullable, no default needed).
3. `ALTER TABLE historical_candles ADD COLUMN` for `sourceId`, `importBatchId`, `checksum`
   (nullable).
4. Per-row UUID backfill via `UPDATE ... SET uuid = <SQLite UUIDv4-shaped expression using
   randomblob()/random()> WHERE uuid = ''` — SQLite has no way to express "random per row" in
   a column `DEFAULT` clause, so this is a necessary second pass after the column exists.
5. `CREATE UNIQUE INDEX` on every table's new `uuid` column, and the remaining new
   non-unique indexes (`isDeleted`, `brokerInstrumentKey`, `exchangeToken`, `importBatchId`),
   matching Room's expected schema exactly so `exportSchema` validation passes.

`createdAt`/`updatedAt` are untouched — they already existed under the same names in v1, so
no data movement was needed for them; `AuditMetadata`'s `@ColumnInfo(name = "createdAt"/"updatedAt")` binds to the pre-existing columns.

---

## 11. Future Partition Strategy

`historical_candles` is the only table this applies to. Options, in likely order of adoption:

1. **Cold-tier archive table** (simplest, SQLite-native): once intraday (1m/3m/5m) candles
   age past a retention window (e.g. 18 months), move them via a background job into a
   `historical_candles_archive` table with the identical schema, then soft-delete (not hard
   delete) the originals. Daily/Weekly/Monthly bars, being orders of magnitude fewer, never
   need archiving.
2. **Per-instrument or per-timeframe physical sharding** if a single device ends up tracking
   a very large instrument universe — out of scope while the universe stays in the tens.
3. **Backend partitioning** (Postgres/warehouse target, §12-13): partition
   `historical_candles` by `timeframe` first (small, fixed cardinality — 10 partitions) and
   optionally sub-partition by month of `timestamp` for the intraday timeframes, which is
   where nearly all row growth concentrates.

None of this requires a schema change to the Room entity itself — `sourceId`/`importBatchId`
already give a partition/archive job everything it needs to select and move data safely.

---

## 12. Migration Path: SQLite (Room) to PostgreSQL

The schema was deliberately kept relational and normalized, so the mapping is direct:

| Room/SQLite | PostgreSQL |
|---|---|
| `INTEGER PRIMARY KEY AUTOINCREMENT` (`exchangeId`, etc.) | `BIGSERIAL PRIMARY KEY`, or keep as a device-local surrogate and promote `uuid` to the canonical cross-system PK |
| `uuid TEXT` | `uuid UUID` (native type, extension `pgcrypto`/`uuid-ossp` for server-side generation going forward) |
| Enum columns stored as `TEXT` (`status`, `assetClass`, `timeframe`, ...) | Native Postgres `ENUM` types, or keep as `TEXT` + `CHECK` constraint — either preserves the same `Converters.kt` string values with zero app-side change |
| `INTEGER` epoch-millis timestamps (`createdAt`, `timestamp`, ...) | `TIMESTAMPTZ`, converting epoch millis to UTC timestamp at migration time |
| `isDeleted INTEGER` (0/1) | `BOOLEAN` |
| Composite unique index on `historical_candles` | Same composite unique constraint, ideally as the table's actual primary key in Postgres (`(instrument_uuid, timeframe, timestamp)`) |
| `vendorMetadata`/`metadata` free-form JSON `TEXT` | Native `JSONB` — enables server-side querying into vendor payloads that SQLite can't do efficiently |

**Recommended approach:** promote `uuid` to be the primary key everywhere in the Postgres
schema (foreign keys reference `uuid`, not the old device-local integer). This is exactly why
Revision 1 added UUIDs now rather than at migration time — retrofitting stable identity after
multiple devices have already generated conflicting local integer keys is far harder than
generating UUIDs from day one.

`version` becomes the natural column for Postgres-side optimistic locking
(`UPDATE ... WHERE version = $1`), and `createdBy`/`updatedBy` slot directly into a Postgres
audit/RLS (row-level security) policy if per-user data isolation is ever needed.

---

## 13. Migration Path: DuckDB / Warehouse Compatibility

`historical_candles` is the table that actually benefits from a columnar/OLAP engine —
everything else is small enough that row-store Postgres is sufficient indefinitely.

- **DuckDB** can read SQLite files directly (`sqlite_scan` / the `sqlite` extension) or Room
  export files without a transformation step, making it a good fit for ad-hoc local analytics
  (backtesting, Instrument DNA feature extraction) directly against the on-device database —
  no ETL needed for exploratory work.
- **Warehouse export** (Parquet, for BigQuery/Snowflake/Redshift, or a data-lake landing
  zone): `historical_candles` maps cleanly to a Parquet dataset partitioned by
  `timeframe=.../year=.../month=...`, using `instrumentId`'s `uuid` as the stable join key
  back to a separately-synced `instruments` dimension table. Enum-as-TEXT columns and
  epoch-millis timestamps both convert to Parquet without loss.
- Because every row already carries `sourceId`/`importBatchId`/`checksum` (§5) and full audit
  metadata (§4.2), a warehouse load job can do idempotent incremental loads
  (`WHERE updatedAt > :lastLoadTimestamp`) instead of full-table reloads — this was a direct
  design goal of adding those columns now rather than later.

---

## 14. Performance Considerations

- `historical_candles` writes should always go through `insertAll` (batch) during
  backfill/import, never row-by-row `insert` in a loop — Room's `@Insert` on a `List<T>` runs
  inside a single transaction, which is dramatically faster on SQLite than N individual
  transactions.
- The composite unique index on `historical_candles` means `OnConflictStrategy.REPLACE`
  inserts double as idempotent upserts — safe to re-run a backfill job without duplicate-row
  cleanup logic.
- All `isDeleted = 0` filters added in Revision 1 are covered by an index on every table, so
  soft-delete did not turn any previously-indexed query into a table scan.
- `live_market_snapshots` is intentionally tiny and hot (one row per instrument, continuously
  upserted) — it deliberately carries no timeframe/range indexes because it's never
  range-queried, only point-looked-up by `instrumentId`.
- For write-heavy live-tick ingestion, enabling WAL journal mode
  (`PRAGMA journal_mode=WAL`) at the Application/DI layer (outside this module's scope, since
  this module doesn't own Application setup) is recommended once wired into a real app.

---

## 15. Synchronization Considerations

Revision 1's audit + soft-delete + UUID additions exist specifically to make a future
multi-device or cloud-sync layer straightforward:

- **Change detection**: `WHERE updatedAt > :lastSyncTimestamp` on every table gives a cheap
  delta-sync query without a separate change-log table.
- **Conflict resolution**: `version` supports optimistic locking (reject a sync write if the
  server's `version` has moved past what the client last saw) instead of blind
  last-write-wins, which would silently lose concurrent edits.
- **Deletion propagation**: because deletes are soft (`isDeleted`/`deletedAt`), a "row deleted"
  event syncs like any other update — no separate tombstone mechanism needed.
- **Identity stability**: `uuid` means two offline devices can each create an `Instrument` or
  `MarketEvent` and merge without collision; the local `Long` PK is device-local only and
  never leaves the device in sync payloads.
- **Actor attribution**: `createdBy`/`updatedBy` let a future sync layer distinguish
  system-imported rows (`"SYSTEM_SEED"`, feed connectors) from user-originated edits, which
  matters for conflict-resolution policy (e.g. "user edits win over feed re-imports").

---

## 16. Design Decisions

Carried over from the original implementation, still true after Revision 1:

- **Room + Kotlin + Coroutines/Flow + Repository pattern.** DAOs return `Flow` for read
  queries; single-shot unique-key lookups are plain `suspend fun`.
- **Enums stored as String, not ordinal**, via `Converters.kt` — reordering or extending an
  enum can never silently corrupt existing rows.
- **`REPLACE` conflict strategy scoped narrowly**: `HistoricalCandle` (composite-unique-index
  upsert is intentional) and `LiveMarketSnapshot` (PK-per-instrument requires it). Everything
  else uses `ABORT` on single insert, `REPLACE`-based `insertAll` reserved for bulk
  seeding/sync jobs.
- **No destructive migration fallback**, ever — reinforced, not changed, by Revision 1: a real
  `Migration` is mandatory for every version bump.
- **Manual DI (`TidbModule`), not Hilt/Koin** — still framework-agnostic.
- **Explicitly not implemented here** (per both the original prompt and Revision 1): Signals,
  Trades, Strategy Engine, Backtesting, Performance Analytics, Instrument DNA, and the
  Timeline engine itself (only Timeline *readiness*, §6).

New in Revision 1:
- **`@Embedded`, not entity inheritance**, for `AuditMetadata`/`SoftDeleteMetadata` — Room has
  no first-class entity inheritance; `@Embedded` shares the column group across all seven
  entities without copy-paste drift while still producing flat, ordinary tables.
- **Soft delete via explicit `softDelete()` DAO methods, not a Room-level interceptor** — Room
  has no query-rewriting hook, so every DAO's read queries were updated by hand to add
  `WHERE isDeleted = 0`. This is intentional and explicit rather than "magic," at the cost of
  needing this discipline maintained in any future DAO added to the module.
- **Physical `@Delete` preserved everywhere**, not removed — satisfies "do not remove existing
  functionality" while making `softDelete` the recommended path for application code.

---

## 17. Repository Contracts

Every entity follows the same two-file pattern (Revision 1 §7, formalized — was already the
structure in v1, now explicitly documented as a contract downstream modules can rely on):

```
XxxRepository                     (interface — what consumers depend on)
      |
      v
XxxRepositoryImpl(dao: XxxDao)    (implementation — Room-backed today)
```

Consumers (Signals, Strategy Engine, etc.) must depend on the interface type, never the impl
class, never the DAO directly. This is what allows a future cloud-backed `ExchangeRepositoryImpl`
(talking to Postgres over the network instead of Room) to be substituted with zero changes to
any calling code — `TidbModule` is the single place that wires interface to concrete impl.

---

## 18. Seed Data

Inserted once, on first-ever database creation (`TidbDatabase` `onCreate` callback ->
`SeedDataProvider.seed()`), unchanged in substance from v1, updated only to populate the new
required `audit` field:

- **Exchange**: MCX (Multi Commodity Exchange of India), timezone `Asia/Kolkata`, currency INR.
- **Instrument**: NATGASMINI (Natural Gas Mini), asset class COMMODITY, instrument type FUTURE, lot size 250.
- **Contract**: one ACTIVE futures contract for NATGASMINI, expiring at end of next calendar month.

All three seeded rows carry `createdBy = updatedBy = "SYSTEM_SEED"`, `version = 1`,
`isDeleted = false`, and a freshly generated `uuid`.

---

## 19. File Map

```
app/src/main/java/com/jarvis/tidb/core/
├── entity/
│   ├── Enums.kt
│   ├── AuditInfo.kt                    (NEW - AuditMetadata, SoftDeleteMetadata, GlobalId)
│   ├── ExchangeEntity.kt
│   ├── InstrumentEntity.kt
│   ├── ContractEntity.kt
│   ├── MarketSessionEntity.kt
│   ├── HistoricalCandleEntity.kt
│   ├── LiveMarketSnapshotEntity.kt
│   ├── MarketEventEntity.kt
│   └── Relations.kt
├── dao/
│   ├── ExchangeDao.kt
│   ├── InstrumentDao.kt
│   ├── ContractDao.kt
│   ├── MarketSessionDao.kt
│   ├── HistoricalCandleDao.kt
│   ├── LiveMarketSnapshotDao.kt
│   └── MarketEventDao.kt
├── repository/
│   ├── ExchangeRepository.kt
│   ├── InstrumentRepository.kt
│   ├── ContractRepository.kt
│   ├── MarketSessionRepository.kt
│   ├── HistoricalCandleRepository.kt
│   ├── LiveMarketSnapshotRepository.kt
│   └── MarketEventRepository.kt
├── seed/
│   └── SeedDataProvider.kt
├── Converters.kt
├── TidbDatabase.kt                     (schema v2, MIGRATION_1_2)
└── TidbModule.kt

docs/database/
└── TRADING-001-Core-Market-Foundation.md   (this file)
```

---

**Next step:** per both the original and Revision 1 instructions, start a new chat before
beginning Module 2 (Signals) — it should depend on this module's repository interfaces, not
reimplement or directly query any of this schema.
