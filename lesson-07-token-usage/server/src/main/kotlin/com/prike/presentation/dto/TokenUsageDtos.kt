package com.prike.presentation.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenUsageScenarioTemplateDto(
    val scenarioId: String,
    val scenarioName: String,
    val defaultPrompt: String,
    val description: String? = null
)

@Serializable
data class TokenUsageScenariosResponseDto(
    val scenarios: List<TokenUsageScenarioTemplateDto>,
    val promptTokenLimit: Int,
    val defaultMaxResponseTokens: Int,
    val tokenEncoding: String
)

@Serializable
data class TokenUsageScenarioOverrideDto(
    val scenarioId: String,
    val promptText: String? = null
)

@Serializable
data class TokenUsageAnalyzeRequestDto(
    val scenarios: List<TokenUsageScenarioOverrideDto>? = null
)

@Serializable
data class TokenUsageScenarioResultDto(
    val scenarioId: String,
    val scenarioName: String,
    val promptText: String,
    val responseText: String? = null,
    val promptTokens: Int,
    val responseTokens: Int? = null,
    val totalTokens: Int,
    val durationMs: Long,
    val status: String,
    val errorMessage: String? = null
)

@Serializable
data class TokenUsageRunDto(
    val runId: String,
    val startedAt: String,
    val finishedAt: String,
    val results: List<TokenUsageScenarioResultDto>
)

@Serializable
data class TokenUsageAnalyzeResponseDto(
    val currentRun: TokenUsageRunDto,
    val history: List<TokenUsageRunDto>
)

@Serializable
data class TokenUsageHistoryResponseDto(
    val history: List<TokenUsageRunDto>
)

