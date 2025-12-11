package com.prike.data.dto

import kotlinx.serialization.Serializable

/**
 * Ответ от локальной LLM (Ollama API)
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

