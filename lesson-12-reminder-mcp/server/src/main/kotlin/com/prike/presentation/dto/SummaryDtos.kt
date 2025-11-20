package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для ответа со статусом планировщика
 */
@Serializable
data class SchedulerStatusResponse(
    val success: Boolean,
    val status: SchedulerStatusDto? = null,
    val error: String? = null
)

@Serializable
data class SchedulerStatusDto(
    val isRunning: Boolean,
    val enabled: Boolean,
    val intervalMinutes: Int,
    val periodHours: Int,
    val activeSource: String
)

/**
 * DTO для ответа со списком summary
 */
@Serializable
data class SummariesResponse(
    val success: Boolean,
    val summaries: List<SummaryDto> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

@Serializable
data class SummaryDto(
    val id: String,
    val source: String,
    val periodStart: Long,
    val periodEnd: Long,
    val summaryText: String,
    val messageCount: Int,
    val generatedAt: Long,
    val deliveredToTelegram: Boolean,
    val llmModel: String? = null
)

