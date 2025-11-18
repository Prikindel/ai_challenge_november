package com.prike.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTO для инструментов (tools) в OpenAI API
 */
@Serializable
data class ToolDto(
    val type: String = "function",
    val function: FunctionDto
)

/**
 * DTO для функции инструмента
 */
@Serializable
data class FunctionDto(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject? = null
)

/**
 * DTO для вызова инструмента от LLM
 */
@Serializable
data class ToolCallDto(
    val id: String,
    val type: String = "function",
    val function: FunctionCallDto
)

/**
 * DTO для функции в вызове инструмента
 */
@Serializable
data class FunctionCallDto(
    val name: String,
    val arguments: String // JSON строка с аргументами
)

