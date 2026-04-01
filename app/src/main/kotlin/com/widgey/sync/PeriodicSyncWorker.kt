package com.widgey.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.widgey.WidgeyApp
import com.widgey.widget.WidgetUpdater

class PeriodicSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PeriodicSyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic sync")

        val app = applicationContext as WidgeyApp
        val syncManager = SyncManager(applicationContext)

        // Check if we have an API key
        if (!app.settingsRepository.hasApiKey()) {
            Log.d(TAG, "No API key, skipping sync")
            return Result.success()
        }

        // Pull updates for all configured widgets
        when (val pullResult = syncManager.pullUpdates()) {
            is SyncManager.PullResult.Success -> {
                Log.d(TAG, "Pull sync completed: ${pullResult.count} nodes updated")
            }
            is SyncManager.PullResult.PartialSuccess -> {
                Log.w(TAG, "Pull sync partial: ${pullResult.successCount} success, ${pullResult.errorCount} errors")
            }
            is SyncManager.PullResult.Unauthorized -> {
                Log.e(TAG, "Pull sync unauthorized")
                // Don't retry, API key issue
                return Result.success()
            }
            is SyncManager.PullResult.Error -> {
                Log.e(TAG, "Pull sync error: ${pullResult.message}")
                return Result.retry()
            }
        }

        // Also push any pending changes
        when (val pushResult = syncManager.pushPendingChanges()) {
            is SyncManager.PushResult.Success -> {
                Log.d(TAG, "Push sync completed: ${pushResult.count} nodes pushed")
            }
            is SyncManager.PushResult.PartialSuccess -> {
                Log.w(TAG, "Push sync partial: ${pushResult.successCount} success, ${pushResult.retryCount} pending retry")
            }
            is SyncManager.PushResult.Unauthorized -> {
                Log.e(TAG, "Push sync unauthorized")
            }
            is SyncManager.PushResult.Error -> {
                Log.e(TAG, "Push sync error: ${pushResult.message}")
            }
        }

        // Update all widgets
        WidgetUpdater.updateAllWidgets(applicationContext)

        Log.d(TAG, "Periodic sync completed")
        return Result.success()
    }
}
