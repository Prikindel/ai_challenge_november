package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Агент, демонстрирующий разные стратегии рассуждения на одной логической задаче.
 */
class ReasoningAgent(
    aiRepository: AIRepository
) : BaseAgent(aiRepository) {

    enum class ReasoningMode {
        ALL,
        DIRECT,
        STEP_BY_STEP,
        PROMPT_FROM_OTHER_AI,
        EXPERT_PANEL,
        COMPARISON;

        companion object {
            fun fromString(raw: String?): ReasoningMode {
                return when (raw?.trim()?.lowercase()) {
                    "direct" -> DIRECT
                    "step", "step_by_step", "step-by-step" -> STEP_BY_STEP
                    "prompt", "prompt_from_other_ai", "prompt-from-other-ai" -> PROMPT_FROM_OTHER_AI
                    "experts", "expert_panel", "expert-panel" -> EXPERT_PANEL
                    "comparison", "compare" -> COMPARISON
                    else -> ALL
                }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private data class Expert(
        val name: String,
        val style: String,
        val systemInstruction: String
    )

    @Serializable
    private data class ExpertPanelLLMResponse(
        val answer: String,
        val reasoning: String
    )

    @Serializable
    private data class ComparisonLLMResponse(
        val comparison: String
    )

    @Serializable
    private data class GeneratedPromptResponse(
        val prompt: String,
        val overview: String? = null
    )

    private val defaultPuzzle: String =
        """
        У нас есть три друга — Анна, Борис и Виктор. Они получили три разных подарка: книгу, игру и головоломку.
        Известно, что Анна не получила игру, Борис не получил головоломку. Кто что получил?
        """.trimIndent()

    private val baseSystemPrompt =
        """
        Ты — внимательный логик-аналитик. Работай аккуратно, не выдумывай факты.
        В финале перечисли соответствия между участниками и объектами задачи в формате «Имя — предмет».
        """.trimIndent()

    private val stepByStepInstruction =
        """
        Решай задачу пошагово: перечисли ограничения, рассмотри возможные комбинации, исключи невозможные варианты и сделай вывод.
        В финале перечисли пары «Имя — предмет» на основе найденного решения.
        """.trimIndent()

    private fun buildFallbackGeneratedPrompt(puzzle: String) =
        """
        Ты — логический помощник. Вот задача:

        $puzzle

        Проанализируй ограничения, перечисли возможные варианты, последовательно исключи несоответствующие и сформулируй окончательный вывод.
        В конце перечисли соответствия «Имя — предмет» из задачи, по одному на строку.
        """.trimIndent()

    private val experts = listOf(
        Expert(
            name = "Логик",
            style = "Строго следует формальной логике, проверяет каждое утверждение и ищет единственно возможное распределение.",
            systemInstruction = "Ты — Логик. Анализируй задачу строго логическими шагами, избегай предположений без доказательств."
        ),
        Expert(
            name = "Аналитик",
            style = "Структурирует данные в таблицу ограничений, использует исключения и перебор.",
            systemInstruction = "Ты — Аналитик. Структурируй факты, работай с ограничениями и исключениями, объясняй ход рассуждений."
        ),
        Expert(
            name = "Скептик",
            style = "Проверяет потенциальные ошибки, ищет противоречия, перепроверяет выводы остальных.",
            systemInstruction = "Ты — Скептик. Сомневайся в очевидных решениях, ищи альтернативы и подтверждения, но сделай итоговый вывод."
        )
    )

    fun getDefaultTask(): String = defaultPuzzle

    suspend fun solve(
        question: String? = null,
        mode: ReasoningMode = ReasoningMode.ALL
    ): ReasoningAgentResult {
        val puzzle = question?.trim()?.takeIf { it.isNotEmpty() } ?: defaultPuzzle

        return when (mode) {
            ReasoningMode.ALL -> solveAll(puzzle)
            ReasoningMode.DIRECT -> {
                val directResult = runSimplePrompt(buildDirectPrompt(puzzle))
                ReasoningAgentResult(
                    task = puzzle,
                    mode = mode,
                    direct = directResult
                )
            }
            ReasoningMode.STEP_BY_STEP -> {
                val stepResult = runSimplePrompt(buildStepPrompt(puzzle))
                ReasoningAgentResult(
                    task = puzzle,
                    mode = mode,
                    stepByStep = stepResult
                )
            }
            ReasoningMode.PROMPT_FROM_OTHER_AI -> {
                val promptGenerationResult = runPromptGeneration(puzzle)
                val executionResult = runGeneratedPrompt(
                    promptGenerationResult.generatedPrompt,
                    promptGenerationResult.usedFallback
                )
                ReasoningAgentResult(
                    task = puzzle,
                    mode = mode,
                    promptFromOtherAI = PromptFromOtherAIResult(
                        generatedPrompt = promptGenerationResult.generatedPrompt,
                        answer = executionResult.answer,
                        notes = promptGenerationResult.note,
                        usedFallback = promptGenerationResult.usedFallback,
                        promptDebug = promptGenerationResult.debug,
                        answerDebug = executionResult.debug
                    )
                )
            }
            ReasoningMode.EXPERT_PANEL -> {
                val expertResults = runExpertPanel(puzzle)
                val summary = summarizeExpertPanel(expertResults)
                ReasoningAgentResult(
                    task = puzzle,
                    mode = mode,
                    expertPanel = ExpertPanelResult(
                        experts = expertResults,
                        summary = summary.summary,
                        summaryDebug = summary.summaryDebug
                    )
                )
            }
            ReasoningMode.COMPARISON -> {
                val allResult = solveAll(puzzle)
                ReasoningAgentResult(
                    task = puzzle,
                    mode = mode,
                    direct = allResult.direct,
                    stepByStep = allResult.stepByStep,
                    promptFromOtherAI = allResult.promptFromOtherAI,
                    expertPanel = allResult.expertPanel,
                    comparison = allResult.comparison,
                    comparisonDebug = allResult.comparisonDebug
                )
            }
        }
    }

    private suspend fun solveAll(puzzle: String): ReasoningAgentResult {
        val directResult = runSimplePrompt(buildDirectPrompt(puzzle))
        val stepResult = runSimplePrompt(buildStepPrompt(puzzle))

        val promptGenerationResult = runPromptGeneration(puzzle)
        val promptExecutionResult = runGeneratedPrompt(
            promptGenerationResult.generatedPrompt,
            promptGenerationResult.usedFallback
        )

        val expertResults = runExpertPanel(puzzle)
        val expertSummary = summarizeExpertPanel(expertResults)

        val comparison = buildComparison(
            directResult = directResult,
            stepByStepResult = stepResult,
            promptFromOtherAIResult = promptExecutionResult,
            expertSummary = expertSummary.summary
        )

        return ReasoningAgentResult(
            task = puzzle,
            mode = ReasoningMode.ALL,
            direct = directResult,
            stepByStep = stepResult,
            promptFromOtherAI = PromptFromOtherAIResult(
                generatedPrompt = promptGenerationResult.generatedPrompt,
                answer = promptExecutionResult.answer,
                notes = promptGenerationResult.note,
                usedFallback = promptGenerationResult.usedFallback,
                promptDebug = promptGenerationResult.debug,
                answerDebug = promptExecutionResult.debug
            ),
            expertPanel = ExpertPanelResult(
                experts = expertResults,
                summary = expertSummary.summary,
                summaryDebug = expertSummary.summaryDebug
            ),
            comparison = comparison.comparison,
            comparisonDebug = comparison.debug
        )
    }

    private fun buildDirectPrompt(puzzle: String): String = buildString {
        appendLine(puzzle)
        appendLine()
        append("Сделай краткое рассуждение и выведи финальный ответ в требуемом формате.")
    }

    private fun buildStepPrompt(puzzle: String): String = buildString {
        appendLine(puzzle)
        appendLine()
        append(stepByStepInstruction.trim())
    }

    private suspend fun runSimplePrompt(prompt: String): ModeResult {
        val result = getMessageWithHistory(
            listOf(
                MessageDto(role = "system", content = baseSystemPrompt),
                MessageDto(role = "user", content = prompt)
            )
        )
        return ModeResult(
            prompt = prompt,
            answer = result.message,
            debug = DebugInfo(
                llmRequest = result.requestJson,
                llmResponse = result.responseJson
            )
        )
    }

    private suspend fun runPromptGeneration(puzzle: String): PromptGenerationResult {
        val promptBuilder = getMessageWithHistory(
            listOf(
                MessageDto(
                    role = "system",
                    content = "Ты генерируешь промты для другой модели. Помоги ей решить логическую задачу корректно."
                ),
                MessageDto(
                    role = "user",
                    content = buildString {
                        appendLine("Составь оптимальный промт для решения задачи другой моделью.")
                        appendLine("Промт должен включать чёткие инструкции по анализу и формат финального ответа.")
                        appendLine()
                        appendLine(puzzle)
                        appendLine()
                        appendLine("Верни результат строго в JSON формате:")
                        appendLine("{")
                        appendLine("  \"prompt\": \"...\",")
                        appendLine("  \"overview\": \"короткое пояснение почему промт хорош\"")
                        appendLine("}")
                    }
                )
            )
        )

        val parsed = runCatching {
            json.decodeFromString<GeneratedPromptResponse>(promptBuilder.message)
        }.getOrNull()

        val rawPrompt = parsed?.prompt?.trim()
        val sanitized = sanitizeGeneratedPrompt(rawPrompt)

        val usedFallback = sanitized == null
        val finalPrompt = sanitized ?: buildFallbackGeneratedPrompt(puzzle)
        val note = when {
            usedFallback && rawPrompt.isNullOrBlank() -> "Использован резервный промт: модель вернула пустой ответ."
            usedFallback -> "Использован резервный промт: сгенерированный вариант оказался недостаточно информативным."
            else -> parsed?.overview ?: "Промт принят без изменений."
        }

        return PromptGenerationResult(
            generatedPrompt = finalPrompt,
            note = note,
            usedFallback = usedFallback,
            debug = DebugInfo(
                llmRequest = promptBuilder.requestJson,
                llmResponse = promptBuilder.responseJson
            )
        )
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

    private suspend fun runGeneratedPrompt(prompt: String, usedFallback: Boolean): ModeResult {
        val result = getMessageWithHistory(
            listOf(
                MessageDto(role = "system", content = baseSystemPrompt),
                MessageDto(
                    role = "user",
                    content = if (usedFallback) prompt else "$prompt\n\nНе забывай о требуемом формате финального ответа."
                )
            )
        )
        return ModeResult(
            prompt = prompt,
            answer = result.message,
            debug = DebugInfo(
                llmRequest = result.requestJson,
                llmResponse = result.responseJson
            )
        )
    }

    private suspend fun runExpertPanel(puzzle: String): List<ExpertResult> {
        return experts.map { expert ->
            val messages = buildList {
                add(MessageDto(role = "system", content = expert.systemInstruction))
                add(
                    MessageDto(
                        role = "user",
                        content = buildString {
                            appendLine("Реши задачу:")
                            appendLine(puzzle)
                            appendLine()
                            appendLine("Ответь в формате JSON:")
                            appendLine("{")
                            appendLine("  \"answer\": \"краткий итоговый ответ\",")
                            appendLine("  \"reasoning\": \"подробное пошаговое обоснование\"")
                            appendLine("}")
                            appendLine()
                            appendLine("Финальный ответ в поле \"answer\" перечисли в формате «Имя — предмет», по одному соответствию на строку.")
                        }
                    )
                )
            }

            val result = getMessageWithHistory(messages)
            val parsed = runCatching {
                json.decodeFromString<ExpertPanelLLMResponse>(result.message)
            }.getOrElse {
                ExpertPanelLLMResponse(
                    answer = result.message,
                    reasoning = "Не удалось распарсить JSON, ответ возвращён как есть."
                )
            }

            ExpertResult(
                name = expert.name,
                style = expert.style,
                answer = parsed.answer,
                reasoning = parsed.reasoning,
                debug = DebugInfo(
                    llmRequest = result.requestJson,
                    llmResponse = result.responseJson
                )
            )
        }
    }

    private suspend fun summarizeExpertPanel(experts: List<ExpertResult>): ExpertPanelSummary {
        val summaryMessages = listOf(
            MessageDto(
                role = "system",
                content = "Ты объединяешь выводы группы экспертов и формируешь общий результат. Соблюдай нейтралитет и четкость."
            ),
            MessageDto(
                role = "user",
                content = buildString {
                    appendLine("Дано заключение трёх экспертов по задаче. Сформируй взвешенный общий итог.")
                    appendLine("Опиши общий вывод и укажи, как объединились мнения.")
                    appendLine()
                    experts.forEach { expert ->
                        appendLine("${expert.name} (${expert.style}):")
                        appendLine("Ответ: ${expert.answer}")
                        appendLine("Обоснование: ${expert.reasoning}")
                        appendLine()
                    }
                    appendLine("Подтверди финальный ответ, перечислив соответствия «Имя — предмет».")
                }
            )
        )

        val summaryResult = getMessageWithHistory(summaryMessages)

        return ExpertPanelSummary(
            summary = summaryResult.message,
            summaryDebug = DebugInfo(
                llmRequest = summaryResult.requestJson,
                llmResponse = summaryResult.responseJson
            )
        )
    }

    private suspend fun buildComparison(
        directResult: ModeResult,
        stepByStepResult: ModeResult,
        promptFromOtherAIResult: ModeResult,
        expertSummary: String
    ): ComparisonResult {
        val comparisonMessages = listOf(
            MessageDto(
                role = "system",
                content = "Ты сравниваешь стратегии рассуждения и выделяешь различия между ответами."
            ),
            MessageDto(
                role = "user",
                content = buildString {
                    appendLine("Проанализируй четыре подхода к одной задаче. Отметь различия и укажи, какой ответ наиболее точный и почему.")
                    appendLine()
                    appendLine("Direct ответ:")
                    appendLine(directResult.answer)
                    appendLine()
                    appendLine("Step-by-step ответ:")
                    appendLine(stepByStepResult.answer)
                    appendLine()
                    appendLine("Prompt from other AI ответ:")
                    appendLine(promptFromOtherAIResult.answer)
                    appendLine()
                    appendLine("Expert panel общий вывод:")
                    appendLine(expertSummary)
                    appendLine()
                    appendLine("Верни краткую выжимку в формате JSON: {\"comparison\": \"...\"}")
                }
            )
        )

        val comparisonResult = getMessageWithHistory(comparisonMessages)
        val parsed = runCatching {
            json.decodeFromString<ComparisonLLMResponse>(comparisonResult.message)
        }.getOrElse {
            ComparisonLLMResponse(
                comparison = comparisonResult.message
            )
        }

        return ComparisonResult(
            comparison = parsed.comparison,
            debug = DebugInfo(
                llmRequest = comparisonResult.requestJson,
                llmResponse = comparisonResult.responseJson
            )
        )
    }

    data class ReasoningAgentResult(
        val task: String,
        val mode: ReasoningMode,
        val direct: ModeResult? = null,
        val stepByStep: ModeResult? = null,
        val promptFromOtherAI: PromptFromOtherAIResult? = null,
        val expertPanel: ExpertPanelResult? = null,
        val comparison: String? = null,
        val comparisonDebug: DebugInfo? = null
    )

    data class ModeResult(
        val prompt: String,
        val answer: String,
        val debug: DebugInfo
    )

    data class PromptGenerationResult(
        val generatedPrompt: String,
        val note: String,
        val usedFallback: Boolean,
        val debug: DebugInfo
    )

    data class PromptFromOtherAIResult(
        val generatedPrompt: String,
        val answer: String,
        val notes: String,
        val usedFallback: Boolean,
        val promptDebug: DebugInfo,
        val answerDebug: DebugInfo
    )

    data class ExpertResult(
        val name: String,
        val style: String,
        val answer: String,
        val reasoning: String,
        val debug: DebugInfo
    )

    data class ExpertPanelResult(
        val experts: List<ExpertResult>,
        val summary: String,
        val summaryDebug: DebugInfo
    )

    data class ExpertPanelSummary(
        val summary: String,
        val summaryDebug: DebugInfo
    )

    data class ComparisonResult(
        val comparison: String,
        val debug: DebugInfo
    )

    data class DebugInfo(
        val llmRequest: String,
        val llmResponse: String
    )
}


