package com.prike.domain.agent

import com.prike.config.DialogCompressionConfig
import com.prike.data.dto.MessageDto
import com.prike.data.dto.UsageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.model.DialogMessage
import com.prike.domain.model.MessageRole
import com.prike.domain.model.SummaryContent
import com.prike.domain.service.SummaryParser

class DialogSummarizationAgent(
    private val aiRepository: AIRepository,
    private val summaryParser: SummaryParser,
    private val lessonConfig: DialogCompressionConfig
) {

    suspend fun summarizeMessages(messages: List<DialogMessage>): SummaryResult? {
        if (messages.isEmpty()) return null

        val summaryPrompt = buildSummaryPrompt(messages)
        val completionResult = aiRepository.getMessageWithHistory(
            summaryPrompt,
            AIRepository.ChatCompletionOptions(
                model = lessonConfig.lesson.compressionModel,
                temperature = 0.2,
                useJsonResponseFormat = true
            )
        )

        val parsed = summaryParser.parse(completionResult.message)
        return SummaryResult(parsed, completionResult.usage)
    }

    private fun buildSummaryPrompt(messages: List<DialogMessage>): List<MessageDto> {
        val instructions = lessonConfig.lesson.compressionPromptTemplate.trim()
        val conversation = messages.joinToString(separator = "\n") { message ->
            "${message.role.toApiRole()}: ${message.content}"
        }

        return listOf(
            MessageDto(role = "system", content = instructions),
            MessageDto(
                role = "user",
                content = "Ниже фрагмент диалога. Сожми его по правилам.\n$conversation"
            )
        )
    }

    data class SummaryResult(
        val summary: SummaryContent,
        val usage: UsageDto?
    )

    private fun MessageRole.toApiRole(): String = when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.SYSTEM -> "system"
    }
}
