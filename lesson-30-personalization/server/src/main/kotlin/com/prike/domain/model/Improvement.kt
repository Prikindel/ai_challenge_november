package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Улучшение по сравнению с предыдущей неделей
 */
@Serializable
data class Improvement(
    val metric: String,
    val change: String,
    val reason: String
)
