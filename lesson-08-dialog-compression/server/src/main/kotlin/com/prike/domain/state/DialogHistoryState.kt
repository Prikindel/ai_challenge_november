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

    fun needsSummary(summaryInterval: Int): Boolean = rawMessages.size >= summaryInterval

    fun takeMessagesForSummary(summaryInterval: Int): List<DialogMessage> {
        val candidates = rawMessages.takeLast(summaryInterval)
        if (candidates.size < summaryInterval) return emptyList()
        return candidates
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

    fun getAllMessages(): List<DialogMessage> = archivedMessages + rawMessages

    fun getSummaries(): List<SummaryNode> = summaries.toList()

    fun recordManualSummary(summaryNode: SummaryNode, affectedMessages: List<DialogMessage>) {
        applySummary(summaryNode, affectedMessages)
    }
}
