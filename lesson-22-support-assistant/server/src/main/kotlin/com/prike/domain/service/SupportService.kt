package com.prike.domain.service

import com.prike.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис для обработки вопросов пользователей поддержки
 */
class SupportService(
    private val crmMCPService: CRMMCPService?,
    private val ragMCPService: RagMCPService?,
    private val llmService: LLMService
) {
    private val logger = LoggerFactory.getLogger(SupportService::class.java)
    
    /**
     * Ответить на вопрос пользователя
     */
    suspend fun answerQuestion(request: SupportRequest): SupportResponse {
        logger.info("Processing support question: ticketId=${request.ticketId}, userId=${request.userId}, question=${request.question.take(100)}...")
        
        // 1. Получаем контекст тикета и пользователя
        val context = getSupportContext(request.userId, request.ticketId)
        
        // 2. Ищем ответы в документации через RAG
        val ragResults = searchInDocs(request.question)
        
        // 3. Формируем RAG-контекст
        val ragContext = ragResults.joinToString("\n\n") { it.content }
        
        // 4. Обновляем контекст с RAG-данными
        val fullContext = context.copy(ragContext = ragContext)
        
        // 5. Генерируем ответ через LLM
        val answer = generateAnswer(request.question, fullContext)
        
        // 6. Формируем источники
        val sources = ragResults.map { result ->
            Source(
                title = result.title ?: "Документация поддержки",
                content = result.content,
                url = result.url
            )
        }
        
        // 7. Генерируем предложения (если нужно)
        val suggestions = generateSuggestions(request.question, fullContext)
        
        return SupportResponse(
            answer = answer,
            sources = sources,
            suggestions = suggestions,
            ticketId = request.ticketId
        )
    }
    
    /**
     * Получить контекст поддержки (пользователь, тикет, история)
     */
    suspend fun getSupportContext(userId: String?, ticketId: String?): SupportContext {
        val user = if (userId != null && crmMCPService != null) {
            crmMCPService.getUser(userId)
        } else {
            null
        }
        
        val ticket = if (ticketId != null && crmMCPService != null) {
            crmMCPService.getTicket(ticketId)
        } else {
            null
        }
        
        val userTickets = if (userId != null && crmMCPService != null) {
            crmMCPService.getUserTickets(userId)
        } else {
            null
        }
        
        return SupportContext(
            user = user,
            ticket = ticket,
            userTickets = userTickets
        )
    }
    
    /**
     * Поиск в документации через RAG
     */
    suspend fun searchInDocs(question: String): List<RagSearchResult> {
        if (ragMCPService == null) {
            logger.warn("RAG MCP service is not available")
            return emptyList()
        }
        
        return try {
            val arguments = buildJsonObject {
                put("query", question)
                put("topK", 5)
                put("filter", buildJsonObject {
                    put("path", "project/docs/support/")
                })
            }
            
            val result = ragMCPService.callTool("rag_search_project_docs", arguments)
            parseRagResults(result)
        } catch (e: Exception) {
            logger.error("Failed to search in docs: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Генерация ответа через LLM
     */
    private suspend fun generateAnswer(question: String, context: SupportContext): String {
        // Формируем промпт с контекстом
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(question, context)
        
        return try {
            val response = llmService.generateAnswer(
                question = userPrompt,
                systemPrompt = systemPrompt,
                temperature = 0.7
            )
            response.answer
        } catch (e: Exception) {
            logger.error("Failed to generate answer: ${e.message}", e)
            "Извините, произошла ошибка при генерации ответа. Пожалуйста, попробуйте позже или свяжитесь с поддержкой."
        }
    }
    
    /**
     * Генерация предложений
     */
    private fun generateSuggestions(question: String, context: SupportContext): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Если вопрос про авторизацию
        if (question.contains("авторизац", ignoreCase = true) || 
            question.contains("войти", ignoreCase = true) ||
            question.contains("пароль", ignoreCase = true)) {
            suggestions.add("Восстановить пароль")
            suggestions.add("Очистить кэш браузера")
        }
        
        // Если вопрос про подписку
        if (question.contains("подписк", ignoreCase = true) ||
            question.contains("оплат", ignoreCase = true)) {
            suggestions.add("Управление подпиской")
            suggestions.add("История платежей")
        }
        
        // Если тикет не указан, предлагаем создать
        if (context.ticket == null && question.length > 50) {
            suggestions.add("Создать тикет для детального разбора")
        }
        
        return suggestions
    }
    
    /**
     * Построение системного промпта
     */
    private fun buildSystemPrompt(): String {
        return """
            Ты — опытный агент поддержки, который помогает пользователям решать проблемы.
            
            Твоя задача:
            - Отвечать на вопросы пользователей о продукте
            - Использовать документацию и FAQ для точных ответов
            - Учитывать контекст тикета (история обращений, статус пользователя)
            - Предлагать конкретные шаги для решения проблемы
            - Быть вежливым и профессиональным
            
            Отвечай на русском языке, кратко и по делу.
        """.trimIndent()
    }
    
    /**
     * Построение пользовательского промпта
     */
    private fun buildUserPrompt(question: String, context: SupportContext): String {
        val prompt = StringBuilder()
        
        prompt.append("Вопрос пользователя: $question\n\n")
        
        // Контекст пользователя
        if (context.user != null) {
            prompt.append("Контекст пользователя:\n")
            prompt.append("- Имя: ${context.user.name ?: "Не указано"}\n")
            prompt.append("- Email: ${context.user.email}\n")
            prompt.append("- Статус аккаунта: ${context.user.status}\n")
            if (context.user.subscription != null) {
                prompt.append("- Подписка: ${context.user.subscription.plan}\n")
            }
            prompt.append("\n")
        }
        
        // Контекст тикета
        if (context.ticket != null) {
            prompt.append("Контекст тикета:\n")
            prompt.append("- Тема: ${context.ticket.subject}\n")
            prompt.append("- Статус: ${context.ticket.status}\n")
            prompt.append("- Приоритет: ${context.ticket.priority}\n")
            if (context.ticket.messages.isNotEmpty()) {
                prompt.append("- История сообщений:\n")
                context.ticket.messages.takeLast(5).forEach { message ->
                    prompt.append("  [${message.author}]: ${message.content.take(200)}\n")
                }
            }
            prompt.append("\n")
        }
        
        // RAG-контекст
        if (context.ragContext != null && context.ragContext.isNotBlank()) {
            prompt.append("Информация из документации:\n")
            prompt.append(context.ragContext)
            prompt.append("\n\n")
        }
        
        prompt.append("Ответь на вопрос пользователя, учитывая контекст тикета и информацию из документации.")
        
        return prompt.toString()
    }
    
    /**
     * Парсинг результатов RAG поиска
     */
    private fun parseRagResults(json: String): List<RagSearchResult> {
        return try {
            val obj = Json.parseToJsonElement(json) as? JsonObject ?: return emptyList()
            val chunks = obj["chunks"]?.jsonArray ?: return emptyList()
            
            chunks.mapNotNull { chunkElement ->
                if (chunkElement is JsonObject) {
                    try {
                        RagSearchResult(
                            title = chunkElement["title"]?.jsonPrimitive?.content,
                            content = chunkElement["content"]?.jsonPrimitive?.content ?: "",
                            url = chunkElement["url"]?.jsonPrimitive?.content,
                            similarity = chunkElement["similarity"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to parse RAG result: ${e.message}")
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse RAG results: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Результат поиска в RAG
     */
    data class RagSearchResult(
        val title: String?,
        val content: String,
        val url: String?,
        val similarity: Double
    )
}

