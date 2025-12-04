package com.prike.domain.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import com.prike.domain.koog.tools.*
import com.prike.domain.model.*
import com.prike.domain.service.CRMMCPService
import com.prike.domain.service.RagMCPService
import org.slf4j.LoggerFactory

/**
 * AI агент поддержки на основе Koog
 * 
 * Использует реальные классы Koog:
 * - ai.koog.agents.core.agent.AIAgent для создания агента
 * - ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient для работы с OpenRouter
 * - ai.koog.prompt.prompt DSL для создания промптов
 * - ai.koog.agents.core.tools.Tool для инструментов
 */
class SupportAgentKoog(
    private val crmMCPService: CRMMCPService?,
    private val ragMCPService: RagMCPService?,
    private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(SupportAgentKoog::class.java)
    
    // Создаём инструменты Koog
    private val tools = mutableListOf<ai.koog.agents.core.tools.Tool<*, *>>()
    
    init {
        // Добавляем CRM инструменты
        if (crmMCPService != null) {
            tools.add(GetUserTool(crmMCPService))
            tools.add(GetTicketTool(crmMCPService))
            tools.add(GetUserTicketsTool(crmMCPService))
            tools.add(CreateTicketTool(crmMCPService))
            tools.add(AddTicketMessageTool(crmMCPService))
        }
        
        // Добавляем RAG инструмент
        if (ragMCPService != null) {
            tools.add(SearchSupportDocsTool(ragMCPService))
        }
    }
    
    // Создаём OpenRouter executor
    private val openRouterExecutor = simpleOpenRouterExecutor(apiKey)
    
    // Создаём ToolRegistry для инструментов
    private val toolRegistry = ToolRegistry {
        tools(tools)
    }
    
    // Создаём Koog AIAgent
    private val agent = AIAgent(
        promptExecutor = openRouterExecutor,
        llmModel = run {
            val modelsClass = Class.forName("ai.koog.prompt.executor.clients.openrouter.OpenRouterModels")
            val instance = modelsClass.getDeclaredField("INSTANCE").get(null)
            val getGPT4oMiniMethod = modelsClass.getMethod("getGPT4oMini")
            getGPT4oMiniMethod.invoke(instance) as ai.koog.prompt.llm.LLModel
        },
        toolRegistry = toolRegistry,
        systemPrompt = """
            Ты — профессиональный ассистент поддержки пользователей.
            Твоя задача — помогать пользователям решать их проблемы, отвечая на вопросы на основе документации и контекста.
            
            Инструкции:
            1. ВСЕГДА используй инструмент search_support_docs для поиска информации в документации перед ответом
            2. Если указан userId, используй get_user для получения информации о пользователе
            3. Если указан ticketId, используй get_ticket для получения истории тикета
            4. Учитывай контекст пользователя (статус, подписка) при формировании ответа
            5. Если указан тикет, учитывай историю сообщений и не повторяй уже данную информацию
            6. Будь вежливым, профессиональным и полезным
            7. Если вопрос сложный или требует дальнейшего расследования, предложи создать тикет через create_ticket
            8. Всегда цитируй источники из документации
        """.trimIndent()
    )
    
    /**
     * Обрабатывает вопрос пользователя через Koog агента
     */
    suspend fun answerQuestion(request: SupportRequest): SupportResponse {
        logger.info("Processing support question with Koog: ticketId=${request.ticketId}, userId=${request.userId}, question=${request.question.take(100)}...")
        
        return try {
            // Формируем пользовательское сообщение
            val userMessage = buildUserMessage(request)
            
            // Запускаем Koog агента
            val agentResult = agent.run(userMessage)
            
            // Получаем ответ от агента (AIAgent возвращает String напрямую)
            val answer = agentResult
            
            // Извлекаем источники из ответа
            val sources = extractSources(answer)
            
            // Генерируем предложения
            val suggestions = extractSuggestions(answer)
            
            // Определяем, нужно ли создавать тикет
            val shouldCreateTicket = determineShouldCreateTicket(request, answer)
            
            SupportResponse(
                answer = answer,
                sources = sources,
                suggestions = suggestions,
                ticketId = request.ticketId,
                shouldCreateTicket = shouldCreateTicket
            )
        } catch (e: Exception) {
            logger.error("Error processing question with Koog", e)
            throw e
        }
    }
    
    /**
     * Формирует пользовательское сообщение для агента
     */
    private fun buildUserMessage(request: SupportRequest): String {
        val message = StringBuilder()
        
        message.append("Вопрос пользователя: ${request.question}\n\n")
        
        if (request.userId != null) {
            message.append("ID пользователя: ${request.userId}\n")
            message.append("ВАЖНО: Используй инструмент get_user с userId=${request.userId} для получения информации о пользователе.\n")
        }
        
        if (request.ticketId != null) {
            message.append("ID тикета: ${request.ticketId}\n")
            message.append("ВАЖНО: Используй инструмент get_ticket с ticketId=${request.ticketId} для получения истории тикета.\n")
            message.append("Это продолжение тикета. Учти предыдущие сообщения и не повторяй информацию.\n")
        }
        
        return message.toString()
    }
    
    /**
     * Извлекает источники из ответа агента
     */
    private fun extractSources(answer: String): List<Source> {
        val sources = mutableListOf<Source>()
        
        // Ищем упоминания источников в ответе
        val sourcePattern = Regex("\\[Источник[^\\]]*: ([^\\]]+)\\]", RegexOption.IGNORE_CASE)
        val matches = sourcePattern.findAll(answer)
        
        matches.forEach { match ->
            val sourceTitle = match.groupValues[1].trim()
            sources.add(Source(
                title = sourceTitle,
                content = "",
                url = null
            ))
        }
        
        return sources
    }
    
    /**
     * Извлекает предложения из ответа
     */
    private fun extractSuggestions(answer: String): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (answer.contains("восстановить пароль", ignoreCase = true)) {
            suggestions.add("Восстановить пароль")
        }
        if (answer.contains("очистить кэш", ignoreCase = true)) {
            suggestions.add("Очистить кэш браузера")
        }
        if (answer.contains("связаться с поддержкой", ignoreCase = true)) {
            suggestions.add("Связаться с поддержкой")
        }
        if (answer.contains("создать тикет", ignoreCase = true)) {
            suggestions.add("Создать новый тикет")
        }
        
        return suggestions
    }
    
    /**
     * Определяет, нужно ли создавать тикет
     */
    private fun determineShouldCreateTicket(
        request: SupportRequest,
        answer: String
    ): Boolean {
        if (request.ticketId != null) {
            return false
        }
        
        val complexKeywords = listOf("не работает", "ошибка", "проблема", "не могу", "помогите")
        val isComplex = complexKeywords.any { request.question.contains(it, ignoreCase = true) }
        val isLong = request.question.length > 100
        
        return (isComplex || isLong) || answer.contains("создать тикет", ignoreCase = true)
    }
}
