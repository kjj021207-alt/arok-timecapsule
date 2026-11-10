package com.arok.timecapsule.data

import com.arok.timecapsule.data.db.CapsuleDao
import com.arok.timecapsule.data.db.CapsuleEntity
import kotlinx.coroutines.flow.Flow

class CapsuleRepository(private val dao: CapsuleDao) {
    val capsules: Flow<List<CapsuleEntity>> = dao.observeAll()

    suspend fun seedIfEmpty() {
        if (dao.count() == 0) dao.insertAll(SampleData.capsules)
    }

    suspend fun add(capsule: CapsuleEntity): Long = dao.insert(capsule)
    suspend fun open(id: Long) = dao.markOpened(id)
    suspend fun update(capsule: CapsuleEntity) = dao.update(capsule)
}
