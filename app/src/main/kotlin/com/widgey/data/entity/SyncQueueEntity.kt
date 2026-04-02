package com.widgey.data.entity

data class SyncQueueEntity(
    val id: Long = 0,
    val nodeId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val nextRetryAt: Long = System.currentTimeMillis()
)
