package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для промежуточных сообщений через WebSocket
 */

/**
 * Промежуточное сообщение от LLM (например, "Анализирую данные...")
 */
@Serializable
data class StatusUpdate(
    val type: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor(message: String, timestamp: Long = System.currentTimeMillis()) : this("status", message, timestamp)
}

/**
 * Информация о вызове инструмента
 */
@Serializable
data class ToolCallUpdate(
    val type: String,
    val toolName: String,
    val status: String, // "starting", "success", "error"
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor(toolName: String, status: String, message: String? = null, timestamp: Long = System.currentTimeMillis()) 
        : this("tool_call", toolName, status, message, timestamp)
}

/**
 * Финальный ответ
 */
@Serializable
data class FinalResponse(
    val type: String,
    val message: String,
    val toolCalls: List<ToolCallInfoDto>,
    val processingTime: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor(message: String, toolCalls: List<ToolCallInfoDto>, processingTime: Long, timestamp: Long = System.currentTimeMillis())
        : this("final", message, toolCalls, processingTime, timestamp)
}

/**
 * Ошибка
 */
@Serializable
data class WebSocketError(
    val type: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor(message: String, timestamp: Long = System.currentTimeMillis()) : this("error", message, timestamp)
}

