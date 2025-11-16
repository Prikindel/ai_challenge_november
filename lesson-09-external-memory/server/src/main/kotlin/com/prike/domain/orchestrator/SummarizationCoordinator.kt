package com.prike.domain.orchestrator

import com.prike.data.model.MemoryEntry
import com.prike.data.model.MessageRole
import com.prike.domain.agent.SummarizationAgent
import com.prike.domain.service.MemoryService
import com.prike.config.MemoryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Координатор суммаризации.
 *
 * - Логика проверки необходимости суммаризации вынесена из основного оркестратора.
 * - Суммаризация выполняется каждые 10 пользовательских сообщений внутри сегмента из 100 пользовательских сообщений.
 * - В суммаризацию включаем все, включая предыдущие суммаризации, чтобы не терять контекст.
 * - После 100 пользовательских сообщений начинается новый сегмент суммаризации (вторые 100 и т.д.),
 *   прошлые сегменты не учитываются.
 */
class SummarizationCoordinator(
    private val memoryService: MemoryService,
    private val summarizationAgent: SummarizationAgent,
    private val config: MemoryConfig.SummarizationConfig?,
    private val backgroundScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val logger = LoggerFactory.getLogger(SummarizationCoordinator::class.java)

    /**
     * Запустить проверку и суммаризацию в фоне, не блокируя ответ пользователю.
     */
    fun triggerIfNeeded() {
        backgroundScope.launch {
            try {
                processIfNeeded()
            } catch (e: Exception) {
                logger.warn("Суммаризация завершилась с ошибкой: ${e.message}")
            }
        }
    }

    /**
     * Проверить необходимость и выполнить суммаризацию.
     */
    suspend fun processIfNeeded() {
        val history = memoryService.getHistory()
        if (history.isEmpty()) return

        val (segmentStartIndex, userCountInSegment) = computeCurrentSegment(history)

        // Суммаризируем каждые N пользовательских сообщений (из конфигурации)
        val perSummary = (config?.userMessagesPerSummary ?: 10).coerceAtLeast(1)
        if (userCountInSegment == 0 || userCountInSegment % perSummary != 0) return

        // История для суммаризации: текущий сегмент (включая SUMMARY)
        val segmentHistory = history.drop(segmentStartIndex)

        val messages = memoryService.toMessageDtos(segmentHistory)
        val result = summarizationAgent.summarize(messages)

        val summaryText = result.summary.trim()
        if (summaryText.isBlank()) {
            logger.warn("Получена пустая суммаризация — запись не будет сохранена")
            return
        }

        val summaryEntry = MemoryEntry.create(
            id = UUID.randomUUID().toString(),
            role = MessageRole.SUMMARY,
            content = summaryText,
            metadata = result.usage?.let {
                com.prike.data.model.MemoryMetadata(
                    model = null,
                    promptTokens = it.promptTokens,
                    completionTokens = it.completionTokens,
                    totalTokens = it.totalTokens
                )
            }
        )

        memoryService.saveEntries(listOf(summaryEntry))
    }

    /**
     * Вычислить начало текущего сегмента (по пользовательским сообщениям)
     * и количество пользовательских сообщений внутри него.
     *
     * Сегмент = каждые 100 пользовательских сообщений.
     */
    private fun computeCurrentSegment(history: List<MemoryEntry>): Pair<Int, Int> {
        // Собираем индексы пользовательских сообщений
        val userIndices = history.withIndex()
            .filter { it.value.role == MessageRole.USER }
            .map { it.index }
        val totalUserCount = userIndices.size
        if (totalUserCount == 0) return 0 to 0

        // Номер сегмента (начиная с 0)
        val perSegment = (config?.userMessagesPerSegment ?: 100).coerceAtLeast(1)
        val segmentNumber = (totalUserCount - 1) / perSegment
        val segmentStartUserOrdinal = segmentNumber * perSegment // 0, perSegment, 2*perSegment ...

        // Индекс первого пользовательского сообщения сегмента в истории
        val firstUserIndexInSegment = userIndices[segmentStartUserOrdinal]

        // Кол-во пользовательских сообщений в сегменте до текущего момента
        val userCountInSegment = totalUserCount - segmentStartUserOrdinal

        // Найти индекс в общей истории, откуда начинается сегмент
        // Это позиция первого пользовательского сообщения сегмента, но включаем
        // также возможные предыдущие записи до него в рамках "текущей сессии".
        // Для простоты считаем началом сегмента именно это место.
        val segmentStartIndex = firstUserIndexInSegment

        return segmentStartIndex to userCountInSegment
    }
}


