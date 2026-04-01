package com.widgey.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "parent_id")
    val parentId: String? = null,

    @ColumnInfo(name = "priority")
    val priority: Int = 0,

    @ColumnInfo(name = "remote_modified_at")
    val remoteModifiedAt: Long = 0,

    @ColumnInfo(name = "local_modified_at")
    val localModifiedAt: Long? = null,

    @ColumnInfo(name = "is_dirty")
    val isDirty: Boolean = false,

    @ColumnInfo(name = "completed")
    val completed: Boolean = false
)
