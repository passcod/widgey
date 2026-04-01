package com.widgey.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.widgey.data.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun getValueFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT EXISTS(SELECT 1 FROM settings WHERE `key` = :key AND value IS NOT NULL)")
    suspend fun exists(key: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM settings WHERE `key` = :key AND value IS NOT NULL)")
    fun existsFlow(key: String): Flow<Boolean>
}
