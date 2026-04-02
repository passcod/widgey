package com.widgey.data.entity

data class NodeEntity(
    val id: String,
    val name: String,
    val note: String? = null,
    val parentId: String? = null,
    val priority: Int = 0,
    val remoteModifiedAt: Long = 0,
    val localModifiedAt: Long? = null,
    val isDirty: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Long? = null
)
