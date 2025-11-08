package com.prike.domain.agent.reasoning

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.model.ModeResult
import com.prike.domain.agent.model.PromptResult
import com.prike.domain.agent.model.ReasoningContext
import com.prike.domain.agent.model.ReasoningMode
import com.prike.domain.agent.model.ReasoningOutput
import kotlinx.serialization.Serializable

class PromptFromOtherAIAgent(
    aiRepository: AIRepository
) : ReasoningStrategyAgent(aiRepository) {

    override val mode: ReasoningMode = ReasoningMode.PROMPT_FROM_OTHER_AI

    override suspend fun execute(context: ReasoningContext): ReasoningOutput {
        val promptGeneration = generatePrompt(context.task)
        val execution = runPrompt(promptGeneration.generatedPrompt, promptGeneration.usedFallback)

        val promptResult = PromptResult(
            generatedPrompt = promptGeneration.generatedPrompt,
            note = promptGeneration.note,
            usedFallback = promptGeneration.usedFallback,
            debug = promptGeneration.debug
        )

        val modeResult = ModeResult(
            prompt = promptGeneration.generatedPrompt,
            answer = execution.message,
            debug = execution.toDebugInfo()
        )

        return ReasoningOutput.PromptFromOtherAI(
            prompt = promptResult,
            answer = modeResult
        )
    }

    private suspend fun generatePrompt(task: String): PromptGenerationData {
        val messages = listOf(
            MessageDto(
                role = "system",
                content = ReasoningPromptTemplates.promptGeneratorSystem()
            ),
            MessageDto(
                role = "user",
                content = ReasoningPromptTemplates.promptGeneratorUser(task)
            )
        )

        val result = getMessageWithHistory(messages)
        val parsed = runCatching {
            json.decodeFromString(GeneratedPromptPayload.serializer(), result.message)
        }.getOrNull()

        val rawPrompt = parsed?.prompt?.trim()
        val sanitized = sanitizeGeneratedPrompt(rawPrompt)

        val usedFallback = sanitized == null
        val finalPrompt = sanitized ?: ReasoningPromptTemplates.fallbackPrompt(task)
        val note = when {
            usedFallback && rawPrompt.isNullOrBlank() ->
                "Использован резервный промт: модель вернула пустой ответ."
            usedFallback ->
                "Использован резервный промт: сгенерированный вариант оказался недостаточно информативным."
            else -> parsed?.overview ?: "Промт принят без изменений."
        }

        return PromptGenerationData(
            generatedPrompt = finalPrompt,
            note = note,
            usedFallback = usedFallback,
            debug = result.toDebugInfo()
        )
    }

    private suspend fun runPrompt(prompt: String, usedFallback: Boolean): AIRepository.MessageResult {
        val messages = listOf(
            MessageDto(role = "system", content = ReasoningPromptTemplates.BASE_SYSTEM_PROMPT.trim()),
            MessageDto(
                role = "user",
                content = ReasoningPromptTemplates.promptExecutionUser(prompt, usedFallback)
            )
        )
        return getMessageWithHistory(messages)
    }

    private fun sanitizeGeneratedPrompt(candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        val trimmed = candidate.trim()
        val hasLength = trimmed.length > 80
        val mentionsFormat = trimmed.contains("формат", ignoreCase = true) ||
            trimmed.contains("перечисли", ignoreCase = true) ||
            trimmed.contains("финал", ignoreCase = true) ||
            trimmed.contains("в конце", ignoreCase = true)
        if (!hasLength || !mentionsFormat) {
            return null
        }
        val hasDelimiter = trimmed.contains("—") || trimmed.contains("-")
        return if (hasDelimiter) trimmed else buildString {
            appendLine(trimmed.trimEnd())
            appendLine()
            appendLine("Финальный ответ представь в формате «Имя — предмет», по одному соответствию на строку.")
        }
    }

    @Serializable
    private data class GeneratedPromptPayload(
        val prompt: String,
        val overview: String? = null
    )

    private data class PromptGenerationData(
        val generatedPrompt: String,
        val note: String,
        val usedFallback: Boolean,
        val debug: com.prike.domain.agent.model.DebugInfo
    )
}


