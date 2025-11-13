package com.prike.domain.agent

import com.prike.config.DialogCompressionConfig
import com.prike.data.dto.UsageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.model.DialogMessage
import com.prike.domain.model.SummaryContent
import com.prike.domain.model.SummaryNode
import com.prike.domain.service.CumulativeSummaryStrategy
import com.prike.domain.service.IndependentSummaryStrategy
import com.prike.domain.service.SummaryParser
import com.prike.domain.service.SummaryStrategy

class DialogSummarizationAgent(
    private val aiRepository: AIRepository,
    private val summaryParser: SummaryParser,
    private val lessonConfig: DialogCompressionConfig
) {
    private fun getStrategy(strategyType: String): SummaryStrategy = when (strategyType.lowercase()) {
        "cumulative" -> CumulativeSummaryStrategy()
        "independent" -> IndependentSummaryStrategy()
        else -> IndependentSummaryStrategy()
    }

    suspend fun summarizeMessages(
        messages: List<DialogMessage>,
        existingSummaries: List<SummaryNode> = emptyList(),
        strategyType: String? = null
    ): SummaryResult? {
        if (messages.isEmpty()) return null

        val effectiveStrategyType = strategyType ?: lessonConfig.lesson.summaryStrategyType
        val strategy = getStrategy(effectiveStrategyType)

        val summaryPrompt = strategy.buildSummaryPrompt(
            messagesToSummarize = messages,
            existingSummaries = existingSummaries,
            compressionPromptTemplate = lessonConfig.lesson.compressionPromptTemplate
        )
        
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

    data class SummaryResult(
        val summary: SummaryContent,
        val usage: UsageDto?
    )
}
