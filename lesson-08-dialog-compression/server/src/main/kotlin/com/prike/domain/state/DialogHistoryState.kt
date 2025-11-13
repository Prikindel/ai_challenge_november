package com.prike.domain.state

import com.prike.domain.model.DialogMessage
import com.prike.domain.model.MessageRole
import com.prike.domain.model.SummaryNode
import java.time.Instant
import java.util.UUID

class DialogHistoryState {
    private val rawMessages: MutableList<DialogMessage> = mutableListOf()
    private val archivedMessages: MutableList<DialogMessage> = mutableListOf()
    private val summaries: MutableList<SummaryNode> = mutableListOf()

    fun clear() {
        rawMessages.clear()
        archivedMessages.clear()
        summaries.clear()
    }

    fun addUserMessage(content: String, createdAt: Instant = Instant.now()): DialogMessage =
        addMessage(MessageRole.USER, content, createdAt)

    fun addAssistantMessage(content: String, createdAt: Instant = Instant.now()): DialogMessage =
        addMessage(MessageRole.ASSISTANT, content, createdAt)

    private fun addMessage(role: MessageRole, content: String, createdAt: Instant): DialogMessage {
        val message = DialogMessage(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            createdAt = createdAt
        )
        rawMessages += message
        return message
    }

    fun needsSummary(summaryInterval: Int): Boolean =
        rawMessages.count { it.role == MessageRole.USER } >= summaryInterval

    fun takeMessagesForSummary(summaryInterval: Int): List<DialogMessage> {
        if (!needsSummary(summaryInterval)) return emptyList()

        val chunk = mutableListOf<DialogMessage>()
        var userCount = 0

        for (message in rawMessages.asReversed()) {
            chunk.add(message)
            if (message.role == MessageRole.USER) {
                userCount++
                if (userCount == summaryInterval) break
            }
        }

        if (userCount < summaryInterval) return emptyList()

        chunk.reverse()
        return chunk
    }

    fun applySummary(summaryNode: SummaryNode, summarizedMessages: List<DialogMessage>) {
        val messageIds = summarizedMessages.map { it.id }.toSet()
        val updatedRaw = rawMessages.filterNot { it.id in messageIds }
        archivedMessages += summarizedMessages.map { it.copy(summarized = true) }
        rawMessages.clear()
        rawMessages += updatedRaw
        summaries += summaryNode
    }

    fun getRawMessages(): List<DialogMessage> = rawMessages.toList()

    fun getTimelineMessages(): List<DialogMessage> =
        (archivedMessages + rawMessages).sortedBy { it.createdAt }

    fun takeMessagesForSummaryBeforeMessage(targetMessageId: String, summaryInterval: Int): List<DialogMessage> {
        val targetIndex = rawMessages.indexOfFirst { it.id == targetMessageId }
        if (targetIndex < 0) return emptyList()
        
        // Берем сообщения до нового сообщения (не включая его)
        // Это только несумаризированные сообщения, которые еще в rawMessages
        val candidates = if (targetIndex > 0) {
            rawMessages.subList(0, targetIndex)
        } else {
            emptyList()
        }
        
        if (candidates.isEmpty()) return emptyList()

        // Считаем только несумаризированные пользовательские сообщения в candidates
        val unsumarizedUserMessages = candidates.filter { 
            it.role == MessageRole.USER && !it.summarized 
        }
        val userCountInCandidates = unsumarizedUserMessages.size
        
        // Сумаризация должна произойти, когда накопилось ровно summaryInterval пользовательских сообщений
        // Например, если summaryInterval = 3:
        // - После user1: candidates=[], userCount=0, новое сообщение user1 → всего 1 < 3, не сумаризируем
        // - После user2: candidates=[user1, assistant1], userCount=1, новое сообщение user2 → всего 2 < 3, не сумаризируем
        // - После user3: candidates=[user1, assistant1, user2, assistant2], userCount=2, новое сообщение user3 → всего 3 >= 3, сумаризируем!
        // Новое сообщение уже добавлено в rawMessages, поэтому проверяем: userCountInCandidates + 1 >= summaryInterval?
        // Но новое сообщение еще не в candidates, поэтому проверяем: userCountInCandidates >= summaryInterval
        
        // На самом деле, если summaryInterval = 3, то сумаризация должна произойти после 3-го пользовательского сообщения
        // То есть когда в candidates уже есть 2 пользовательских сообщения, и добавляется 3-е
        // Но мы проверяем ДО добавления нового, поэтому нужно: userCountInCandidates >= summaryInterval - 1
        // Или проще: проверяем, что userCountInCandidates >= summaryInterval (уже накопилось достаточно)
        
        // Исправление: сумаризация должна произойти, когда в candidates накопилось summaryInterval пользовательских сообщений
        // Но новое сообщение уже добавлено, поэтому если userCountInCandidates >= summaryInterval, значит уже накопилось достаточно
        // Но это неправильно! Если summaryInterval = 3, то после user3 должно быть сумаризировано.
        // В candidates будет [user1, assistant1, user2, assistant2], userCount = 2
        // Но user3 уже добавлен, поэтому всего 3. Нужно проверять: userCountInCandidates + 1 >= summaryInterval
        
        // Правильная логика: сумаризация происходит, когда ВКЛЮЧАЯ новое сообщение накопилось summaryInterval пользовательских
        // Новое сообщение уже в rawMessages, но не в candidates. Поэтому:
        // - Если новое сообщение - пользовательское, то userCountInCandidates + 1 - это общее количество
        // - Нужно проверить: userCountInCandidates + 1 >= summaryInterval
        
        val newMessage = rawMessages.getOrNull(targetIndex)
        val isNewMessageUser = newMessage?.role == MessageRole.USER && !newMessage.summarized
        val totalUserCount = userCountInCandidates + if (isNewMessageUser) 1 else 0
        
        // Сумаризация должна произойти, когда накопилось ровно summaryInterval пользовательских сообщений
        // Например, если summaryInterval = 3:
        // - После user3: candidates=[user1, assistant1, user2, assistant2], userCount=2, новое=user3 → total=3 >= 3, сумаризируем
        // Но в candidates только 2 пользовательских, поэтому берем все сообщения из candidates
        if (totalUserCount < summaryInterval) {
            return emptyList()
        }

        // Если в candidates меньше пользовательских сообщений, чем summaryInterval,
        // но с учетом нового сообщения накопилось достаточно, берем все сообщения из candidates
        if (userCountInCandidates < summaryInterval) {
            // Берем все сообщения из candidates (новое сообщение не включаем)
            return candidates.toList()
        }

        // Берем последние summaryInterval несумаризированных пользовательских сообщений 
        // из candidates и все сообщения между ними
        val targetUserMessages = unsumarizedUserMessages.takeLast(summaryInterval)
        val firstTargetUserIndex = candidates.indexOfFirst { it.id == targetUserMessages.first().id }
        
        if (firstTargetUserIndex < 0) return emptyList()

        // Собираем все сообщения, начиная с первого целевого пользовательского сообщения
        val chunk = candidates.subList(firstTargetUserIndex, candidates.size)
        
        // Проверяем, что собрали нужное количество пользовательских сообщений
        val actualUserCount = chunk.count { it.role == MessageRole.USER && !it.summarized }
        if (actualUserCount < summaryInterval) return emptyList()

        return chunk.toList()
    }

    fun getSummaries(): List<SummaryNode> = summaries.toList()

    fun recordManualSummary(summaryNode: SummaryNode, affectedMessages: List<DialogMessage>) {
        applySummary(summaryNode, affectedMessages)
    }

    /**
     * Заменяет все существующие сумаризации на новую (для кумулятивной стратегии)
     */
    fun replaceAllSummariesWith(newSummary: SummaryNode, newSummarizedMessages: List<DialogMessage>) {
        val messageIds = newSummarizedMessages.map { it.id }.toSet()
        val updatedRaw = rawMessages.filterNot { it.id in messageIds }
        
        // Архивируем все сообщения, которые были в старых сумаризациях
        val oldSummarizedMessageIds = summaries.flatMap { it.sourceMessageIds }.toSet()
        val oldSummarizedMessages = rawMessages.filter { it.id in oldSummarizedMessageIds }
        archivedMessages += oldSummarizedMessages.map { it.copy(summarized = true) }
        
        // Архивируем новые сумаризированные сообщения
        archivedMessages += newSummarizedMessages.map { it.copy(summarized = true) }
        
        // Заменяем все сумаризации на одну новую
        summaries.clear()
        summaries += newSummary
        
        // Обновляем rawMessages
        rawMessages.clear()
        rawMessages += updatedRaw
    }
}
