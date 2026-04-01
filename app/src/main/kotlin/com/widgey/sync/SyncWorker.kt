package com.widgey.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.widgey.WidgeyApp
import com.widgey.widget.WidgetUpdater

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting push sync")

        val app = applicationContext as WidgeyApp
        val syncManager = SyncManager(applicationContext)

        // Check if we have an API key
        if (!app.settingsRepository.hasApiKey()) {
            Log.w(TAG, "No API key configured, skipping sync")
            return Result.success()
        }

        return when (val result = syncManager.pushPendingChanges()) {
            is SyncManager.PushResult.Success -> {
                Log.d(TAG, "Push sync completed: ${result.count} items synced")
                // Refresh widgets to show updated sync status
                WidgetUpdater.updateAllWidgets(applicationContext)
                Result.success()
            }
            is SyncManager.PushResult.PartialSuccess -> {
                Log.w(TAG, "Push sync partial: ${result.successCount} synced, ${result.retryCount} pending retry")
                WidgetUpdater.updateAllWidgets(applicationContext)
                Result.success() // We'll retry via the queue mechanism
            }
            is SyncManager.PushResult.Unauthorized -> {
                Log.e(TAG, "Push sync failed: unauthorized")
                WidgetUpdater.updateAllWidgets(applicationContext)
                Result.failure() // Don't retry without valid API key
            }
            is SyncManager.PushResult.Error -> {
                Log.e(TAG, "Push sync failed: ${result.message}")
                Result.retry()
            }
        }
    }
}
