package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для статуса от LLM (описание действия)
 */
@Serializable
data class StatusUpdate(
    val type: String = "status",
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DTO для статуса вызова инструмента
 */
@Serializable
data class ToolCallUpdate(
    val type: String = "tool_call",
    val toolName: String,
    val status: String, // "starting", "success", "error"
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DTO для финального ответа
 */
@Serializable
data class FinalResponse(
    val type: String = "final",
    val message: String,
    val toolCalls: List<ToolCallInfoDto> = emptyList(),
    val processingTime: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
