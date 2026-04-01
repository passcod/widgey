package com.widgey.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NodeDto(
    val id: String,
    val name: String,
    val note: String? = null,
    @SerialName("parent_id")
    val parentId: String? = null,
    val priority: Int = 0,
    val data: NodeDataDto? = null,
    val createdAt: Long = 0,
    val modifiedAt: Long? = null,
    val completedAt: Long? = null
)

@Serializable
data class NodeDataDto(
    val layoutMode: String? = null
)

@Serializable
data class SingleNodeResponse(
    val node: NodeDto
)

@Serializable
data class NodesListResponse(
    val nodes: List<NodeDto>
)

@Serializable
data class CreateNodeResponse(
    @SerialName("item_id")
    val itemId: String
)

@Serializable
data class StatusResponse(
    val status: String
)

@Serializable
data class TargetDto(
    val key: String,
    val type: String,
    val name: String? = null
)

@Serializable
data class TargetsResponse(
    val targets: List<TargetDto>
)

@Serializable
data class CreateNodeRequest(
    @SerialName("parent_id")
    val parentId: String?,
    val name: String,
    val note: String? = null,
    val position: String = "top"
)

@Serializable
data class UpdateNodeRequest(
    val name: String? = null,
    val note: String? = null
)
