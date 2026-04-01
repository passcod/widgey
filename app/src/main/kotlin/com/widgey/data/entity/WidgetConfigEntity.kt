package com.widgey.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widget_config")
data class WidgetConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "widget_id")
    val widgetId: Int,

    @ColumnInfo(name = "node_id")
    val nodeId: String? = null
)
