package com.prike.data.dto

import kotlinx.serialization.Serializable

/**
 * Запрос к локальной LLM (Ollama API)
 */
@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
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
    val num_predict: Int? = null
)

