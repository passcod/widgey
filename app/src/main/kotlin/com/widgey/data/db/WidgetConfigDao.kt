package com.widgey.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.widgey.data.entity.WidgetConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetConfigDao {

    @Query("SELECT * FROM widget_config WHERE widget_id = :widgetId")
    suspend fun getConfig(widgetId: Int): WidgetConfigEntity?

    @Query("SELECT * FROM widget_config WHERE widget_id = :widgetId")
    fun observeConfig(widgetId: Int): Flow<WidgetConfigEntity?>

    @Query("SELECT * FROM widget_config")
    suspend fun getAllConfigs(): List<WidgetConfigEntity>

    @Query("SELECT * FROM widget_config WHERE node_id IS NOT NULL")
    suspend fun getConfiguredWidgets(): List<WidgetConfigEntity>

    @Query("SELECT DISTINCT node_id FROM widget_config WHERE node_id IS NOT NULL")
    suspend fun getAllNodeIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: WidgetConfigEntity)

    @Query("UPDATE widget_config SET node_id = :nodeId WHERE widget_id = :widgetId")
    suspend fun updateNodeId(widgetId: Int, nodeId: String?)

    @Query("DELETE FROM widget_config WHERE widget_id = :widgetId")
    suspend fun delete(widgetId: Int)

    @Query("DELETE FROM widget_config WHERE widget_id IN (:widgetIds)")
    suspend fun deleteAll(widgetIds: List<Int>)
}
