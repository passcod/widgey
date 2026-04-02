package com.widgey.data.db

import android.content.ContentValues
import android.database.Cursor
import com.widgey.data.entity.WidgetConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class WidgetConfigDao internal constructor(
    private val db: AppDatabase,
    private val changeNotifier: DatabaseChangeNotifier
) {

    // ---- suspend queries ----

    suspend fun getConfig(widgetId: Int): WidgetConfigEntity? = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "widget_config", null, "widget_id = ?",
            arrayOf(widgetId.toString()), null, null, null
        ).use { if (it.moveToFirst()) it.toEntity() else null }
    }

    suspend fun getAllConfigs(): List<WidgetConfigEntity> = withContext(Dispatchers.IO) {
        db.readableDatabase.query("widget_config", null, null, null, null, null, null)
            .use { it.toList() }
    }

    suspend fun getConfiguredWidgets(): List<WidgetConfigEntity> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "widget_config", null, "node_id IS NOT NULL", null, null, null, null
        ).use { it.toList() }
    }

    suspend fun getAllNodeIds(): List<String> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            true, "widget_config", arrayOf("node_id"),
            "node_id IS NOT NULL", null, null, null, null, null
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }

    // ---- suspend writes ----

    suspend fun insert(config: WidgetConfigEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("widget_id", config.widgetId)
            put("node_id", config.nodeId)
        }
        db.writableDatabase.insertWithOnConflict(
            "widget_config", null, values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        changeNotifier.invalidateWidgetConfig()
    }

    suspend fun updateNodeId(widgetId: Int, nodeId: String?) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("node_id", nodeId) }
        db.writableDatabase.update("widget_config", values, "widget_id = ?",
            arrayOf(widgetId.toString()))
        changeNotifier.invalidateWidgetConfig()
    }

    suspend fun delete(widgetId: Int) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("widget_config", "widget_id = ?",
            arrayOf(widgetId.toString()))
        changeNotifier.invalidateWidgetConfig()
    }

    suspend fun deleteAll(widgetIds: List<Int>) = withContext(Dispatchers.IO) {
        if (widgetIds.isEmpty()) return@withContext
        val placeholders = widgetIds.joinToString(",") { "?" }
        val args = widgetIds.map { it.toString() }.toTypedArray()
        db.writableDatabase.execSQL(
            "DELETE FROM widget_config WHERE widget_id IN ($placeholders)", args
        )
        changeNotifier.invalidateWidgetConfig()
    }

    // ---- observe flows ----

    fun observeConfig(widgetId: Int): Flow<WidgetConfigEntity?> =
        changeNotifier.widgetConfig
            .onStart { emit(Unit) }
            .map {
                db.readableDatabase.query(
                    "widget_config", null, "widget_id = ?",
                    arrayOf(widgetId.toString()), null, null, null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.toEntity() else null }
            }
            .flowOn(Dispatchers.IO)

    // ---- private helpers ----

    private fun Cursor.toEntity(): WidgetConfigEntity = WidgetConfigEntity(
        widgetId = getInt(getColumnIndexOrThrow("widget_id")),
        nodeId = getString(getColumnIndexOrThrow("node_id"))
    )

    private fun Cursor.toList(): List<WidgetConfigEntity> = buildList {
        while (moveToNext()) add(toEntity())
    }
}
