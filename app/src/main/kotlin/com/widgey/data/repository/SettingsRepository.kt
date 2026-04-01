package com.widgey.data.repository

import com.widgey.data.db.SettingsDao
import com.widgey.data.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val settingsDao: SettingsDao
) {
    companion object {
        const val KEY_API_KEY = "api_key"
    }

    suspend fun getApiKey(): String? {
        return settingsDao.getValue(KEY_API_KEY)
    }

    fun observeApiKey(): Flow<String?> {
        return settingsDao.getValueFlow(KEY_API_KEY)
    }

    suspend fun setApiKey(apiKey: String) {
        settingsDao.set(SettingEntity(KEY_API_KEY, apiKey))
    }

    suspend fun clearApiKey() {
        settingsDao.delete(KEY_API_KEY)
    }

    suspend fun hasApiKey(): Boolean {
        return settingsDao.exists(KEY_API_KEY)
    }

    fun observeHasApiKey(): Flow<Boolean> {
        return settingsDao.existsFlow(KEY_API_KEY)
    }
}
