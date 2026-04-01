package com.widgey.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.widgey.data.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SyncQueueEntity): Long

    @Update
    suspend fun update(entry: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE node_id = :nodeId LIMIT 1")
    suspend fun getByNodeId(nodeId: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE next_retry_at <= :currentTime ORDER BY next_retry_at ASC")
    suspend fun getPendingItems(currentTime: Long = System.currentTimeMillis()): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY next_retry_at ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY next_retry_at ASC")
    fun observeAll(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM sync_queue WHERE node_id = :nodeId")
    suspend fun deleteByNodeId(nodeId: String)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()

    @Query("UPDATE sync_queue SET next_retry_at = :time")
    suspend fun resetAllRetryTimes(time: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET retry_count = retry_count + 1, next_retry_at = :nextRetryAt WHERE id = :id")
    suspend fun incrementRetryCount(id: Long, nextRetryAt: Long)
}
