package com.widgey.data.db

import android.content.ContentValues
import android.database.Cursor
import androidx.core.database.sqlite.transaction
import com.widgey.data.entity.NodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class NodeDao internal constructor(
    private val db: AppDatabase,
    private val changeNotifier: DatabaseChangeNotifier
) {

    // ---- suspend queries ----

    suspend fun getById(id: String): NodeEntity? = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "nodes", null, "id = ?", arrayOf(id), null, null, null
        ).use { if (it.moveToFirst()) it.toNodeEntity() else null }
    }

    suspend fun getTopLevelNodes(): List<NodeEntity> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "nodes", null, "parent_id IS NULL", null, null, null, "priority ASC"
        ).use { it.toList() }
    }

    suspend fun getTopLevelActiveNodes(): List<NodeEntity> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "nodes", null, "parent_id IS NULL AND completed_at IS NULL",
            null, null, null, "priority ASC"
        ).use { it.toList() }
    }

    suspend fun getChildNodes(parentId: String): List<NodeEntity> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "nodes", null, "parent_id = ?", arrayOf(parentId), null, null, "priority ASC"
        ).use { it.toList() }
    }

    suspend fun getDirtyNodes(): List<NodeEntity> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "nodes", null, "is_dirty = 1", null, null, null, null
        ).use { it.toList() }
    }

    suspend fun searchByName(query: String): List<NodeEntity> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "nodes", null, "name LIKE ?", arrayOf("%$query%"), null, null, "priority ASC"
        ).use { it.toList() }
    }

    suspend fun exists(id: String): Boolean = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "nodes", arrayOf("id"), "id = ?", arrayOf(id), null, null, null
        ).use { it.count > 0 }
    }

    // ---- suspend writes ----

    suspend fun insert(node: NodeEntity) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertWithOnConflict(
            "nodes", null, node.toContentValues(),
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        changeNotifier.invalidateNodes()
    }

    suspend fun insertAll(nodes: List<NodeEntity>) = withContext(Dispatchers.IO) {
        val wdb = db.writableDatabase
        wdb.transaction {
            nodes.forEach { node ->
                insertWithOnConflict(
                    "nodes", null, node.toContentValues(),
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
        changeNotifier.invalidateNodes()
    }

    suspend fun update(node: NodeEntity) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "nodes", node.toContentValues(), "id = ?", arrayOf(node.id)
        )
        changeNotifier.invalidateNodes()
    }

    suspend fun updateNoteLocally(id: String, note: String?, localModifiedAt: Long) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("note", note)
                put("local_modified_at", localModifiedAt)
                put("is_dirty", 1)
            }
            db.writableDatabase.update("nodes", values, "id = ?", arrayOf(id))
            changeNotifier.invalidateNodes()
        }

    suspend fun updateFromRemote(id: String, name: String, note: String?, remoteModifiedAt: Long) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("name", name)
                put("note", note)
                put("remote_modified_at", remoteModifiedAt)
            }
            db.writableDatabase.update("nodes", values, "id = ? AND is_dirty = 0", arrayOf(id))
            changeNotifier.invalidateNodes()
        }

    suspend fun updateCompletionStatus(id: String, completed: Boolean, completedAt: Long?) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("completed", if (completed) 1 else 0)
                if (completedAt != null) put("completed_at", completedAt) else putNull("completed_at")
            }
            db.writableDatabase.update("nodes", values, "id = ?", arrayOf(id))
            changeNotifier.invalidateNodes()
        }

    suspend fun markSynced(id: String, remoteModifiedAt: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("is_dirty", 0)
            put("remote_modified_at", remoteModifiedAt)
        }
        db.writableDatabase.update("nodes", values, "id = ?", arrayOf(id))
        changeNotifier.invalidateNodes()
    }

    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("nodes", "id = ?", arrayOf(id))
        changeNotifier.invalidateNodes()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("nodes", null, null)
        changeNotifier.invalidateNodes()
    }

    // ---- observe flows ----

    fun observeById(id: String): Flow<NodeEntity?> =
        changeNotifier.nodes
            .onStart { emit(Unit) }
            .map {
                db.readableDatabase.query(
                    "nodes", null, "id = ?", arrayOf(id), null, null, null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.toNodeEntity() else null }
            }
            .flowOn(Dispatchers.IO)

    fun observeTopLevelNodes(): Flow<List<NodeEntity>> =
        changeNotifier.nodes
            .onStart { emit(Unit) }
            .map {
                db.readableDatabase.query(
                    "nodes", null, "parent_id IS NULL", null, null, null, "priority ASC"
                ).use { it.toList() }
            }
            .flowOn(Dispatchers.IO)

    // ---- private helpers ----

    private fun Cursor.toNodeEntity(): NodeEntity {
        val localModColIdx = getColumnIndexOrThrow("local_modified_at")
        val completedAtColIdx = getColumnIndexOrThrow("completed_at")
        return NodeEntity(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            note = getString(getColumnIndexOrThrow("note")),
            parentId = getString(getColumnIndexOrThrow("parent_id")),
            priority = getInt(getColumnIndexOrThrow("priority")),
            remoteModifiedAt = getLong(getColumnIndexOrThrow("remote_modified_at")),
            localModifiedAt = if (isNull(localModColIdx)) null else getLong(localModColIdx),
            isDirty = getInt(getColumnIndexOrThrow("is_dirty")) != 0,
            completed = getInt(getColumnIndexOrThrow("completed")) != 0,
            completedAt = if (isNull(completedAtColIdx)) null else getLong(completedAtColIdx)
        )
    }

    private fun Cursor.toList(): List<NodeEntity> = buildList {
        while (moveToNext()) add(toNodeEntity())
    }

    private fun NodeEntity.toContentValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("note", note)
        put("parent_id", parentId)
        put("priority", priority)
        put("remote_modified_at", remoteModifiedAt)
        if (localModifiedAt != null) put("local_modified_at", localModifiedAt)
        else putNull("local_modified_at")
        put("is_dirty", if (isDirty) 1 else 0)
        put("completed", if (completed) 1 else 0)
        if (completedAt != null) put("completed_at", completedAt) else putNull("completed_at")
    }
}
