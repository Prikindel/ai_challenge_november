package com.prike.domain.service

import com.prike.domain.model.RetrievedChunk
import org.slf4j.LoggerFactory

/**
 * Построитель промптов с контекстом из базы знаний
 */
class PromptBuilder(
    private val systemMessage: String = DEFAULT_SYSTEM_MESSAGE
) {
    private val logger = LoggerFactory.getLogger(PromptBuilder::class.java)
    
    companion object {
        private const val DEFAULT_SYSTEM_MESSAGE = 
            "Ты — помощник, который отвечает на вопросы на основе предоставленного контекста из базы знаний."
    }
    
    /**
     * Результат построения промпта
     */
    data class PromptResult(
        val systemPrompt: String,
        val userMessage: String
    )
    
    /**
     * Формирует промпт с контекстом из чанков
     * 
     * @param question вопрос пользователя
     * @param chunks список релевантных чанков
     * @return системный промпт и сообщение пользователя
     */
    fun buildPromptWithContext(
        question: String,
        chunks: List<RetrievedChunk>
    ): PromptResult {
        if (chunks.isEmpty()) {
            logger.warn("No chunks provided for context")
            return buildPromptWithoutContext(question)
        }
        
        val contextSection = buildContextSection(chunks)
        val instructions = buildInstructions()
        
        val systemPrompt = buildString {
            appendLine(systemMessage)
            appendLine()
            appendLine("Контекст из базы знаний:")
            appendLine()
            append(contextSection)
            appendLine()
            appendLine("Инструкции:")
            append(instructions)
        }
        
        val userMessage = buildString {
            appendLine("Вопрос: $question")
            appendLine()
            appendLine("Ответ:")
        }
        
        return PromptResult(
            systemPrompt = systemPrompt,
            userMessage = userMessage
        )
    }
    
    /**
     * Формирует промпт без контекста (для обычного режима)
     */
    fun buildPromptWithoutContext(question: String): PromptResult {
        val systemPrompt = systemMessage
        val userMessage = buildString {
            appendLine("Вопрос: $question")
            appendLine()
            appendLine("Ответ:")
        }
        
        return PromptResult(
            systemPrompt = systemPrompt,
            userMessage = userMessage
        )
    }
    
    /**
     * Формирует секцию контекста из чанков
     */
    private fun buildContextSection(chunks: List<RetrievedChunk>): String {
        return chunks.mapIndexed { index, chunk ->
            val documentInfo = chunk.documentPath ?: chunk.documentTitle ?: "Неизвестный документ"
            val similarityPercent = (chunk.similarity * 100).toInt()
            
            buildString {
                appendLine("[Чанк ${index + 1}] (документ: $documentInfo, сходство: ${similarityPercent}%)")
                appendLine(chunk.content.trim())
                if (index < chunks.size - 1) {
                    appendLine() // Пустая строка между чанками
                }
            }
        }.joinToString("\n")
    }
    
    /**
     * Формирует инструкции для LLM
     */
    private fun buildInstructions(): String {
        return """
            - Отвечай только на основе предоставленного контекста
            - Если в контексте нет информации для ответа, скажи об этом
            - Используй конкретные детали из контекста
            - Если информация противоречива, укажи на это
            - Указывай источники информации, когда это возможно
        """.trimIndent()
    }
}
