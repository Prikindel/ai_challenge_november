package com.prike.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class ServerInfoDto(
    val name: String,
    val type: String,
    val enabled: Boolean,
    val description: String?
)

@Serializable
data class ServersListDto(
    val servers: List<ServerInfoDto>
)

@Serializable
data class ConnectRequestDto(
    val serverName: String
)

@Serializable
data class ToolDto(
    val name: String,
    val description: String?,
    val inputSchema: String? = null // JSON строка вместо Map для упрощения сериализации
)

@Serializable
data class ResourceDto(
    val uri: String,
    val name: String,
    val description: String?,
    val mimeType: String? = null
)

@Serializable
data class ConnectResponseDto(
    val success: Boolean,
    val serverName: String,
    val serverDescription: String?,
    val tools: List<ToolDto>,
    val resources: List<ResourceDto>
)

@Serializable
data class ErrorDto(
    val message: String
)

