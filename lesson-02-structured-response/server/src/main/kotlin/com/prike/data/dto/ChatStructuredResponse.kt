package com.prike.data.dto

import kotlinx.serialization.Serializable

/**
 * Структурированный JSON ответ от AI
 * Пример формата ответа, который должен возвращать LLM
 */
@Serializable
data class ChatStructuredResponse(
    val message: String,
    val tone: String, // "friendly", "professional", "casual", etc.
    val tags: List<String> = emptyList(),
    val sentiment: String? = null // "positive", "neutral", "negative"
)

