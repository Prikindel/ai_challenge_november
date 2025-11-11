package com.prike.domain.entity

/**
 * Результат обращения к LLM, возвращается из репозитория.
 */
data class LLMCompletionResult(
    val content: String,
    val meta: LLMCompletionMeta
)

/**
 * Метаданные о запросе к LLM.
 */
data class LLMCompletionMeta(
    val durationMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val requestJson: String? = null,
    val responseJson: String? = null
)

