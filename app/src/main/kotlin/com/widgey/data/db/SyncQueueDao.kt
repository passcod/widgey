package com.widgey.data.db

import android.content.ContentValues
import android.database.Cursor
import com.widgey.data.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class SyncQueueDao internal constructor(
    private val db: AppDatabase,
    private val changeNotifier: DatabaseChangeNotifier
) {

    // ---- suspend queries ----

    suspend fun getByNodeId(nodeId: String): SyncQueueEntity? = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "sync_queue", null, "node_id = ?", arrayOf(nodeId), null, null, null, "1"
        ).use { if (it.moveToFirst()) it.toEntity() else null }
    }

    suspend fun getPendingItems(currentTime: Long = System.currentTimeMillis()): List<SyncQueueEntity> =
        withContext(Dispatchers.IO) {
            db.readableDatabase.query(
                "sync_queue", null, "next_retry_at <= ?",
                arrayOf(currentTime.toString()), null, null, "next_retry_at ASC"
            ).use { it.toList() }
        }

    suspend fun getAll(): List<SyncQueueEntity> = withContext(Dispatchers.IO) {
        db.readableDatabase.query("sync_queue", null, null, null, null, null, "next_retry_at ASC")
            .use { it.toList() }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        db.readableDatabase.query("sync_queue", arrayOf("COUNT(*)"), null, null, null, null, null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // ---- suspend writes ----

    suspend fun insert(entry: SyncQueueEntity): Long = withContext(Dispatchers.IO) {
        val rowId = db.writableDatabase.insertWithOnConflict(
            "sync_queue", null, entry.toContentValues(),
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        changeNotifier.invalidateSyncQueue()
        rowId
    }

    suspend fun update(entry: SyncQueueEntity) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "sync_queue", entry.toContentValues(), "id = ?", arrayOf(entry.id.toString())
        )
        changeNotifier.invalidateSyncQueue()
    }

    suspend fun deleteByNodeId(nodeId: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("sync_queue", "node_id = ?", arrayOf(nodeId))
        changeNotifier.invalidateSyncQueue()
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("sync_queue", "id = ?", arrayOf(id.toString()))
        changeNotifier.invalidateSyncQueue()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("sync_queue", null, null)
        changeNotifier.invalidateSyncQueue()
    }

    suspend fun resetAllRetryTimes(time: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply { put("next_retry_at", time) }
            db.writableDatabase.update("sync_queue", values, null, null)
            changeNotifier.invalidateSyncQueue()
        }

    suspend fun incrementRetryCount(id: Long, nextRetryAt: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.execSQL(
            "UPDATE sync_queue SET retry_count = retry_count + 1, next_retry_at = ? WHERE id = ?",
            arrayOf(nextRetryAt, id)
        )
        changeNotifier.invalidateSyncQueue()
    }

    // ---- observe flows ----

    fun observeAll(): Flow<List<SyncQueueEntity>> =
        changeNotifier.syncQueue
            .onStart { emit(Unit) }
            .map {
                db.readableDatabase.query(
                    "sync_queue", null, null, null, null, null, "next_retry_at ASC"
                ).use { it.toList() }
            }
            .flowOn(Dispatchers.IO)

    fun observeCount(): Flow<Int> =
        changeNotifier.syncQueue
            .onStart { emit(Unit) }
            .map {
                db.readableDatabase.query(
                    "sync_queue", arrayOf("COUNT(*)"), null, null, null, null, null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
            }
            .flowOn(Dispatchers.IO)

    // ---- private helpers ----

    private fun Cursor.toEntity(): SyncQueueEntity = SyncQueueEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        nodeId = getString(getColumnIndexOrThrow("node_id")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        retryCount = getInt(getColumnIndexOrThrow("retry_count")),
        nextRetryAt = getLong(getColumnIndexOrThrow("next_retry_at"))
    )

    private fun Cursor.toList(): List<SyncQueueEntity> = buildList {
        while (moveToNext()) add(toEntity())
    }

    private fun SyncQueueEntity.toContentValues(): ContentValues = ContentValues().apply {
        if (id != 0L) put("id", id)
        put("node_id", nodeId)
        put("created_at", createdAt)
        put("retry_count", retryCount)
        put("next_retry_at", nextRetryAt)
    }
}
