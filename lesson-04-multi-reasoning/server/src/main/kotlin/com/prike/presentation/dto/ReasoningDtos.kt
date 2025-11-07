package com.prike.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReasoningRequestDto(
    val question: String? = null
)

@Serializable
data class ReasoningResponseDto(
    val task: String,
    val direct: ReasoningModeResponseDto,
    val stepByStep: ReasoningModeResponseDto,
    val promptFromOtherAI: PromptFromOtherAIResponseDto,
    val expertPanel: ExpertPanelResponseDto,
    val comparison: String,
    val debug: ReasoningDebugDto
)

@Serializable
data class ReasoningModeResponseDto(
    val prompt: String,
    val answer: String,
    val debug: DebugInfoDto
)

@Serializable
data class PromptFromOtherAIResponseDto(
    val generatedPrompt: String,
    val answer: String,
    val promptDebug: DebugInfoDto,
    val answerDebug: DebugInfoDto
)

@Serializable
data class ExpertPanelResponseDto(
    val experts: List<ExpertResponseDto>,
    val summary: String,
    val summaryDebug: DebugInfoDto
)

@Serializable
data class ExpertResponseDto(
    val name: String,
    val style: String,
    val answer: String,
    val reasoning: String,
    val debug: DebugInfoDto
)

@Serializable
data class ReasoningDebugDto(
    val comparison: DebugInfoDto
)

@Serializable
data class DebugInfoDto(
    val llmRequest: String,
    val llmResponse: String
)


