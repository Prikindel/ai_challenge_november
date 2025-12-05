package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Категория отзыва
 */
@Serializable
enum class ReviewCategory {
    POSITIVE,  // Положительный
    NEGATIVE,  // Отрицательный
    NEUTRAL    // Нейтральный
}
