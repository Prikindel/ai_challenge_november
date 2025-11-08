package com.prike.domain.agent.reasoning

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.model.ExpertPanelEntry
import com.prike.domain.agent.model.ExpertPanelResult
import com.prike.domain.agent.model.ReasoningContext
import com.prike.domain.agent.model.ReasoningMode
import com.prike.domain.agent.model.ReasoningOutput
import kotlinx.serialization.Serializable

class ExpertPanelReasoningAgent(
    aiRepository: AIRepository
) : ReasoningStrategyAgent(aiRepository) {

    override val mode: ReasoningMode = ReasoningMode.EXPERT_PANEL

    override suspend fun execute(context: ReasoningContext): ReasoningOutput {
        val expertEntries = ReasoningPromptTemplates.experts.map { expert ->
            val messages = listOf(
                MessageDto(role = "system", content = expert.systemInstruction),
                MessageDto(
                    role = "user",
                    content = ReasoningPromptTemplates.expertUserPrompt(context.task)
                )
            )

            val result = getMessageWithHistory(messages)
            val parsed = runCatching {
                json.decodeFromString(ExpertResponse.serializer(), result.message)
            }.getOrElse {
                ExpertResponse(
                    answer = result.message,
                    reasoning = "Не удалось распарсить JSON, ответ возвращён как есть."
                )
            }

            ExpertPanelEntry(
                name = expert.name,
                style = expert.style,
                answer = parsed.answer,
                reasoning = parsed.reasoning,
                debug = result.toDebugInfo()
            )
        }

        val summaryMessages = listOf(
            MessageDto(
                role = "system",
                content = "Ты объединяешь выводы группы экспертов и формируешь общий результат. Соблюдай нейтралитет и четкость."
            ),
            MessageDto(
                role = "user",
                content = ReasoningPromptTemplates.expertSummaryUserPrompt(
                    expertEntries.map { entry ->
                        buildString {
                            appendLine("${entry.name} (${entry.style}):")
                            appendLine("Ответ: ${entry.answer}")
                            append("Обоснование: ${entry.reasoning}")
                        }
                    }
                )
            )
        )

        val summaryResult = getMessageWithHistory(summaryMessages)

        val panelResult = ExpertPanelResult(
            experts = expertEntries,
            summary = summaryResult.message,
            summaryDebug = summaryResult.toDebugInfo()
        )

        return ReasoningOutput.ExpertPanel(panelResult)
    }

    @Serializable
    private data class ExpertResponse(
        val answer: String,
        val reasoning: String
    )
}


