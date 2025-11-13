package com.prike.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class SendMessageRequestDto(
    val message: String,
    val summaryInterval: Int? = null,
    val maxSummariesInContext: Int? = null
)

@Serializable
data class SendMessageResponseDto(
    val answer: String,
    val contextUsed: ContextUsageDto,
    val tokenUsage: TokenUsageDto,
    val summaries: List<SummaryDto>,
    val rawMessagesCount: Int,
    val summaryInterval: Int
)

@Serializable
data class ContextUsageDto(
    val summaryIds: List<String>,
    val rawMessages: List<ContextRawMessageDto>
)

@Serializable
data class ContextRawMessageDto(
    val id: String,
    val role: String,
    val preview: String,
    val createdAt: String
)

@Serializable
data class TokenUsageDto(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val hypotheticalPromptTokens: Int?,
    val tokensSavedByCompression: Int?
)

@Serializable
data class SummaryDto(
    val id: String,
    val createdAt: String,
    val summary: String,
    val facts: List<String>,
    val openQuestions: List<String>
)

@Serializable
data class DialogStateResponseDto(
    val rawMessages: List<StateMessageDto>,
    val summaries: List<SummaryDto>
)

@Serializable
data class StateMessageDto(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String
)

@Serializable
data class ComparisonRequestDto(
    val scenarioId: String
)

@Serializable
data class ComparisonResponseDto(
    val scenarioId: String,
    val description: String,
    val withCompressionMetrics: ScenarioMetricsDto,
    val withoutCompressionMetrics: ScenarioMetricsDto,
    val analysisText: String
)

@Serializable
data class ScenarioMetricsDto(
    val totalPromptTokens: Int?,
    val totalCompletionTokens: Int?,
    val totalTokens: Int?,
    val durationMs: Long,
    val messagesProcessed: Int,
    val summariesGenerated: Int,
    val tokensSaved: Int?,
    val qualityNotes: String?
)

@Serializable
data class ComparisonScenariosResponseDto(
    val scenarios: List<ScenarioInfoDto>
)

@Serializable
data class ScenarioInfoDto(
    val id: String,
    val description: String,
    val messagesCount: Int
)
