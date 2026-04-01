package com.widgey

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.widgey.data.api.WorkflowyApi
import com.widgey.data.db.AppDatabase
import com.widgey.data.repository.NodeRepository
import com.widgey.data.repository.SettingsRepository

class WidgeyApp : Application(), Configuration.Provider {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var nodeRepository: NodeRepository
        private set

    lateinit var api: WorkflowyApi
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize database
        database = AppDatabase.getInstance(this)

        // Initialize repositories
        settingsRepository = SettingsRepository(database.settingsDao())

        // Initialize API client
        api = WorkflowyApi { settingsRepository.getApiKey() }

        // Initialize node repository
        nodeRepository = NodeRepository(
            nodeDao = database.nodeDao(),
            syncQueueDao = database.syncQueueDao(),
            api = api
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    companion object {
        @Volatile
        private var instance: WidgeyApp? = null

        fun getInstance(): WidgeyApp {
            return instance ?: throw IllegalStateException("WidgeyApp not initialized")
        }
    }
}
