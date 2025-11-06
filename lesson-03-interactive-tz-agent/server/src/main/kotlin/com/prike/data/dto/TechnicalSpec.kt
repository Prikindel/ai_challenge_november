package com.prike.data.dto

import kotlinx.serialization.Serializable

/**
 * DTO для технического задания (ТЗ)
 * Структурированный результат, который собирает агент
 */
@Serializable
data class TechnicalSpec(
    val title: String,
    val description: String,
    val requirements: List<String>,
    val features: List<String>,
    val constraints: List<String> = emptyList(),
    val timeline: String? = null,
    val targetAudience: String? = null,
    val successCriteria: List<String> = emptyList()
)

