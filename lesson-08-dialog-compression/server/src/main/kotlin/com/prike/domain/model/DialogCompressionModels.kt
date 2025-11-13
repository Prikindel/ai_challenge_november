package com.prike.domain.model

import java.time.Instant

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class DialogMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Instant,
    val summarized: Boolean = false
)

data class SummaryNode(
    val id: String,
    val createdAt: Instant,
    val summary: String,
    val facts: List<String>,
    val openQuestions: List<String>,
    val sourceMessageIds: List<String>,
    val anchorMessageId: String? = null,
    val rawTokens: Int? = null,
    val summaryTokens: Int? = null,
    val tokensSaved: Int? = null
)

data class SummaryContent(
    val summary: String,
    val facts: List<String>,
    val openQuestions: List<String>
)

data class ContextUsageReport(
    val summaryIds: List<String>,
    val rawMessages: List<ContextRawMessage>
)

data class ContextRawMessage(
    val id: String,
    val role: MessageRole,
    val contentPreview: String,
    val createdAt: Instant
)

data class TokenUsageMetrics(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val hypotheticalPromptTokens: Int?,
    val tokensSavedByCompression: Int?
)

data class AgentResponseMetrics(
    val answer: String,
    val contextUsed: ContextUsageReport,
    val tokenUsage: TokenUsageMetrics,
    val summaries: List<SummaryNode>,
    val rawMessagesCount: Int,
    val summaryInterval: Int
)

data class DialogStateSnapshot(
    val messages: List<DialogMessage>,
    val summaries: List<SummaryNode>
)

data class ComparisonScenarioMetrics(
    val totalPromptTokens: Int?,
    val totalCompletionTokens: Int?,
    val totalTokens: Int?,
    val durationMs: Long,
    val messagesProcessed: Int,
    val summariesGenerated: Int,
    val tokensSaved: Int?,
    val qualityNotes: String?
)

data class ComparisonReport(
    val scenarioId: String,
    val description: String,
    val withCompressionMetrics: ComparisonScenarioMetrics,
    val withoutCompressionMetrics: ComparisonScenarioMetrics,
    val analysisText: String
)
