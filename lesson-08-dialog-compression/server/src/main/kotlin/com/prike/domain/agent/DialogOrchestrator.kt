package com.prike.domain.agent

import com.prike.config.DialogCompressionConfig
import com.prike.data.dto.MessageDto
import com.prike.data.dto.UsageDto
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
import com.prike.domain.service.TokenEstimator
import com.prike.domain.state.DialogHistoryState
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

class DialogOrchestrator(
    private val conversationAgent: DialogConversationAgent,
    private val summarizationAgent: DialogSummarizationAgent,
    private val lessonConfig: DialogCompressionConfig,
    private val tokenEstimator: TokenEstimator,
    private val baseModel: String?
) {

    private val logger = LoggerFactory.getLogger(DialogOrchestrator::class.java)
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
        val strategyType = command.summaryStrategyTypeOverride?.takeIf { it.isNotBlank() }
            ?: lessonConfig.lesson.summaryStrategyType

        logger.info("Обработка сообщения: summaryInterval=$summaryInterval (override=${command.summaryIntervalOverride}), maxSummaries=$maxSummaries, strategyType=$strategyType")

        val newUserMessage = historyState.addUserMessage(command.userMessage)

        // Принудительная сумаризация срабатывает только если:
        // 1. Количество сообщений превышает порог (rawHistoryLimit * 2)
        // 2. И при этом накопилось достаточно пользовательских сообщений по summaryInterval
        val forceSummaryThreshold = lessonConfig.lesson.rawHistoryLimit * 2

        while (true) {
            val rawMessagesCount = historyState.getRawMessages().size
            val unsummarizedUserCount = historyState.getRawMessages().count { 
                it.role == MessageRole.USER && !it.summarized 
            }
            
            logger.info("Проверка сумаризации: rawMessagesCount=$rawMessagesCount, unsummarizedUserCount=$unsummarizedUserCount, summaryInterval=$summaryInterval, forceThreshold=$forceSummaryThreshold")
            
            val messagesToSummarize: List<DialogMessage> = if (rawMessagesCount > forceSummaryThreshold && unsummarizedUserCount >= summaryInterval) {
                // Принудительная сумаризация: берем первые сообщения до лимита, но только если накопилось summaryInterval пользовательских
                logger.info("Условие принудительной сумаризации выполнено, проверяем выборку...")
                val allRaw = historyState.getRawMessages()
                val toSummarize = allRaw.take(allRaw.size - lessonConfig.lesson.rawHistoryLimit)
                val userCountInToSummarize = toSummarize.count { it.role == MessageRole.USER && !it.summarized }
                
                logger.info("Принудительная выборка: ${toSummarize.size} сообщений, пользовательских: $userCountInToSummarize, требуется: $summaryInterval")
                
                if (toSummarize.isNotEmpty() && userCountInToSummarize >= summaryInterval) {
                    logger.info("Принудительная сумаризация: ${toSummarize.size} сообщений (пользовательских: $userCountInToSummarize, rawMessages: $rawMessagesCount > threshold: $forceSummaryThreshold)")
                    toSummarize
                } else {
                    logger.info("В принудительной выборке недостаточно пользовательских сообщений ($userCountInToSummarize < $summaryInterval), используем обычную логику")
                    // Если в принудительной выборке недостаточно пользовательских сообщений, используем обычную логику
                    historyState.takeMessagesForSummaryBeforeMessage(
                targetMessageId = newUserMessage.id,
                summaryInterval = summaryInterval
            )
                }
            } else {
                // Обычная логика: сумаризация по summaryInterval
                logger.info("Обычная проверка сумаризации: rawUserCount=$unsummarizedUserCount, summaryInterval=$summaryInterval")
                
                historyState.takeMessagesForSummaryBeforeMessage(
                    targetMessageId = newUserMessage.id,
                    summaryInterval = summaryInterval
                )
            }

            if (messagesToSummarize.isEmpty()) {
                logger.info("Сумаризация не требуется: недостаточно сообщений для интервала $summaryInterval (найдено: $unsummarizedUserCount)")
                break
            }

            val userCountInSummary = messagesToSummarize.count { it.role == MessageRole.USER && !it.summarized }
            logger.info("Сумаризация будет выполнена: ${messagesToSummarize.size} сообщений (пользовательских: $userCountInSummary, требуется: $summaryInterval)")

            val result = runCatching {
                performSummaryForMessages(messagesToSummarize, anchorMessageId = newUserMessage.id, strategyType = strategyType)
            }

            if (result.isFailure) {
                logger.warn(
                    "Не удалось сформировать summary: ${result.exceptionOrNull()?.message}",
                    result.exceptionOrNull()
                )
                break
            }
        }

        val context = buildContext(maxSummaries, strategyType)
        val completionResult = conversationAgent.respond(context.messages)

        val assistantAnswer = completionResult.message
        historyState.addAssistantMessage(assistantAnswer)

        val usage = completionResult.usage
        val promptTokens = usage?.promptTokens ?: calculatePromptTokens(context.messages)
        val completionTokens = usage?.completionTokens
        val totalTokens = usage?.totalTokens

        val hypotheticalMessages = buildHypotheticalFullHistory()
        val summaries = historyState.getSummaries()
        val hypotheticalPromptTokens = calculateHypotheticalTokens(
            messages = hypotheticalMessages,
            allowEstimate = summaries.isNotEmpty()
        )
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
            summaries = summaries,
            rawMessagesCount = historyState.getRawMessages().size,
            summaryInterval = summaryInterval
        )
    }

    suspend fun reset() = mutex.withLock {
        historyState.clear()
    }

    suspend fun getState(): DialogStateSnapshot = mutex.withLock {
        DialogStateSnapshot(
            messages = historyState.getTimelineMessages(),
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

    private fun buildContext(maxSummaries: Int, strategyType: String): ContextData {
        val allSummaries = historyState.getSummaries()
        // Для кумулятивной стратегии всегда берем только последний (единственный) summary
        val summaries = if (strategyType.lowercase() == "cumulative") {
            allSummaries.takeLast(1)
        } else {
            allSummaries.takeLast(maxSummaries)
        }
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

    private suspend fun performSummaryForMessages(
        messages: List<DialogMessage>,
        anchorMessageId: String?,
        strategyType: String
    ) {
        if (messages.isEmpty()) return
        
        val strategyTypeLower = strategyType.lowercase()
        val existingSummaries = if (strategyTypeLower == "cumulative") {
            // Для кумулятивной стратегии передаем все существующие сумаризации
            historyState.getSummaries()
        } else {
            // Для независимой стратегии не передаем существующие сумаризации
            emptyList()
        }
        
        val summaryResult = summarizationAgent.summarizeMessages(messages, existingSummaries, strategyType) ?: return
        val summaryNode = createSummaryNode(summaryResult.summary, messages, anchorMessageId, summaryResult.usage)
        
        // Для кумулятивной стратегии заменяем все старые сумаризации на новую
        if (strategyTypeLower == "cumulative" && existingSummaries.isNotEmpty()) {
            historyState.replaceAllSummariesWith(summaryNode, messages)
        } else {
            historyState.applySummary(summaryNode, messages)
        }
    }

    private fun createSummaryNode(
        summary: SummaryContent,
        messages: List<DialogMessage>,
        anchorMessageId: String?,
        usage: UsageDto?
    ): SummaryNode {
        val rawTokens = calculateRawTokens(messages)
        val summaryTokens = calculateSummaryTokens(summary, usage)
        val tokensSaved = if (rawTokens != null && summaryTokens != null) {
            (rawTokens - summaryTokens).coerceAtLeast(0)
        } else null

        return SummaryNode(
            id = UUID.randomUUID().toString(),
            createdAt = Instant.now(),
            summary = summary.summary,
            facts = summary.facts,
            openQuestions = summary.openQuestions,
            sourceMessageIds = messages.map { it.id },
            anchorMessageId = anchorMessageId,
            rawTokens = rawTokens,
            summaryTokens = summaryTokens,
            tokensSaved = tokensSaved
        )
    }

    private fun calculateRawTokens(messages: List<DialogMessage>): Int? {
        if (messages.isEmpty()) return null
        val dtos = messages.map { MessageDto(role = it.role.toApiRole(), content = it.content) }
        return tokenEstimator.approximateForModel(dtos, baseModel)
    }

    private fun calculateSummaryTokens(summary: SummaryContent, usage: UsageDto?): Int? {
        usage?.completionTokens?.let { return it }
        val text = buildSummaryBody(summary)
        return tokenEstimator.approximateForModel(
            listOf(MessageDto(role = "assistant", content = text)),
            baseModel
        )
    }

    private fun buildSummaryBody(summary: SummaryContent): String = buildString {
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

    private fun buildHypotheticalFullHistory(): List<MessageDto> {
        return historyState.getTimelineMessages().map { message ->
            MessageDto(role = message.role.toApiRole(), content = message.content)
        }
    }

    private fun calculateHypotheticalTokens(messages: List<MessageDto>, allowEstimate: Boolean): Int? {
        if (!allowEstimate || messages.isEmpty()) return null
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
        val maxSummariesInContext: Int? = null,
        val summaryStrategyTypeOverride: String? = null
    )

    data class RunComparisonScenarioCommand(
        val scenarioId: String
    )

    private data class ContextData(
        val messages: List<MessageDto>,
        val report: ContextUsageReport
    )
}
