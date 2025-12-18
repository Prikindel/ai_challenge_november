package com.prike.domain.service

import ai.koog.agents.core.agent.AIAgent
import com.prike.data.repository.ChatRepository
import com.prike.domain.model.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Главный сервис God Agent, объединяющий все компоненты:
 * - MCP Router для работы с инструментами
 * - RAG для поиска в базе знаний
 * - Analytics для анализа данных
 * - Персонализация для адаптации под пользователя
 * 
 * TODO: Интегрировать в ChatController для замены ReviewsChatService
 * Сейчас используется ReviewsChatService, но GodAgentService предоставляет
 * более полную функциональность с роутингом запросов.
 */
class GodAgentService(
    private val mcpRouterService: MCPRouterService,
    private val knowledgeBaseService: KnowledgeBaseService,
    private val requestRouterService: RequestRouterService,
    private val chatRepository: ChatRepository,
    private val koogAgentService: KoogAgentService,
    private val userProfileService: UserProfileService,
    private val responseFormatter: ResponseFormatter? = null
) {
    private val logger = LoggerFactory.getLogger(GodAgentService::class.java)
    
    /**
     * Главный метод обработки запроса пользователя
     */
    suspend fun processUserRequest(
        message: String,
        sessionId: String,
        userId: String = "default"
    ): ChatResponse {
        logger.info("God Agent processing request: session=$sessionId, userId=$userId, message=${message.take(50)}...")
        
        // 1. Получить профиль пользователя
        val userProfile = userProfileService.getProfile(userId)
        
        // 2. Получить историю диалога
        val chatHistory = chatRepository.getHistory(sessionId)
            .map { ChatMessage(it.id, it.sessionId, it.role, it.content, it.citations, it.createdAt) }
        
        // 3. Сохранить сообщение пользователя
        chatRepository.saveMessage(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = message
        )
        
        // 4. Роутинг запроса - определить, какие инструменты использовать
        val routingDecision = requestRouterService.routeRequest(message, chatHistory)
        logger.info("Routing decision: action=${routingDecision.action}, tools=${routingDecision.tools.size}")
        
        // 5. Выполнить действия в зависимости от решения роутера
        val context = when (routingDecision.action) {
            RoutingAction.MCP_TOOLS -> {
                executeMCPTools(routingDecision.tools)
            }
            RoutingAction.RAG_SEARCH -> {
                performRAGSearch(message, routingDecision.category)
            }
            RoutingAction.ANALYTICS -> {
                performAnalytics(message, routingDecision.dataSource)
            }
            RoutingAction.DIRECT_ANSWER -> {
                null // Прямой ответ без дополнительного контекста
            }
        }
        
        // 6. Сформировать промпт с учетом контекста и персонализации
        val prompt = buildPersonalizedPrompt(
            message = message,
            context = context,
            userProfile = userProfile,
            chatHistory = chatHistory,
            routingDecision = routingDecision
        )
        
        // 7. Генерация ответа через LLM
        val response = runBlocking {
            val agent = koogAgentService.createAgent()
            try {
                agent.run(prompt)
            } finally {
                agent.close()
            }
        }
        
        logger.debug("LLM response generated: ${response.length} chars")
        
        // 8. Форматирование ответа с учетом профиля пользователя
        val formattedResponse = responseFormatter?.formatResponse(response, userId) ?: response
        
        // 9. Извлечение источников из контекста
        val sources = extractSources(context, routingDecision)
        
        // 10. Сохранение ответа в историю
        val assistantMessage = chatRepository.saveMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = formattedResponse,
            citations = sources
        )
        
        return ChatResponse(
            message = assistantMessage,
            sources = sources,
            toolsUsed = routingDecision.tools.map { "${it.server}:${it.tool}" },
            routingAction = routingDecision.action.name
        )
    }
    
    /**
     * Выполнить MCP инструменты
     */
    private suspend fun executeMCPTools(tools: List<ToolCall>): String {
        if (tools.isEmpty()) {
            return ""
        }
        
        val results = tools.map { tool ->
            try {
                val result = mcpRouterService.executeTool(
                    serverName = tool.server,
                    toolName = tool.tool,
                    arguments = tool.args
                )
                
                if (result.success) {
                    "✅ ${tool.server}:${tool.tool} - ${result.data}"
                } else {
                    "❌ ${tool.server}:${tool.tool} - Ошибка: ${result.error}"
                }
            } catch (e: Exception) {
                logger.error("Failed to execute tool ${tool.server}:${tool.tool}: ${e.message}", e)
                "❌ ${tool.server}:${tool.tool} - Ошибка: ${e.message}"
            }
        }
        
        return results.joinToString("\n\n")
    }
    
    /**
     * Выполнить поиск в базе знаний (RAG)
     */
    private suspend fun performRAGSearch(
        query: String,
        category: String?
    ): String {
        return try {
            val chunks = knowledgeBaseService.searchInCategory(query, category, limit = 5)
            
            if (chunks.isEmpty()) {
                "В базе знаний не найдено релевантной информации по запросу."
            } else {
                buildString {
                    appendLine("Найдено ${chunks.size} релевантных фрагментов в базе знаний:")
                    appendLine()
                    chunks.forEachIndexed { index, chunk ->
                        appendLine("[${index + 1}] ${chunk.source} (сходство: ${String.format("%.2f", chunk.similarity)})")
                        appendLine("Категория: ${chunk.category.displayName}")
                        appendLine("Текст: ${chunk.text.take(200)}...")
                        appendLine()
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to perform RAG search: ${e.message}", e)
            "Ошибка при поиске в базе знаний: ${e.message}"
        }
    }
    
    /**
     * Выполнить аналитику данных
     */
    private suspend fun performAnalytics(
        query: String,
        dataSource: String?
    ): String {
        // Используем Analytics MCP сервер
        return try {
                // Используем Analytics MCP сервер через правильное имя сервера
                val analyticsServerName = "Analytics MCP"
                val result = mcpRouterService.executeTool(
                    serverName = analyticsServerName,
                    toolName = "get_data_summary",
                    arguments = mapOf(
                        "data_source" to (dataSource ?: "data/analytics")
                    )
                )
            
            if (result.success) {
                "Аналитика данных:\n${result.data}"
            } else {
                "Ошибка при анализе данных: ${result.error}"
            }
        } catch (e: Exception) {
            logger.error("Failed to perform analytics: ${e.message}", e)
            "Ошибка при выполнении аналитики: ${e.message}"
        }
    }
    
    /**
     * Построить персонализированный промпт
     */
    private fun buildPersonalizedPrompt(
        message: String,
        context: String?,
        userProfile: com.prike.domain.model.UserProfile,
        chatHistory: List<ChatMessage>,
        routingDecision: RoutingDecision
    ): String {
        return buildString {
            appendLine("Ты — персональный AI-помощник пользователя ${userProfile.name}.")
            appendLine()
            
            // Профиль пользователя
            appendLine("Профиль пользователя:")
            appendLine("- Стиль общения: ${userProfile.communicationStyle.tone.name.lowercase()}")
            appendLine("- Уровень детализации: ${userProfile.communicationStyle.detailLevel.name.lowercase()}")
            if (userProfile.preferences.language.isNotEmpty()) {
                appendLine("- Язык: ${userProfile.preferences.language}")
            }
            if (userProfile.context.currentProject != null) {
                appendLine("- Текущий проект: ${userProfile.context.currentProject}")
            }
            if (userProfile.context.role != null) {
                appendLine("- Роль: ${userProfile.context.role}")
            }
            appendLine()
            
            // Контекст из инструментов/RAG
            if (context != null && context.isNotBlank()) {
                appendLine("=== РЕЗУЛЬТАТЫ ВЫПОЛНЕНИЯ ИНСТРУМЕНТОВ ===")
                appendLine("ВАЖНО: Используй эти результаты для ответа пользователю. Не говори, что не можешь выполнить действие - инструменты уже выполнены!")
                appendLine()
                appendLine(context)
                appendLine()
                appendLine("=== КОНЕЦ РЕЗУЛЬТАТОВ ===")
                appendLine()
            }
            
            // Использованные инструменты
            if (routingDecision.tools.isNotEmpty()) {
                appendLine("Использованные инструменты:")
                routingDecision.tools.forEach { tool ->
                    appendLine("- ${tool.server}:${tool.tool}")
                }
                appendLine()
                if (context != null && context.isNotBlank()) {
                    appendLine("ИНСТРУКЦИЯ: Если инструменты были выполнены успешно (✅), используй их результаты для ответа пользователю. Если были ошибки (❌), объясни проблему.")
                    appendLine()
                }
            }
            
            // История диалога (последние 5 сообщений)
            if (chatHistory.isNotEmpty()) {
                appendLine("История диалога (последние сообщения):")
                chatHistory.takeLast(5).forEach { msg ->
                    appendLine("${msg.role.name}: ${msg.content.take(200)}")
                }
                appendLine()
            }
            
            // Запрос пользователя
            appendLine("Запрос пользователя: $message")
            appendLine()
            
            // Инструкции для LLM
            if (context != null && context.isNotBlank()) {
                appendLine("КРИТИЧЕСКИ ВАЖНО:")
                appendLine("1. Инструменты УЖЕ ВЫПОЛНЕНЫ, результаты находятся выше в разделе 'РЕЗУЛЬТАТЫ ВЫПОЛНЕНИЯ ИНСТРУМЕНТОВ'")
                appendLine("2. НЕ говори, что не можешь выполнить действие - инструменты уже выполнены!")
                appendLine("3. Используй результаты инструментов для ответа пользователю")
                appendLine("4. Если результат начинается с ✅ - инструмент выполнен успешно, используй данные")
                appendLine("5. Если результат начинается с ❌ - была ошибка, объясни проблему пользователю")
                appendLine()
            }
            
            appendLine("Ответь на запрос, учитывая профиль пользователя, контекст и историю диалога.")
        }
    }
    
    /**
     * Извлечь источники из контекста
     */
    private fun extractSources(
        context: String?,
        routingDecision: RoutingDecision
    ): List<Citation> {
        val sources = mutableListOf<Citation>()
        
        // Источники из RAG
        if (routingDecision.action == RoutingAction.RAG_SEARCH && context != null) {
            // Парсим источники из контекста RAG
            val sourcePattern = Regex("\\[\\d+\\] ([^\\s]+)")
            sourcePattern.findAll(context).forEach { match ->
                val source = match.groupValues[1]
                sources.add(
                    Citation(
                        text = context.substring(match.range),
                        documentPath = source,
                        documentTitle = source.substringAfterLast("/"),
                        chunkId = null
                    )
                )
            }
        }
        
        // Источники из MCP инструментов
        routingDecision.tools.forEach { tool ->
            sources.add(
                Citation(
                    text = "Результат выполнения ${tool.server}:${tool.tool}",
                    documentPath = "mcp://${tool.server}/${tool.tool}",
                    documentTitle = "${tool.server} - ${tool.tool}",
                    chunkId = null
                )
            )
        }
        
        return sources
    }
}

/**
 * Ответ God Agent
 */
data class ChatResponse(
    val message: ChatMessage,
    val sources: List<Citation>,
    val toolsUsed: List<String>,
    val routingAction: String
)

