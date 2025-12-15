package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Статистика по отзывам за неделю
 */
@Serializable
data class WeekStats(
    val weekStart: String, // ISO8601 формат (начало недели)
    val totalReviews: Int,
    val positiveCount: Int,
    val negativeCount: Int,
    val neutralCount: Int,
    val averageRating: Double,
    val analyses: List<ReviewAnalysis>
)
