package com.prike.data.dto

import kotlinx.serialization.Serializable

/**
 * Ответ от локальной LLM (Ollama API /api/generate)
 */
@Serializable
data class OllamaGenerateResponse(
    val model: String,
    val created_at: String? = null,
    val response: String,
    val done: Boolean,
    val done_reason: String? = null,
    val context: List<Long>? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)

/**
 * Сообщение в ответе от Ollama Chat API
 */
@Serializable
data class OllamaMessageResponse(
    val role: String,
    val content: String
)

/**
 * Ответ от локальной LLM (Ollama API /api/chat)
 */
@Serializable
data class OllamaChatResponse(
    val model: String? = null,
    val created_at: String? = null,
    val message: OllamaMessageResponse? = null,
    val done: Boolean? = null,
    val done_reason: String? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)
