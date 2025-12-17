package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Саммари отзыва (хранится в БД вместо полного отзыва)
 */
@Serializable
data class ReviewSummary(
    val reviewId: String,
    val rating: Int, // 1-5
    val date: String, // ISO8601 формат
    val summary: String, // Краткое саммари отзыва
    val category: ReviewCategory,
    val topics: List<ReviewTopic>, // Категории из списка (Автозагрузка, Альбомы, и т.д.)
    val criticality: Criticality,
    val weekStart: String? = null // Начало недели для группировки
)

