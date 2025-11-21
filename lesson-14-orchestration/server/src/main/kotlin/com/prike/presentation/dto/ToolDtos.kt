package com.prike.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class ToolDto(
    val name: String,
    val description: String?,
    val server: String
)

@Serializable
data class ToolsByServerDto(
    val server: String,
    val serverName: String,
    val tools: List<ToolDto>
)

@Serializable
data class AllToolsResponse(
    val tools: List<ToolsByServerDto>,
    val total: Int
)

@Serializable
data class ConnectionStatusDto(
    val server: String,
    val connected: Boolean
)

@Serializable
data class ConnectionsResponse(
    val connections: List<ConnectionStatusDto>,
    val total: Int,
    val connected: Int
)

