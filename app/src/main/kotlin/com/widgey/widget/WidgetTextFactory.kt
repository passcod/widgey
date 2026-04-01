package com.widgey.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.Spanned
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.widgey.R
import com.widgey.data.db.AppDatabase
import com.widgey.util.HtmlFormatter
import kotlinx.coroutines.runBlocking

class WidgetTextFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    private var lines: List<Spanned> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val db = AppDatabase.getInstance(context)
        val config = runBlocking { db.widgetConfigDao().getConfig(appWidgetId) }
        val nodeId = config?.nodeId ?: return

        val node = runBlocking { db.nodeDao().getById(nodeId) }
        val note = node?.note ?: ""

        lines = if (note.isEmpty()) {
            emptyList()
        } else {
            note.split("\n").map { HtmlFormatter.toSpanned(it) }
        }
    }

    override fun onDestroy() {
        lines = emptyList()
    }

    override fun getCount(): Int = lines.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.item_widget_line)
        views.setTextViewText(R.id.line_text, lines.getOrElse(position) { HtmlFormatter.toSpanned("") })
        // Empty fill-in intent — the pending intent template on the ListView handles the tap
        views.setOnClickFillInIntent(R.id.line_text, Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
