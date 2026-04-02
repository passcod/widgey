package com.widgey.data.db

import android.content.ContentValues
import com.widgey.data.entity.SettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class SettingsDao internal constructor(
    private val db: AppDatabase,
    private val changeNotifier: DatabaseChangeNotifier
) {

    // ---- suspend queries ----

    suspend fun getValue(key: String): String? = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "settings", arrayOf("value"), "`key` = ?", arrayOf(key), null, null, null
        ).use { if (it.moveToFirst()) it.getString(0) else null }
    }

    suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "settings", arrayOf("key"), "`key` = ? AND value IS NOT NULL",
            arrayOf(key), null, null, null
        ).use { it.count > 0 }
    }

    // ---- suspend writes ----

    suspend fun set(setting: SettingEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("key", setting.key)
            put("value", setting.value)
        }
        db.writableDatabase.insertWithOnConflict(
            "settings", null, values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        changeNotifier.invalidateSettings()
    }

    suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("settings", "`key` = ?", arrayOf(key))
        changeNotifier.invalidateSettings()
    }

    // ---- observe flows ----

    fun getValueFlow(key: String): Flow<String?> =
        changeNotifier.settings
            .onStart { emit(Unit) }
            .map {
                db.readableDatabase.query(
                    "settings", arrayOf("value"), "`key` = ?", arrayOf(key), null, null, null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }
            .flowOn(Dispatchers.IO)

    fun existsFlow(key: String): Flow<Boolean> =
        changeNotifier.settings
            .onStart { emit(Unit) }
            .map {
                db.readableDatabase.query(
                    "settings", arrayOf("key"), "`key` = ? AND value IS NOT NULL",
                    arrayOf(key), null, null, null
                ).use { cursor -> cursor.count > 0 }
            }
            .flowOn(Dispatchers.IO)
}
