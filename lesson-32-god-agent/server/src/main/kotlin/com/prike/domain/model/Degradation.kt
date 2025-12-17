package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Ухудшение по сравнению с предыдущей неделей
 */
@Serializable
data class Degradation(
    val metric: String,
    val change: String,
    val reason: String
)
