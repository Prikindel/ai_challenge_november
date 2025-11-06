package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для истории переписки (JSON запросов и ответов)
 */
@Serializable
data class ChatHistoryDto(
    /**
     * Список всех JSON запросов и ответов
     */
    val entries: List<JsonHistoryEntryDto>
)

/**
 * DTO для одной записи в истории
 */
@Serializable
data class JsonHistoryEntryDto(
    /**
     * JSON запрос к LLM
     */
    val requestJson: String,
    /**
     * JSON ответ от LLM
     */
    val responseJson: String
)

