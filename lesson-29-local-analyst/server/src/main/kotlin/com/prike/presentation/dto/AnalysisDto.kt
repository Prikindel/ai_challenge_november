package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * Запрос на анализ данных
 */
@Serializable
data class AnalysisRequest(
    val question: String,
    val source: String? = null,  // "csv", "json", "logs"
    val limit: Int? = null
)

/**
 * Ответ на аналитический вопрос
 */
@Serializable
data class AnalysisResponse(
    val question: String,
    val answer: String,
    val source: String?,
    val recordsCount: Int
)
