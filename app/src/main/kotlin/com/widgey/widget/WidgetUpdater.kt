package com.widgey.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
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
            // Notify each ListView that its data may have changed
            for (id in widgetIds) {
                appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            }
        }
    }

    /**
     * Update a single widget
     */
    fun updateWidget(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            val views = buildRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        }
    }

    /**
     * Build RemoteViews for a widget
     */
    suspend fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val app = context.applicationContext as WidgeyApp
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // No API key
        val hasApiKey = app.settingsRepository.hasApiKey()
        if (!hasApiKey) {
            views.setViewVisibility(R.id.widget_list, View.GONE)
            views.setViewVisibility(R.id.widget_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_text, context.getString(R.string.widget_no_api_key))
            views.setOnClickPendingIntent(R.id.widget_container, nodeSelectionIntent(context, appWidgetId))
            return views
        }

        // No node configured
        val config = app.database.widgetConfigDao().getConfig(appWidgetId)
        val nodeId = config?.nodeId
        if (nodeId == null) {
            views.setViewVisibility(R.id.widget_list, View.GONE)
            views.setViewVisibility(R.id.widget_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_text, "")
            views.setOnClickPendingIntent(R.id.widget_container, nodeSelectionIntent(context, appWidgetId))
            return views
        }

        // Configured — use ListView via RemoteViewsService
        val editorPendingIntent = editorIntent(context, appWidgetId, nodeId)

        val serviceIntent = Intent(context, WidgetTextService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Unique URI per widget so each gets its own factory instance
            data = Uri.fromParts("widgey", appWidgetId.toString(), null)
        }

        views.setViewVisibility(R.id.widget_text, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.VISIBLE)
        views.setRemoteAdapter(R.id.widget_list, serviceIntent)
        // Tapping any list item opens the editor
        views.setPendingIntentTemplate(R.id.widget_list, editorPendingIntent)
        // Tapping empty space in the widget (padding, etc.) also opens editor
        views.setOnClickPendingIntent(R.id.widget_container, editorPendingIntent)

        return views
    }

    private fun nodeSelectionIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, NodeSelectionActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun editorIntent(context: Context, appWidgetId: Int, nodeId: String): PendingIntent {
        val intent = Intent(context, EditorActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(EditorActivity.EXTRA_NODE_ID, nodeId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
