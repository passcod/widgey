package com.widgey.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.widgey.R
import com.widgey.WidgeyApp
import com.widgey.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgeyProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "WidgeyProvider"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: ${appWidgetIds.size} widgets")

        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "onDeleted: ${appWidgetIds.size} widgets")

        val app = context.applicationContext as WidgeyApp
        scope.launch {
            app.database.widgetConfigDao().deleteAll(appWidgetIds.toList())
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "onEnabled: First widget added")

        // Start periodic sync when first widget is added
        val syncManager = SyncManager(context)
        syncManager.startPeriodicSync()
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "onDisabled: Last widget removedx")

        // Stop periodic sync when last widget is removed
        val syncManager = SyncManager(context)
        syncManager.stopPeriodicSync()
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        scope.launch {
            val views = WidgetUpdater.buildRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        }
    }
}
