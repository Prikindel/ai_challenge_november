package com.prike.domain.service

import com.prike.data.dto.MessageDto
import com.prike.domain.model.DialogMessage
import com.prike.domain.model.SummaryNode

/**
 * Стратегия формирования промпта для сумаризации
 */
interface SummaryStrategy {
    /**
     * Формирует промпт для сумаризации на основе сообщений и существующих сумаризаций
     */
    fun buildSummaryPrompt(
        messagesToSummarize: List<DialogMessage>,
        existingSummaries: List<SummaryNode>,
        compressionPromptTemplate: String
    ): List<MessageDto>
}

/**
 * Независимая стратегия: каждая сумаризация формируется только на основе новых сообщений,
 * предыдущие сумаризации не включаются
 */
class IndependentSummaryStrategy : SummaryStrategy {
    override fun buildSummaryPrompt(
        messagesToSummarize: List<DialogMessage>,
        existingSummaries: List<SummaryNode>,
        compressionPromptTemplate: String
    ): List<MessageDto> {
        val instructions = compressionPromptTemplate.trim()
        val conversation = messagesToSummarize.joinToString(separator = "\n") { message ->
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

    private fun com.prike.domain.model.MessageRole.toApiRole(): String = when (this) {
        com.prike.domain.model.MessageRole.USER -> "user"
        com.prike.domain.model.MessageRole.ASSISTANT -> "assistant"
        com.prike.domain.model.MessageRole.SYSTEM -> "system"
    }
}

/**
 * Кумулятивная стратегия: сумаризация включает предыдущие сумаризации,
 * всегда формируется один системный промпт с полной историей
 */
class CumulativeSummaryStrategy : SummaryStrategy {
    override fun buildSummaryPrompt(
        messagesToSummarize: List<DialogMessage>,
        existingSummaries: List<SummaryNode>,
        compressionPromptTemplate: String
    ): List<MessageDto> {
        val instructions = compressionPromptTemplate.trim()
        
        val summaryContext = if (existingSummaries.isNotEmpty()) {
            val previousSummaries = existingSummaries.joinToString(separator = "\n\n") { summary ->
                buildString {
                    appendLine("Предыдущая сумаризация:")
                    appendLine(summary.summary)
                    if (summary.facts.isNotEmpty()) {
                        appendLine("Факты:")
                        summary.facts.forEach { appendLine("- $it") }
                    }
                    if (summary.openQuestions.isNotEmpty()) {
                        val questions = summary.openQuestions.filter { it.lowercase() != "none" && it.isNotBlank() }
                        if (questions.isNotEmpty()) {
                            appendLine("Открытые вопросы:")
                            questions.forEach { appendLine("- $it") }
                        }
                    }
                }.trim()
            }
            
            "Предыдущие сумаризации диалога:\n$previousSummaries\n\n"
        } else {
            ""
        }
        
        val conversation = messagesToSummarize.joinToString(separator = "\n") { message ->
            "${message.role.toApiRole()}: ${message.content}"
        }

        val userContent = buildString {
            if (summaryContext.isNotEmpty()) {
                append(summaryContext)
            }
            appendLine("Новые сообщения диалога:")
            append(conversation)
            appendLine("\n\nСформируй обновленную сумаризацию, которая включает информацию из предыдущих сумаризаций и новых сообщений.")
        }

        return listOf(
            MessageDto(role = "system", content = instructions),
            MessageDto(role = "user", content = userContent.trim())
        )
    }

    private fun com.prike.domain.model.MessageRole.toApiRole(): String = when (this) {
        com.prike.domain.model.MessageRole.USER -> "user"
        com.prike.domain.model.MessageRole.ASSISTANT -> "assistant"
        com.prike.domain.model.MessageRole.SYSTEM -> "system"
    }
}


