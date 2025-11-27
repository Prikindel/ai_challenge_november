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
        val documentsList = buildDocumentsList(chunks)
        val instructions = buildInstructions()
        
        val systemPrompt = buildString {
            appendLine(systemMessage)
            appendLine()
            appendLine("Доступные документы:")
            appendLine(documentsList)
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
            val documentPath = chunk.documentPath ?: "unknown.md"
            val documentTitle = chunk.documentTitle ?: documentPath
            val similarityPercent = (chunk.similarity * 100).toInt()
            
            buildString {
                appendLine("[Чанк ${index + 1}] (документ: $documentTitle, путь: $documentPath, сходство: ${similarityPercent}%)")
                appendLine(chunk.content.trim())
                if (index < chunks.size - 1) {
                    appendLine() // Пустая строка между чанками
                }
            }
        }.joinToString("\n")
    }
    
    /**
     * Формирует список доступных документов
     */
    private fun buildDocumentsList(chunks: List<RetrievedChunk>): String {
        val uniqueDocuments = chunks
            .mapNotNull { chunk ->
                val path = chunk.documentPath ?: return@mapNotNull null
                val title = chunk.documentTitle ?: path
                path to title
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
        
        if (uniqueDocuments.isEmpty()) {
            return "Нет доступных документов"
        }
        
        return uniqueDocuments.joinToString("\n") { (path, title) ->
            "- $title → путь: $path"
        }
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
            
            КРИТИЧЕСКИ ВАЖНО - ОБЯЗАТЕЛЬНЫЕ ЦИТАТЫ:
            - Ты ОБЯЗАН указывать источники информации в каждом ответе
            - Каждое утверждение, основанное на контексте, должно иметь ссылку на источник
            - Используй формат Markdown для цитат: [Источник: название_документа](путь_к_документу)
            - Название документа и путь к документу указывай точно из контекста выше
            - Пример: [Источник: Создание MCP сервера](documents/01-mcp-server-creation.md)
            - Если используешь информацию из нескольких документов, укажи все источники
            - Минимум 2 цитаты в ответе, если контекст содержит достаточно информации
            - Цитаты должны быть расположены рядом с соответствующими утверждениями
        """.trimIndent()
    }
}
