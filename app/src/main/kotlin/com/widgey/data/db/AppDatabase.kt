package com.widgey.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.widgey.data.entity.NodeEntity
import com.widgey.data.entity.SettingEntity
import com.widgey.data.entity.SyncQueueEntity
import com.widgey.data.entity.WidgetConfigEntity

@Database(
    entities = [
        NodeEntity::class,
        WidgetConfigEntity::class,
        SyncQueueEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun nodeDao(): NodeDao
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val DATABASE_NAME = "widgey.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}
