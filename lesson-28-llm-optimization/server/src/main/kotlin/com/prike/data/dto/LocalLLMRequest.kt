package com.prike.data.dto

import kotlinx.serialization.Serializable

/**
 * Запрос к локальной LLM (Ollama API) - устаревший формат /api/generate
 */
@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

/**
 * Сообщение для Ollama Chat API
 */
@Serializable
data class OllamaMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

/**
 * Запрос к локальной LLM через Ollama Chat API (/api/chat)
 */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

/**
 * Опции для генерации (Ollama)
 */
@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    val top_p: Double? = null,
    val top_k: Int? = null,
    val num_predict: Int? = null,
    val repeat_penalty: Double? = null,
    val num_ctx: Int? = null,
    val seed: Int? = null
)

