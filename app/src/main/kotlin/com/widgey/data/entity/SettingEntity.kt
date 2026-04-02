package com.widgey.data.entity

data class SettingEntity(
    val key: String,
    val value: String?
) {
    companion object {
        const val KEY_API_KEY = "api_key"
    }
}
