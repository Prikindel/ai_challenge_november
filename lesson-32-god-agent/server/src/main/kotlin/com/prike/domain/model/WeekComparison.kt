package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Сравнение текущей недели с предыдущей
 */
@Serializable
data class WeekComparison(
    val currentWeek: WeekStats,
    val previousWeek: WeekStats,
    val improvements: List<Improvement>,
    val degradations: List<Degradation>,
    val newIssues: List<String>,
    val resolvedIssues: List<String>
)
