package com.widgey.data.repository

import com.widgey.data.api.WorkflowyApi
import com.widgey.data.db.NodeDao
import com.widgey.data.db.SyncQueueDao
import com.widgey.data.entity.NodeEntity
import com.widgey.data.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

class NodeRepository(
    private val nodeDao: NodeDao,
    private val syncQueueDao: SyncQueueDao,
    private val api: WorkflowyApi
) {
    /**
     * Get a node by ID from local cache
     */
    suspend fun getNode(id: String): NodeEntity? {
        return nodeDao.getById(id)
    }

    /**
     * Observe a node by ID
     */
    fun observeNode(id: String): Flow<NodeEntity?> {
        return nodeDao.observeById(id)
    }

    /**
     * Get top-level nodes from local cache
     */
    suspend fun getTopLevelNodes(): List<NodeEntity> {
        return nodeDao.getTopLevelNodes()
    }

    /**
     * Observe top-level nodes
     */
    fun observeTopLevelNodes(): Flow<List<NodeEntity>> {
        return nodeDao.observeTopLevelNodes()
    }

    /**
     * Update a node's note locally and queue for sync
     */
    suspend fun updateNoteLocally(nodeId: String, note: String?) {
        val now = System.currentTimeMillis()
        nodeDao.updateNoteLocally(nodeId, note, now)
        queueSync(nodeId)
    }

    /**
     * Queue a node for sync
     */
    private suspend fun queueSync(nodeId: String) {
        val existing = syncQueueDao.getByNodeId(nodeId)
        if (existing == null) {
            syncQueueDao.insert(
                SyncQueueEntity(
                    nodeId = nodeId,
                    createdAt = System.currentTimeMillis(),
                    retryCount = 0,
                    nextRetryAt = System.currentTimeMillis()
                )
            )
        } else {
            // Reset retry time to trigger immediate sync
            syncQueueDao.update(existing.copy(nextRetryAt = System.currentTimeMillis()))
        }
    }

    /**
     * Fetch a node from the API and update local cache
     * Returns true if the node exists, false if not found
     */
    suspend fun fetchNode(nodeId: String): FetchResult {
        return when (val result = api.getNode(nodeId)) {
            is WorkflowyApi.ApiResult.Success -> {
                val dto = result.data
                val existing = nodeDao.getById(nodeId)

                if (existing == null) {
                    // New node, just insert
                    nodeDao.insert(
                        NodeEntity(
                            id = dto.id,
                            name = dto.name,
                            note = dto.note,
                            parentId = dto.parentId,
                            priority = dto.priority,
                            remoteModifiedAt = dto.modifiedAt,
                            localModifiedAt = null,
                            isDirty = false
                        )
                    )
                } else if (existing.isDirty) {
                    // Local changes exist - keep them, but update remote timestamp
                    // Only update from remote if remote is newer AND local hasn't been modified more recently
                    val localModTime = existing.localModifiedAt ?: 0
                    if (dto.modifiedAt > localModTime) {
                        // Remote is newer than our local edit, but we still keep local
                        // because user intent should be preserved
                        // Just update the remote timestamp so we know what we're overwriting
                    }
                    // Don't update content, keep local changes
                } else {
                    // No local changes, safe to update from remote
                    if (dto.modifiedAt > existing.remoteModifiedAt) {
                        nodeDao.updateFromRemote(nodeId, dto.note, dto.modifiedAt)
                    }
                }
                FetchResult.Success
            }
            is WorkflowyApi.ApiResult.NotFound -> FetchResult.NotFound
            is WorkflowyApi.ApiResult.Unauthorized -> FetchResult.Unauthorized
            is WorkflowyApi.ApiResult.Error -> FetchResult.Error(result.message)
            is WorkflowyApi.ApiResult.NetworkError -> FetchResult.NetworkError(result.exception)
        }
    }

    /**
     * Fetch top-level nodes from the API and update local cache
     */
    suspend fun fetchTopLevelNodes(): FetchResult {
        return when (val result = api.getTopLevelNodes()) {
            is WorkflowyApi.ApiResult.Success -> {
                val nodes = result.data.map { dto ->
                    NodeEntity(
                        id = dto.id,
                        name = dto.name,
                        note = dto.note,
                        parentId = dto.parentId,
                        priority = dto.priority,
                        remoteModifiedAt = dto.modifiedAt,
                        localModifiedAt = null,
                        isDirty = false
                    )
                }
                // Only insert nodes that don't exist locally or aren't dirty
                for (node in nodes) {
                    val existing = nodeDao.getById(node.id)
                    if (existing == null) {
                        nodeDao.insert(node)
                    } else if (!existing.isDirty && node.remoteModifiedAt > existing.remoteModifiedAt) {
                        nodeDao.updateFromRemote(node.id, node.note, node.remoteModifiedAt)
                    }
                }
                FetchResult.Success
            }
            is WorkflowyApi.ApiResult.NotFound -> FetchResult.NotFound
            is WorkflowyApi.ApiResult.Unauthorized -> FetchResult.Unauthorized
            is WorkflowyApi.ApiResult.Error -> FetchResult.Error(result.message)
            is WorkflowyApi.ApiResult.NetworkError -> FetchResult.NetworkError(result.exception)
        }
    }

    /**
     * Create a new top-level node
     */
    suspend fun createTopLevelNode(name: String, note: String? = null): CreateResult {
        return when (val result = api.createNode(parentId = null, name = name, note = note)) {
            is WorkflowyApi.ApiResult.Success -> {
                val nodeId = result.data
                // Fetch the created node to get full details
                when (val fetchResult = api.getNode(nodeId)) {
                    is WorkflowyApi.ApiResult.Success -> {
                        val dto = fetchResult.data
                        nodeDao.insert(
                            NodeEntity(
                                id = dto.id,
                                name = dto.name,
                                note = dto.note,
                                parentId = dto.parentId,
                                priority = dto.priority,
                                remoteModifiedAt = dto.modifiedAt,
                                localModifiedAt = null,
                                isDirty = false
                            )
                        )
                        CreateResult.Success(nodeId)
                    }
                    else -> {
                        // Node was created but we couldn't fetch it
                        // Create a minimal local entry
                        nodeDao.insert(
                            NodeEntity(
                                id = nodeId,
                                name = name,
                                note = note,
                                parentId = null,
                                priority = 0,
                                remoteModifiedAt = System.currentTimeMillis(),
                                localModifiedAt = null,
                                isDirty = false
                            )
                        )
                        CreateResult.Success(nodeId)
                    }
                }
            }
            is WorkflowyApi.ApiResult.Unauthorized -> CreateResult.Unauthorized
            is WorkflowyApi.ApiResult.NotFound -> CreateResult.Error("Parent not found")
            is WorkflowyApi.ApiResult.Error -> CreateResult.Error(result.message)
            is WorkflowyApi.ApiResult.NetworkError -> CreateResult.NetworkError(result.exception)
        }
    }

    /**
     * Push local changes to the server
     */
    suspend fun pushNode(nodeId: String): PushResult {
        val node = nodeDao.getById(nodeId) ?: return PushResult.NotFound

        if (!node.isDirty) {
            return PushResult.Success
        }

        return when (val result = api.updateNode(nodeId, note = node.note)) {
            is WorkflowyApi.ApiResult.Success -> {
                // Fetch updated node to get new modifiedAt
                when (val fetchResult = api.getNode(nodeId)) {
                    is WorkflowyApi.ApiResult.Success -> {
                        nodeDao.markSynced(nodeId, fetchResult.data.modifiedAt)
                    }
                    else -> {
                        // Push succeeded but couldn't fetch - mark synced with current time
                        nodeDao.markSynced(nodeId, System.currentTimeMillis())
                    }
                }
                syncQueueDao.deleteByNodeId(nodeId)
                PushResult.Success
            }
            is WorkflowyApi.ApiResult.NotFound -> PushResult.NotFound
            is WorkflowyApi.ApiResult.Unauthorized -> PushResult.Unauthorized
            is WorkflowyApi.ApiResult.Error -> PushResult.Error(result.message)
            is WorkflowyApi.ApiResult.NetworkError -> PushResult.NetworkError(result.exception)
        }
    }

    /**
     * Check if a node exists on the server
     */
    suspend fun nodeExistsRemotely(nodeId: String): Boolean {
        return when (api.getNode(nodeId)) {
            is WorkflowyApi.ApiResult.Success -> true
            else -> false
        }
    }

    sealed class FetchResult {
        data object Success : FetchResult()
        data object NotFound : FetchResult()
        data object Unauthorized : FetchResult()
        data class Error(val message: String) : FetchResult()
        data class NetworkError(val exception: Exception) : FetchResult()
    }

    sealed class CreateResult {
        data class Success(val nodeId: String) : CreateResult()
        data object Unauthorized : CreateResult()
        data class Error(val message: String) : CreateResult()
        data class NetworkError(val exception: Exception) : CreateResult()
    }

    sealed class PushResult {
        data object Success : PushResult()
        data object NotFound : PushResult()
        data object Unauthorized : PushResult()
        data class Error(val message: String) : PushResult()
        data class NetworkError(val exception: Exception) : PushResult()
    }
}
