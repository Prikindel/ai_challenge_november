package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Критичность проблемы в отзыве
 */
@Serializable
enum class Criticality {
    HIGH,    // Высокая
    MEDIUM,  // Средняя
    LOW      // Низкая
}
