package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Анализ отзыва, выполненный через LLM
 */
@Serializable
data class ReviewAnalysis(
    val reviewId: String,
    val category: ReviewCategory,
    val topics: List<String>,
    val criticality: Criticality
)
