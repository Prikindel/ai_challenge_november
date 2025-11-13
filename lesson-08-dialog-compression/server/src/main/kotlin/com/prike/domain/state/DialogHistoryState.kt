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
        if (targetIndex <= 0) return emptyList()

        val candidates = rawMessages.subList(0, targetIndex)
        if (candidates.isEmpty()) return emptyList()

        val chunk = mutableListOf<DialogMessage>()
        var userCount = 0

        for (message in candidates.asReversed()) {
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

    fun getSummaries(): List<SummaryNode> = summaries.toList()

    fun recordManualSummary(summaryNode: SummaryNode, affectedMessages: List<DialogMessage>) {
        applySummary(summaryNode, affectedMessages)
    }
}
