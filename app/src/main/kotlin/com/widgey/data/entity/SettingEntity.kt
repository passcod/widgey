package com.widgey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,
    val value: String?
) {
    companion object {
        const val KEY_API_KEY = "api_key"
    }
}
