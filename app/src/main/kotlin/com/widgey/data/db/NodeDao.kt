package com.widgey.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.widgey.data.entity.NodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun getById(id: String): NodeEntity?

    @Query("SELECT * FROM nodes WHERE id = :id")
    fun observeById(id: String): Flow<NodeEntity?>

    @Query("SELECT * FROM nodes WHERE parent_id IS NULL ORDER BY priority ASC")
    suspend fun getTopLevelNodes(): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE parent_id IS NULL AND completed_at IS NULL ORDER BY priority ASC")
    suspend fun getTopLevelActiveNodes(): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE parent_id IS NULL ORDER BY priority ASC")
    fun observeTopLevelNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE parent_id = :parentId ORDER BY priority ASC")
    suspend fun getChildNodes(parentId: String): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE is_dirty = 1")
    suspend fun getDirtyNodes(): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE name LIKE '%' || :query || '%' ORDER BY priority ASC")
    suspend fun searchByName(query: String): List<NodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<NodeEntity>)

    @Update
    suspend fun update(node: NodeEntity)

    @Query("UPDATE nodes SET note = :note, local_modified_at = :localModifiedAt, is_dirty = 1 WHERE id = :id")
    suspend fun updateNoteLocally(id: String, note: String?, localModifiedAt: Long)

    @Query("UPDATE nodes SET note = :note, remote_modified_at = :remoteModifiedAt WHERE id = :id AND is_dirty = 0")
    suspend fun updateFromRemote(id: String, note: String?, remoteModifiedAt: Long)

    @Query("UPDATE nodes SET completed = :completed, completed_at = :completedAt WHERE id = :id")
    suspend fun updateCompletionStatus(id: String, completed: Boolean, completedAt: Long?)

    @Query("UPDATE nodes SET is_dirty = 0, remote_modified_at = :remoteModifiedAt WHERE id = :id")
    suspend fun markSynced(id: String, remoteModifiedAt: Long)

    @Query("DELETE FROM nodes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM nodes")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM nodes WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
