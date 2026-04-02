package com.widgey.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase private constructor(
    context: Context,
    name: String?
) : SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {

    private val changeNotifier = DatabaseChangeNotifier()

    private val _nodeDao = NodeDao(this, changeNotifier)
    private val _widgetConfigDao = WidgetConfigDao(this, changeNotifier)
    private val _syncQueueDao = SyncQueueDao(this, changeNotifier)
    private val _settingsDao = SettingsDao(this, changeNotifier)

    fun nodeDao(): NodeDao = _nodeDao
    fun widgetConfigDao(): WidgetConfigDao = _widgetConfigDao
    fun syncQueueDao(): SyncQueueDao = _syncQueueDao
    fun settingsDao(): SettingsDao = _settingsDao

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE nodes (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                note TEXT,
                parent_id TEXT,
                priority INTEGER NOT NULL DEFAULT 0,
                remote_modified_at INTEGER NOT NULL DEFAULT 0,
                local_modified_at INTEGER,
                is_dirty INTEGER NOT NULL DEFAULT 0,
                completed INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER
            )"""
        )
        db.execSQL(
            """CREATE TABLE widget_config (
                widget_id INTEGER PRIMARY KEY,
                node_id TEXT
            )"""
        )
        db.execSQL(
            """CREATE TABLE sync_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                node_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                retry_count INTEGER NOT NULL DEFAULT 0,
                next_retry_at INTEGER NOT NULL,
                FOREIGN KEY (node_id) REFERENCES nodes(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX idx_sync_queue_node_id ON sync_queue(node_id)")
        db.execSQL("CREATE INDEX idx_sync_queue_next_retry_at ON sync_queue(next_retry_at)")
        db.execSQL(
            """CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE nodes ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE nodes ADD COLUMN completed_at INTEGER DEFAULT NULL")
        }
    }

    companion object {
        private const val DATABASE_NAME = "widgey.db"
        private const val DATABASE_VERSION = 3

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context.applicationContext, DATABASE_NAME)
                    .also { instance = it }
            }

        /** Returns an in-memory database for use in tests. */
        fun inMemory(context: Context): AppDatabase = AppDatabase(context, null)
    }
}
