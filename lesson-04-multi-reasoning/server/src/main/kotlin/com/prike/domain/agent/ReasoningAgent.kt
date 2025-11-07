package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Агент, демонстрирующий разные стратегии рассуждения на одной логической задаче.
 */
class ReasoningAgent(
    aiRepository: AIRepository
) : BaseAgent(aiRepository) {

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

    private val puzzle: String =
        """
        У нас есть три друга — Анна, Борис и Виктор. Они получили три разных подарка: книгу, игру и головоломку.
        Известно, что Анна не получила игру, Борис не получил головоломку. Кто что получил?
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

    suspend fun solve(): ReasoningAgentResult {
        val directPrompt = puzzle
        val directResult = runSimplePrompt(directPrompt)

        val stepPrompt = buildString {
            appendLine(puzzle)
            appendLine()
            append("Пожалуйста, решай пошагово, явно перечисляя рассуждения и финальный ответ.")
        }
        val stepByStepResult = runSimplePrompt(stepPrompt)

        val promptGenerationResult = runPromptGeneration()
        val promptFromOtherAIResult = runGeneratedPrompt(promptGenerationResult.generatedPrompt)

        val expertResults = runExpertPanel()
        val expertSummary = summarizeExpertPanel(expertResults)

        val comparison = buildComparison(
            directResult = directResult,
            stepByStepResult = stepByStepResult,
            promptFromOtherAIResult = promptFromOtherAIResult,
            expertSummary = expertSummary.summary
        )

        return ReasoningAgentResult(
            task = puzzle,
            direct = directResult,
            stepByStep = stepByStepResult,
            promptFromOtherAI = PromptFromOtherAIResult(
                generatedPrompt = promptGenerationResult.generatedPrompt,
                answer = promptFromOtherAIResult.answer,
                promptDebug = promptGenerationResult.debug,
                answerDebug = promptFromOtherAIResult.debug
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

    private suspend fun runSimplePrompt(prompt: String): ModeResult {
        val result = getMessageWithHistory(
            listOf(
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

    private suspend fun runPromptGeneration(): PromptGenerationResult {
        val promptBuilder = getMessageWithHistory(
            listOf(
                MessageDto(
                    role = "system",
                    content = "Ты помогаешь другому ИИ решать логические задачи. Подготовь чёткий и полный промт."
                ),
                MessageDto(
                    role = "user",
                    content = buildString {
                        appendLine("Составь лучший промт для решения следующей задачи.")
                        appendLine()
                        appendLine(puzzle)
                        appendLine()
                        append("Верни только сам промт без лишнего текста.")
                    }
                )
            )
        )

        return PromptGenerationResult(
            generatedPrompt = promptBuilder.message,
            debug = DebugInfo(
                llmRequest = promptBuilder.requestJson,
                llmResponse = promptBuilder.responseJson
            )
        )
    }

    private suspend fun runGeneratedPrompt(prompt: String): ModeResult {
        val result = getMessageWithHistory(
            listOf(
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

    private suspend fun runExpertPanel(): List<ExpertResult> {
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
                content = "Ты объединяешь выводы группы экспертов и формируешь общий результат."
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
        val direct: ModeResult,
        val stepByStep: ModeResult,
        val promptFromOtherAI: PromptFromOtherAIResult,
        val expertPanel: ExpertPanelResult,
        val comparison: String,
        val comparisonDebug: DebugInfo
    )

    data class ModeResult(
        val prompt: String,
        val answer: String,
        val debug: DebugInfo
    )

    data class PromptGenerationResult(
        val generatedPrompt: String,
        val debug: DebugInfo
    )

    data class PromptFromOtherAIResult(
        val generatedPrompt: String,
        val answer: String,
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


