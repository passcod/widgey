package com.widgey.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.widgey.R
import com.widgey.WidgeyApp
import com.widgey.ui.editor.EditorActivity
import com.widgey.ui.nodeselection.NodeSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUpdater {

    /**
     * Update all widgets
     */
    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, WidgeyProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (widgetIds.isNotEmpty()) {
            val intent = Intent(context, WidgeyProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)
        }
    }

    /**
     * Update a single widget
     */
    fun updateWidget(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        CoroutineScope(Dispatchers.Main).launch {
            val views = buildRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /**
     * Build RemoteViews for a widget
     */
    suspend fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val app = context.applicationContext as WidgeyApp
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Check if we have an API key
        val hasApiKey = app.settingsRepository.hasApiKey()
        if (!hasApiKey) {
            views.setTextViewText(R.id.widget_text, context.getString(R.string.widget_no_api_key))
            views.setOnClickPendingIntent(
                R.id.widget_container,
                createConfigPendingIntent(context, appWidgetId)
            )
            return views
        }

        // Get widget config
        val config = app.database.widgetConfigDao().getConfig(appWidgetId)
        val nodeId = config?.nodeId

        if (nodeId == null) {
            // Widget is unconfigured or discarded - show empty state
            views.setTextViewText(R.id.widget_text, "")
            views.setOnClickPendingIntent(
                R.id.widget_container,
                createNodeSelectionPendingIntent(context, appWidgetId)
            )
            return views
        }

        // Get node content
        val node = app.database.nodeDao().getById(nodeId)
        if (node != null) {
            views.setTextViewText(R.id.widget_text, node.note ?: "")
            views.setOnClickPendingIntent(
                R.id.widget_container,
                createEditorPendingIntent(context, appWidgetId, nodeId)
            )
        } else {
            // Node not in cache yet - show empty and tap to configure
            views.setTextViewText(R.id.widget_text, "")
            views.setOnClickPendingIntent(
                R.id.widget_container,
                createEditorPendingIntent(context, appWidgetId, nodeId)
            )
        }

        return views
    }

    private fun createConfigPendingIntent(context: Context, appWidgetId: Int): android.app.PendingIntent {
        val intent = Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return android.app.PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNodeSelectionPendingIntent(context: Context, appWidgetId: Int): android.app.PendingIntent {
        val intent = Intent(context, NodeSelectionActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return android.app.PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createEditorPendingIntent(context: Context, appWidgetId: Int, nodeId: String): android.app.PendingIntent {
        val intent = Intent(context, EditorActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(EditorActivity.EXTRA_NODE_ID, nodeId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return android.app.PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }
}
