package com.prike.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConnectMCPRequestDto(
    val serverJarPath: String? = null // Если null или "class" - запуск через Gradle (development), иначе через JAR
)

@Serializable
data class ConnectMCPResponseDto(
    val success: Boolean,
    val message: String
)

@Serializable
data class ToolDto(
    val name: String,
    val description: String?
)

@Serializable
data class ToolsListDto(
    val tools: List<ToolDto>
)

@Serializable
data class CallToolRequestDto(
    val toolName: String,
    val arguments: Map<String, String>
)

@Serializable
data class CallToolResponseDto(
    val success: Boolean,
    val result: String? = null,
    val error: String? = null
)

@Serializable
data class ErrorDto(
    val message: String
)

