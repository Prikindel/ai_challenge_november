package com.prike.domain.agent

import com.prike.config.DialogCompressionConfig
import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.model.AgentResponseMetrics
import com.prike.domain.model.ComparisonReport
import com.prike.domain.model.ComparisonScenarioMetrics
import com.prike.domain.model.ContextRawMessage
import com.prike.domain.model.ContextUsageReport
import com.prike.domain.model.DialogMessage
import com.prike.domain.model.DialogStateSnapshot
import com.prike.domain.model.MessageRole
import com.prike.domain.model.SummaryContent
import com.prike.domain.model.SummaryNode
import com.prike.domain.model.TokenUsageMetrics
import com.prike.domain.service.SummaryParser
import com.prike.domain.service.TokenEstimator
import com.prike.domain.state.DialogHistoryState
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

class DialogCompressionAgent(
    aiRepository: AIRepository,
    private val lessonConfig: DialogCompressionConfig,
    private val tokenEstimator: TokenEstimator,
    private val summaryParser: SummaryParser,
    private val baseModel: String?
) : BaseAgent(aiRepository) {

    private val logger = LoggerFactory.getLogger(DialogCompressionAgent::class.java)
    private val mutex = Mutex()
    private val historyState = DialogHistoryState()
    private val formatter = DateTimeFormatter.ISO_INSTANT

    suspend fun handleMessage(command: HandleMessageCommand): AgentResponseMetrics = mutex.withLock {
        handleMessageInternal(command)
    }

    private suspend fun handleMessageInternal(command: HandleMessageCommand): AgentResponseMetrics {
        val summaryInterval = command.summaryIntervalOverride?.takeIf { it > 0 }
            ?: lessonConfig.lesson.summaryInterval
        val maxSummaries = command.maxSummariesInContext?.takeIf { it >= 0 }
            ?: lessonConfig.lesson.maxSummariesInContext

        historyState.addUserMessage(command.userMessage)

        val context = buildContext(maxSummaries)
        val completionResult = aiRepository.getMessageWithHistory(
            messages = context.messages,
            options = AIRepository.ChatCompletionOptions()
        )

        val assistantAnswer = completionResult.message
        historyState.addAssistantMessage(assistantAnswer)

        if (historyState.needsSummary(summaryInterval)) {
            runCatching { performSummary(summaryInterval) }
                .onFailure { throwable ->
                    logger.warn("Не удалось сформировать summary: ${throwable.message}", throwable)
                }
        }

        val promptTokensApprox = calculatePromptTokens(context.messages)
        val usage = completionResult.usage
        val promptTokens = usage?.promptTokens ?: promptTokensApprox
        val completionTokens = usage?.completionTokens
        val totalTokens = usage?.totalTokens

        val hypotheticalMessages = buildHypotheticalFullHistory()
        val hypotheticalPromptTokens = calculateHypotheticalTokens(hypotheticalMessages)
        val tokensSaved = if (promptTokens != null && hypotheticalPromptTokens != null) {
            (hypotheticalPromptTokens - promptTokens).coerceAtLeast(0)
        } else null

        return AgentResponseMetrics(
            answer = assistantAnswer,
            contextUsed = context.report,
            tokenUsage = TokenUsageMetrics(
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                hypotheticalPromptTokens = hypotheticalPromptTokens,
                tokensSavedByCompression = tokensSaved
            ),
            summaries = historyState.getSummaries(),
            rawMessagesCount = historyState.getRawMessages().size,
            summaryInterval = summaryInterval
        )
    }

    suspend fun reset() = mutex.withLock {
        historyState.clear()
    }

    suspend fun getState(): DialogStateSnapshot = mutex.withLock {
        DialogStateSnapshot(
            rawMessages = historyState.getRawMessages(),
            summaries = historyState.getSummaries()
        )
    }

    suspend fun runComparisonScenario(command: RunComparisonScenarioCommand): ComparisonReport = mutex.withLock {
        val scenario = lessonConfig.scenarios.firstOrNull { it.id == command.scenarioId }
            ?: error("Сценарий ${command.scenarioId} не найден")

        val withoutCompression = simulateScenario(scenario, enableCompression = false)
        val withCompression = simulateScenario(scenario, enableCompression = true)

        val adjustedWithCompression = withCompression.copy(
            tokensSaved = if (
                withCompression.totalPromptTokens != null &&
                withoutCompression.totalPromptTokens != null
            ) {
                (withoutCompression.totalPromptTokens - withCompression.totalPromptTokens)
                    .coerceAtLeast(0)
            } else null
        )

        val analysis = buildString {
            appendLine("Сжатие включено: ${withCompression.totalPromptTokens ?: "?"} токенов промпта")
            appendLine("Сжатие отключено: ${withoutCompression.totalPromptTokens ?: "?"} токенов промпта")
            adjustedWithCompression.tokensSaved?.let { appendLine("Экономия токенов: $it") }
            appendLine("Сводок (summary) сформировано: ${adjustedWithCompression.summariesGenerated}")
        }

        historyState.clear()

        return ComparisonReport(
            scenarioId = scenario.id,
            description = scenario.description,
            withCompressionMetrics = adjustedWithCompression,
            withoutCompressionMetrics = withoutCompression,
            analysisText = analysis.trim()
        )
    }

    private suspend fun simulateScenario(
        scenario: DialogCompressionConfig.Scenario,
        enableCompression: Boolean
    ): ComparisonScenarioMetrics {
        historyState.clear()
        val summaryInterval = if (enableCompression) lessonConfig.lesson.summaryInterval else Int.MAX_VALUE
        val maxSummaries = if (enableCompression) lessonConfig.lesson.maxSummariesInContext else 0

        val startTime = System.nanoTime()
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var totalTokens = 0
        var summariesCount = 0

        scenario.seedMessages.forEach { seed ->
            if (seed.role.lowercase(Locale.getDefault()) == "user") {
                val result = handleMessageInternal(
                    HandleMessageCommand(
                        userMessage = seed.content,
                        summaryIntervalOverride = summaryInterval,
                        maxSummariesInContext = maxSummaries
                    )
                )
                result.tokenUsage.promptTokens?.let { totalPromptTokens += it }
                result.tokenUsage.completionTokens?.let { totalCompletionTokens += it }
                result.tokenUsage.totalTokens?.let { totalTokens += it }
                summariesCount = result.summaries.size
            } else {
                historyState.addAssistantMessage(seed.content, Instant.now())
            }
        }

        val durationMs = (System.nanoTime() - startTime) / 1_000_000

        val descriptiveQuality = when {
            totalTokens == 0 -> "Недостаточно данных для оценки качества."
            summariesCount == 0 && enableCompression -> "Сжатие не сработало: сообщений меньше интервала."
            enableCompression -> "Сжатие сократило контекст, проверьте, что ответы сохраняют смысл."
            else -> "Базовый прогон без компрессии; ответы максимально точны, но контекст дорогой."
        }

        return ComparisonScenarioMetrics(
            totalPromptTokens = totalPromptTokens.takeIf { it > 0 },
            totalCompletionTokens = totalCompletionTokens.takeIf { it > 0 },
            totalTokens = totalTokens.takeIf { it > 0 },
            durationMs = durationMs,
            messagesProcessed = scenario.seedMessages.count { it.role.lowercase(Locale.getDefault()) == "user" },
            summariesGenerated = summariesCount,
            tokensSaved = null,
            qualityNotes = descriptiveQuality
        )
    }

    private fun buildContext(maxSummaries: Int): ContextData {
        val summaries = historyState.getSummaries().takeLast(maxSummaries)
        val rawMessages = historyState.getRawMessages()
            .takeLast(lessonConfig.lesson.rawHistoryLimit)

        val summaryMessages = summaries.map { summary ->
            MessageDto(
                role = "system",
                content = buildSummaryContent(summary)
            )
        }

        val contextRawMessages = rawMessages.map { message ->
            MessageDto(
                role = message.role.toApiRole(),
                content = message.content
            )
        }

        val report = ContextUsageReport(
            summaryIds = summaries.map { it.id },
            rawMessages = rawMessages.map { message ->
                ContextRawMessage(
                    id = message.id,
                    role = message.role,
                    contentPreview = message.content.take(120),
                    createdAt = message.createdAt
                )
            }
        )

        return ContextData(
            messages = summaryMessages + contextRawMessages,
            report = report
        )
    }

    private fun buildSummaryContent(summary: SummaryNode): String = buildString {
        appendLine("Summary ${summary.id} (${formatter.format(summary.createdAt)}):")
        appendLine(summary.summary)
        if (summary.facts.isNotEmpty()) {
            appendLine("Facts:")
            summary.facts.forEach { appendLine("- $it") }
        }
        if (summary.openQuestions.isNotEmpty()) {
            appendLine("Open questions:")
            summary.openQuestions.forEach { appendLine("- $it") }
        }
    }.trim()

    private suspend fun performSummary(summaryInterval: Int) {
        val messages = historyState.takeMessagesForSummary(summaryInterval)
        if (messages.size < summaryInterval) return

        val summaryPrompt = buildSummaryPrompt(messages)
        val completionResult = aiRepository.getMessageWithHistory(
            messages = summaryPrompt,
            options = AIRepository.ChatCompletionOptions(
                model = lessonConfig.lesson.compressionModel,
                temperature = 0.2,
                useJsonResponseFormat = true
            )
        )

        val parsed = summaryParser.parse(completionResult.message)
        val summaryNode = createSummaryNode(parsed, messages)
        historyState.applySummary(summaryNode, messages)
    }

    private fun buildSummaryPrompt(messages: List<DialogMessage>): List<MessageDto> {
        val instructions = lessonConfig.lesson.compressionPromptTemplate.trim()
        val conversation = messages.joinToString(separator = "\n") { message ->
            "${message.role.name.lowercase(Locale.getDefault())}: ${message.content}"
        }

        return listOf(
            MessageDto(role = "system", content = instructions),
            MessageDto(
                role = "user",
                content = "Ниже фрагмент диалога. Сожми его по правилам.\n$conversation"
            )
        )
    }

    private fun createSummaryNode(summary: SummaryContent, messages: List<DialogMessage>): SummaryNode =
        SummaryNode(
            id = UUID.randomUUID().toString(),
            createdAt = Instant.now(),
            summary = summary.summary,
            facts = summary.facts,
            openQuestions = summary.openQuestions,
            sourceMessageIds = messages.map { it.id }
        )

    private fun buildHypotheticalFullHistory(): List<MessageDto> {
        return historyState.getAllMessages()
            .sortedBy { it.createdAt }
            .map { message ->
                MessageDto(role = message.role.toApiRole(), content = message.content)
            }
    }

    private fun calculateHypotheticalTokens(messages: List<MessageDto>): Int? {
        if (messages.isEmpty()) return null
        return tokenEstimator.approximateForModel(messages, baseModel)
    }

    private fun calculatePromptTokens(messages: List<MessageDto>): Int? {
        if (messages.isEmpty()) return null
        return tokenEstimator.approximateForModel(messages, baseModel)
    }

    private fun MessageRole.toApiRole(): String = when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.SYSTEM -> "system"
    }

    data class HandleMessageCommand(
        val userMessage: String,
        val summaryIntervalOverride: Int? = null,
        val maxSummariesInContext: Int? = null
    )

    data class RunComparisonScenarioCommand(
        val scenarioId: String
    )

    private data class ContextData(
        val messages: List<MessageDto>,
        val report: ContextUsageReport
    )
}
