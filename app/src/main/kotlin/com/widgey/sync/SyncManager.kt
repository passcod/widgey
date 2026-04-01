package com.widgey.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.widgey.WidgeyApp
import com.widgey.data.repository.NodeRepository
import com.widgey.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class SyncManager(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"
        private const val PERIODIC_SYNC_WORK = "periodic_sync"
        private const val PUSH_SYNC_WORK = "push_sync"
        private const val SYNC_INTERVAL_MINUTES = 1L
        private const val MAX_BACKOFF_MS = 15 * 60 * 1000L // 15 minutes
        private const val INITIAL_BACKOFF_MS = 1000L // 1 second
    }

    private val app: WidgeyApp
        get() = context.applicationContext as WidgeyApp

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * Start periodic sync worker (every 60 seconds)
     */
    fun startPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )

        Log.d(TAG, "Started periodic sync")
    }

    /**
     * Stop periodic sync worker
     */
    fun stopPeriodicSync() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK)
        Log.d(TAG, "Stopped periodic sync")
    }

    /**
     * Trigger an immediate push sync
     */
    fun triggerPushSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val pushWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            PUSH_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            pushWorkRequest
        )

        Log.d(TAG, "Triggered push sync")
    }

    /**
     * Trigger immediate sync for all queued items (e.g., on network restored)
     */
    suspend fun resetAndTriggerSync() {
        withContext(Dispatchers.IO) {
            // Reset all retry times to now
            app.database.syncQueueDao().resetAllRetryTimes()
            triggerPushSync()
        }
    }

    /**
     * Calculate next retry time with exponential backoff
     */
    fun calculateNextRetryTime(retryCount: Int): Long {
        val backoffMs = min(
            INITIAL_BACKOFF_MS * 2.0.pow(retryCount.toDouble()).toLong(),
            MAX_BACKOFF_MS
        )
        return System.currentTimeMillis() + backoffMs
    }

    /**
     * Pull updates for all configured widgets' nodes
     */
    suspend fun pullUpdates(): PullResult = withContext(Dispatchers.IO) {
        val nodeIds = app.database.widgetConfigDao().getAllNodeIds()
        if (nodeIds.isEmpty()) {
            return@withContext PullResult.Success(0)
        }

        var successCount = 0
        var errorCount = 0

        for (nodeId in nodeIds) {
            when (app.nodeRepository.fetchNode(nodeId)) {
                is NodeRepository.FetchResult.Success -> successCount++
                is NodeRepository.FetchResult.NotFound -> {
                    // Node was deleted on server - we'll handle this in the editor
                    Log.w(TAG, "Node $nodeId not found on server")
                }
                else -> errorCount++
            }
        }

        if (successCount > 0) {
            WidgetUpdater.updateAllWidgets(context)
        }

        if (errorCount > 0) {
            PullResult.PartialSuccess(successCount, errorCount)
        } else {
            PullResult.Success(successCount)
        }
    }

    /**
     * Push all pending local changes
     */
    suspend fun pushPendingChanges(): PushResult = withContext(Dispatchers.IO) {
        val syncQueue = app.database.syncQueueDao()
        val pendingItems = syncQueue.getPendingItems()

        if (pendingItems.isEmpty()) {
            return@withContext PushResult.Success(0)
        }

        var successCount = 0
        var retryCount = 0

        for (item in pendingItems) {
            when (val result = app.nodeRepository.pushNode(item.nodeId)) {
                is NodeRepository.PushResult.Success -> {
                    syncQueue.deleteByNodeId(item.nodeId)
                    successCount++
                    Log.d(TAG, "Pushed node ${item.nodeId}")
                }
                is NodeRepository.PushResult.NotFound -> {
                    // Node no longer exists, remove from queue
                    syncQueue.deleteByNodeId(item.nodeId)
                    Log.w(TAG, "Node ${item.nodeId} not found, removed from sync queue")
                }
                is NodeRepository.PushResult.Unauthorized -> {
                    // API key issue - stop trying
                    Log.e(TAG, "Unauthorized - stopping push")
                    return@withContext PushResult.Unauthorized
                }
                is NodeRepository.PushResult.Error,
                is NodeRepository.PushResult.NetworkError -> {
                    // Schedule retry with backoff
                    val nextRetry = calculateNextRetryTime(item.retryCount)
                    syncQueue.incrementRetryCount(item.id, nextRetry)
                    retryCount++
                    Log.w(TAG, "Failed to push node ${item.nodeId}, retry scheduled")
                }
            }
        }

        if (retryCount > 0) {
            PushResult.PartialSuccess(successCount, retryCount)
        } else {
            PushResult.Success(successCount)
        }
    }

    sealed class PullResult {
        data class Success(val count: Int) : PullResult()
        data class PartialSuccess(val successCount: Int, val errorCount: Int) : PullResult()
        data object Unauthorized : PullResult()
        data class Error(val message: String) : PullResult()
    }

    sealed class PushResult {
        data class Success(val count: Int) : PushResult()
        data class PartialSuccess(val successCount: Int, val retryCount: Int) : PushResult()
        data object Unauthorized : PushResult()
        data class Error(val message: String) : PushResult()
    }
}
