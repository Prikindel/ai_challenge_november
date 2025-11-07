package com.prike.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReasoningRequestDto(
    val question: String? = null,
    val mode: String? = null
)

@Serializable
data class ReasoningResponseDto(
    val task: String,
    val mode: String,
    val direct: ReasoningModeResponseDto? = null,
    val stepByStep: ReasoningModeResponseDto? = null,
    val promptFromOtherAI: PromptFromOtherAIResponseDto? = null,
    val expertPanel: ExpertPanelResponseDto? = null,
    val comparison: String? = null,
    val debug: ReasoningDebugDto? = null
)

@Serializable
data class ReasoningModeResponseDto(
    val prompt: String,
    val answer: String,
    val debug: DebugInfoDto? = null
)

@Serializable
data class PromptFromOtherAIResponseDto(
    val generatedPrompt: String,
    val answer: String,
    val notes: String,
    val usedFallback: Boolean,
    val promptDebug: DebugInfoDto? = null,
    val answerDebug: DebugInfoDto? = null
)

@Serializable
data class ReasoningDefaultTaskResponseDto(
    val defaultTask: String
)

@Serializable
data class ExpertPanelResponseDto(
    val experts: List<ExpertResponseDto>,
    val summary: String,
    val summaryDebug: DebugInfoDto? = null
)

@Serializable
data class ExpertResponseDto(
    val name: String,
    val style: String,
    val answer: String,
    val reasoning: String,
    val debug: DebugInfoDto? = null
)

@Serializable
data class ReasoningDebugDto(
    val comparison: DebugInfoDto? = null
)

@Serializable
data class DebugInfoDto(
    val llmRequest: String,
    val llmResponse: String
)


