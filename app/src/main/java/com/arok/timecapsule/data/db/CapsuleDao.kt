package com.arok.timecapsule.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CapsuleDao {
    @Query("SELECT * FROM capsules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CapsuleEntity>>

    @Query("SELECT * FROM capsules WHERE id = :id")
    suspend fun get(id: Long): CapsuleEntity?

    @Query("SELECT COUNT(*) FROM capsules")
    suspend fun count(): Int

    @Insert
    suspend fun insert(capsule: CapsuleEntity): Long

    @Insert
    suspend fun insertAll(capsules: List<CapsuleEntity>)

    @Update
    suspend fun update(capsule: CapsuleEntity)

    @Query("UPDATE capsules SET opened = 1 WHERE id = :id")
    suspend fun markOpened(id: Long)
}
