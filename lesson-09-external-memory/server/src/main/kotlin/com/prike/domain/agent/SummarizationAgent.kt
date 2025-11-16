package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.dto.UsageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.exception.AIServiceException
import com.prike.config.MemoryConfig
import kotlin.math.min

/**
 * Агент для суммаризации истории диалога
 *
 * Получает историю (включая предыдущие суммаризации) и возвращает краткую сводку.
 */
class SummarizationAgent(
    private val aiRepository: AIRepository,
    private val config: MemoryConfig.SummarizationConfig?
) {
    data class SummaryResponse(
        val summary: String,
        val usage: UsageDto?
    )

    /**
     * Суммаризировать историю сообщений.
     * История объединяется в одно пользовательское сообщение для повышения стабильности ответа.
     * В начало добавляется системная инструкция для устойчивой суммаризации.
     */
    suspend fun summarize(messages: List<MessageDto>): SummaryResponse {
        return try {
            val systemPrompt = MessageDto(
                role = "system",
                content = """
                    Ты — помощник, который делает краткие, информативные сводки диалога.
                    Требования:
                    - Сохраняй ключевые факты, решения, сущности, нотацию пользователя (имена, предпочтения).
                    - Не теряй контекст: учитывай предыдущие суммаризации как часть истории.
                    - Пиши сжато, но без потери смысла. Избегай воды.
                    - Не придумывай факты.
                """.trimIndent()
            )
            val combinedContent = buildCombinedDialog(messages)
            val combinedUser = MessageDto(
                role = "user",
                content = combinedContent
            )
            val result = aiRepository.getMessageWithHistory(
                messages = listOf(systemPrompt, combinedUser),
                options = AIRepository.ChatCompletionOptions(
                    model = config?.model,
                    temperature = config?.temperature ?: 0.2,
                    maxTokens = config?.maxTokens ?: 900
                )
            )
            val summary = result.message.trim()
            if (summary.isBlank()) {
                throw AIServiceException("Получена пустая суммаризация")
            }
            SummaryResponse(summary = summary, usage = result.usage)
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException("Ошибка при суммаризации: ${e.message}", e)
        }
    }

    /**
     * Объединить историю сообщений в один текст.
     * Сохраняем порядок и помечаем роли. SUMMARY считаем системным контекстом.
     */
    private fun buildCombinedDialog(messages: List<MessageDto>): String {
        val header = "Суммаризируй следующий диалог. Сохрани факты и решения.\n\nДиалог:\n"
        val body = messages.joinToString(separator = "\n") { msg ->
            val role = when (msg.role.lowercase()) {
                "user" -> "USER"
                "assistant" -> "ASSISTANT"
                "system" -> "SUMMARY"
                else -> msg.role.uppercase()
            }
            "[$role] ${msg.content}"
        }
        // Небольшая защита от чрезмерной длины (на случай аномалий)
        val maxLen = 32_000
        val text = (header + body)
        return if (text.length > maxLen) text.take(maxLen) else text
    }
}


