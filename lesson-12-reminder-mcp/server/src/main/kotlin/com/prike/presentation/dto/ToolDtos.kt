package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для инструмента
 */
@Serializable
data class ToolDto(
    val name: String,
    val description: String?,
    val source: String  // ID источника данных
)

/**
 * DTO для списка инструментов по источникам
 */
@Serializable
data class ToolsBySourceDto(
    val source: String,
    val sourceName: String,
    val tools: List<ToolDto>
)

/**
 * DTO для ответа со списком всех инструментов
 */
@Serializable
data class AllToolsResponse(
    val tools: List<ToolsBySourceDto>,
    val total: Int
)

/**
 * DTO для статуса подключений
 */
@Serializable
data class ConnectionStatusDto(
    val sources: Map<String, Boolean>,
    val connectedCount: Int,
    val totalCount: Int
)

