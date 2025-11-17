package com.prike.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class Tool(
    val name: String,
    val description: String?,
    val inputSchema: String? = null // JSON строка вместо Map для упрощения сериализации
)

@Serializable
data class Resource(
    val uri: String,
    val name: String,
    val description: String?,
    val mimeType: String? = null
)

@Serializable
data class MCPServerInfo(
    val name: String,
    val type: String,
    val description: String?,
    val tools: List<Tool> = emptyList(),
    val resources: List<Resource> = emptyList()
)

