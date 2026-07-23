package com.jarvis.tidb.core.repository

import com.jarvis.tidb.core.dao.ContractDao
import com.jarvis.tidb.core.entity.ContractEntity
import com.jarvis.tidb.core.entity.ContractTradingStatus
import kotlinx.coroutines.flow.Flow

interface ContractRepository {
    suspend fun upsert(contract: ContractEntity): Long
    suspend fun upsertAll(contracts: List<ContractEntity>): List<Long>
    suspend fun delete(contract: ContractEntity)
    suspend fun softDelete(contractId: Long, actor: String = "SYSTEM")
    fun observeById(contractId: Long): Flow<ContractEntity?>
    suspend fun getByUuid(uuid: String): ContractEntity?
    fun observeByInstrument(instrumentId: Long): Flow<List<ContractEntity>>
    fun observeByInstrumentAndStatus(
        instrumentId: Long,
        status: ContractTradingStatus = ContractTradingStatus.ACTIVE
    ): Flow<List<ContractEntity>>
    suspend fun getNearestActiveContract(instrumentId: Long): ContractEntity?
    fun observeExpiringBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<ContractEntity>>
}

class ContractRepositoryImpl(
    private val dao: ContractDao
) : ContractRepository {

    override suspend fun upsert(contract: ContractEntity): Long =
        if (contract.contractId == 0L) dao.insert(contract) else {
            dao.update(contract); contract.contractId
        }

    override suspend fun upsertAll(contracts: List<ContractEntity>): List<Long> = dao.insertAll(contracts)

    override suspend fun delete(contract: ContractEntity) = dao.delete(contract)

    override suspend fun softDelete(contractId: Long, actor: String) =
        dao.softDelete(contractId, System.currentTimeMillis(), actor)

    override fun observeById(contractId: Long): Flow<ContractEntity?> = dao.observeById(contractId)

    override suspend fun getByUuid(uuid: String): ContractEntity? = dao.getByUuid(uuid)

    override fun observeByInstrument(instrumentId: Long): Flow<List<ContractEntity>> =
        dao.observeByInstrument(instrumentId)

    override fun observeByInstrumentAndStatus(
        instrumentId: Long,
        status: ContractTradingStatus
    ): Flow<List<ContractEntity>> = dao.observeByInstrumentAndStatus(instrumentId, status)

    override suspend fun getNearestActiveContract(instrumentId: Long): ContractEntity? =
        dao.getNearestActiveContract(instrumentId)

    override fun observeExpiringBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<ContractEntity>> =
        dao.observeExpiringBetween(fromEpochMillis, toEpochMillis)
}
