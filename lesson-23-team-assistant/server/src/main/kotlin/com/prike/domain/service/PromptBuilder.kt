package com.prike.domain.service

import com.prike.domain.model.RetrievedChunk
import com.prike.domain.model.ChatMessage
import com.prike.domain.model.MessageRole
import org.slf4j.LoggerFactory
import kotlin.text.buildString

/**
 * Построитель промптов с контекстом из базы знаний
 */
class PromptBuilder(
    private val systemMessage: String = DEFAULT_SYSTEM_MESSAGE
) {
    private val logger = LoggerFactory.getLogger(PromptBuilder::class.java)
    
    companion object {
        private const val DEFAULT_SYSTEM_MESSAGE = 
            """Ты — ассистент разработчика, который помогает разработчикам работать с проектом.
            
Твои возможности:
- Поиск информации в документации проекта через RAG (Retrieval-Augmented Generation)
- Доступ к текущей ветке git репозитория через MCP (Model Context Protocol)
- Использование истории диалога для контекстуальных ответов
- Ответы на вопросы о структуре проекта, API, схеме данных и правилах стиля

Твоя задача:
- Отвечать на вопросы о проекте на основе документации
- Подсказывать фрагменты кода и правила стиля
- Объяснять архитектуру и структуру проекта
- Помогать разработчикам понимать, как работает проект"""
    }
    
    /**
     * Результат построения промпта
     */
    data class PromptResult(
        val systemPrompt: String,
        val userMessage: String
    )
    
    /**
     * Результат построения промпта для чата (с массивом messages)
     */
    data class ChatPromptResult(
        val messages: List<com.prike.mcpcommon.dto.MessageDto>
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
     * Формирует промпт для чата с историей диалога и контекстом из RAG
     * 
     * @param question текущий вопрос пользователя
     * @param history история диалога (последние N сообщений)
     * @param chunks релевантные чанки из RAG-поиска
     * @param gitBranch текущая ветка git (опционально)
     * @return массив messages для отправки в LLM
     */
    fun buildChatPrompt(
        question: String,
        history: List<ChatMessage> = emptyList(),
        chunks: List<RetrievedChunk> = emptyList(),
        gitBranch: String? = null
    ): ChatPromptResult {
        val contextSection = if (chunks.isNotEmpty()) {
            buildContextSection(chunks)
        } else {
            null
        }
        
        val documentsList = if (chunks.isNotEmpty()) {
            buildDocumentsList(chunks)
        } else {
            null
        }
        
        val messages = mutableListOf<com.prike.mcpcommon.dto.MessageDto>()
        
        // Формируем системный промпт (только инструкции и контекст из RAG, БЕЗ истории)
        val systemPrompt = buildString {
            appendLine(systemMessage)
            appendLine()
            
            // Добавляем информацию о git-ветке, если доступна
            if (gitBranch != null && gitBranch != "unknown") {
                appendLine("📌 Информация о репозитории:")
                appendLine("Текущая ветка git: $gitBranch")
                appendLine()
            }
            
            // Описание доступных ресурсов
            appendLine("📚 Доступные ресурсы:")
            if (documentsList != null) {
                appendLine("Документация проекта:")
                appendLine(documentsList)
            } else {
                appendLine("Документация проекта: не найдена")
            }
            if (history.isNotEmpty()) {
                appendLine("История диалога: доступна (${history.size} сообщений)")
            }
                appendLine()
            
            if (contextSection != null) {
                appendLine("🔍 Контекст из базы знаний:")
                appendLine()
                append(contextSection)
                appendLine()
            }
            
            appendLine("📋 Инструкции:")
            if (chunks.isNotEmpty()) {
                // Если есть контекст из RAG, требуем цитаты
                append(buildInstructions())
            } else {
                // Если контекста нет, но есть история - отвечаем с учетом истории, но без цитат
                if (history.isNotEmpty()) {
                    append(buildInstructionsWithHistoryButNoContext())
                } else {
                    // Если контекста и истории нет, просто отвечаем без требования цитат
                    append(buildInstructionsWithoutCitations())
                }
            }
        }
        
        // Добавляем системный промпт
        messages.add(com.prike.mcpcommon.dto.MessageDto(role = "system", content = systemPrompt))
        
        // Добавляем историю диалога в формате messages (role: user, role: assistant, ...)
        history.forEach { message ->
            val role = when (message.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
            }
            // Не включаем цитаты в историю - они уже были обработаны ранее
            messages.add(com.prike.mcpcommon.dto.MessageDto(role = role, content = message.content))
        }
        
        // Добавляем текущий вопрос пользователя
        messages.add(com.prike.mcpcommon.dto.MessageDto(role = "user", content = question))
        
        return ChatPromptResult(messages = messages)
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
     * Формирует инструкции для LLM (с требованием цитат)
     */
    private fun buildInstructions(): String {
        return """
            - Отвечай только на основе предоставленного контекста из документации проекта
            - Если в контексте нет информации для ответа, скажи об этом честно
            - Используй конкретные детали из контекста (код, примеры, схемы)
            - Если информация противоречива, укажи на это
            - Помогай разработчикам понимать структуру проекта, API, схемы данных
            - Подсказывай фрагменты кода и правила стиля из документации
            
            КРИТИЧЕСКИ ВАЖНО - ОБЯЗАТЕЛЬНЫЕ ЦИТАТЫ:
            - Ты ОБЯЗАН указывать источники информации в каждом ответе
            - Каждое утверждение, основанное на контексте, должно иметь ссылку на источник
            - Используй формат Markdown для цитат: [Источник: название_документа](путь_к_документу)
            - Название документа и путь к документу указывай точно из контекста выше
            - Пример: [Источник: API Документация](project/docs/api.md)
            - Если используешь информацию из нескольких документов, укажи все источники
            - Минимум 2 цитаты в ответе, если контекст содержит достаточно информации
            - Цитаты должны быть расположены рядом с соответствующими утверждениями
            - Без цитат ответ считается неполным и неинформативным
        """.trimIndent()
    }
    
    /**
     * Формирует инструкции для LLM (без требования цитат, когда нет контекста из RAG)
     */
    private fun buildInstructionsWithoutCitations(): String {
        return """
            - Отвечай на вопрос пользователя на основе своих знаний
            - Если вопрос не связан с документацией проекта, отвечай как ассистент разработчика
            - Будь полезным и информативным
            - Если не знаешь ответа, честно скажи об этом
            - Рекомендуй использовать команду /help для вопросов о проекте
            - НЕ добавляй цитаты, если контекст из базы знаний не предоставлен
        """.trimIndent()
    }
    
    /**
     * Формирует инструкции для LLM (когда есть история, но нет контекста из RAG)
     */
    private fun buildInstructionsWithHistoryButNoContext(): String {
        return """
            - Отвечай на вопрос пользователя с учетом истории диалога выше
            - Используй информацию из истории диалога для понимания контекста вопроса
            - Если вопрос не связан с документацией проекта, отвечай на основе своих знаний и истории диалога
            - Будь полезным и информативным
            - Если не знаешь ответа, честно скажи об этом
            - Рекомендуй использовать команду /help для вопросов о проекте
            - НЕ добавляй цитаты, если контекст из базы знаний не предоставлен
        """.trimIndent()
    }
}
