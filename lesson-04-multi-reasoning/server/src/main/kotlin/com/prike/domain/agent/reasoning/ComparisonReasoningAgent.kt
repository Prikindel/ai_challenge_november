package com.prike.domain.agent.reasoning

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.model.ComparisonResult
import com.prike.domain.agent.model.ModeResult
import com.prike.domain.agent.model.ReasoningContext
import com.prike.domain.agent.model.ReasoningMode
import com.prike.domain.agent.model.ReasoningOutput
import kotlinx.serialization.Serializable

class ComparisonReasoningAgent(
    aiRepository: AIRepository
) : ReasoningStrategyAgent(aiRepository) {

    override val mode: ReasoningMode = ReasoningMode.COMPARISON

    override suspend fun execute(context: ReasoningContext): ReasoningOutput {
        val direct = context.results[ReasoningMode.DIRECT] as? ReasoningOutput.Direct
            ?: error("Direct result is required for comparison")
        val step = context.results[ReasoningMode.STEP_BY_STEP] as? ReasoningOutput.StepByStep
            ?: error("Step-by-step result is required for comparison")
        val prompt = context.results[ReasoningMode.PROMPT_FROM_OTHER_AI] as? ReasoningOutput.PromptFromOtherAI
            ?: error("Prompt from other AI result is required for comparison")
        val experts = context.results[ReasoningMode.EXPERT_PANEL] as? ReasoningOutput.ExpertPanel
            ?: error("Expert panel result is required for comparison")

        val messages = listOf(
            MessageDto(
                role = "system",
                content = "Ты сравниваешь стратегии рассуждения и выделяешь различия между ответами."
            ),
            MessageDto(
                role = "user",
                content = buildComparisonPrompt(
                    direct.result,
                    step.result,
                    prompt.answer,
                    experts.panel.summary
                )
            )
        )

        val result = getMessageWithHistory(messages)
        val parsed = runCatching {
            json.decodeFromString(ComparisonPayload.serializer(), result.message)
        }.getOrNull()

        val comparisonResult = ComparisonResult(
            summary = parsed?.comparison ?: result.message,
            debug = result.toDebugInfo()
        )

        return ReasoningOutput.Comparison(comparisonResult)
    }

    private fun buildComparisonPrompt(
        direct: ModeResult,
        step: ModeResult,
        prompt: ModeResult,
        expertSummary: String
    ): String = buildString {
        appendLine("Проанализируй четыре подхода к одной задаче. Отметь различия и укажи, какой ответ наиболее точный и почему.")
        appendLine()
        appendLine("Direct ответ:")
        appendLine(direct.answer)
        appendLine()
        appendLine("Step-by-step ответ:")
        appendLine(step.answer)
        appendLine()
        appendLine("Prompt from other AI ответ:")
        appendLine(prompt.answer)
        appendLine()
        appendLine("Expert panel общий вывод:")
        appendLine(expertSummary)
        appendLine()
        appendLine("Верни краткую выжимку в формате JSON: {\"comparison\": \"...\"}")
    }

    @Serializable
    private data class ComparisonPayload(
        val comparison: String
    )
}


