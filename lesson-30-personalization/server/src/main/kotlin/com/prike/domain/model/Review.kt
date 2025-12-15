package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Модель отзыва из мобильного стора
 */
@Serializable
data class Review(
    val id: String,
    val text: String,
    val rating: Int, // 1-5
    val date: String // ISO8601 формат
)
