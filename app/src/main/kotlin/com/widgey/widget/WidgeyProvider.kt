package com.widgey.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.widgey.R
import com.widgey.WidgeyApp
import com.widgey.sync.SyncManager
import com.widgey.ui.editor.EditorActivity
import com.widgey.ui.nodeselection.NodeSelectionActivity
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
        Log.d(TAG, "onDisabled: Last widget removed")

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
            val app = context.applicationContext as WidgeyApp
            val config = app.database.widgetConfigDao().getConfig(appWidgetId)
            val hasApiKey = app.settingsRepository.hasApiKey()

            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            when {
                // No API key configured
                !hasApiKey -> {
                    views.setTextViewText(R.id.widget_text, context.getString(R.string.widget_no_api_key))
                    views.setOnClickPendingIntent(
                        R.id.widget_container,
                        createConfigIntent(context, appWidgetId)
                    )
                }

                // No node configured (empty/discarded widget)
                config == null || config.nodeId == null -> {
                    views.setTextViewText(R.id.widget_text, "")
                    views.setOnClickPendingIntent(
                        R.id.widget_container,
                        createNodeSelectionIntent(context, appWidgetId)
                    )
                }

                // Configured widget - show node content
                else -> {
                    val node = app.database.nodeDao().getById(config.nodeId)
                    val content = node?.note ?: ""
                    views.setTextViewText(R.id.widget_text, content)
                    views.setOnClickPendingIntent(
                        R.id.widget_container,
                        createEditorIntent(context, appWidgetId, config.nodeId)
                    )
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createConfigIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, NodeSelectionActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNodeSelectionIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, NodeSelectionActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createEditorIntent(context: Context, appWidgetId: Int, nodeId: String): PendingIntent {
        val intent = Intent(context, EditorActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(EditorActivity.EXTRA_NODE_ID, nodeId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
