package com.prike.domain.service

import com.prike.domain.model.ChatMessage
import com.prike.domain.model.RoutingAction
import com.prike.domain.model.RoutingDecision
import com.prike.domain.model.ToolCall
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис для роутинга запросов пользователя
 */
class RequestRouterService(
    private val mcpRouterService: MCPRouterService,
    private val llmService: LLMRouterService
) {
    private val logger = LoggerFactory.getLogger(RequestRouterService::class.java)
    
    /**
     * Определить, какие инструменты использовать для запроса
     */
    suspend fun routeRequest(
        userQuery: String,
        chatHistory: List<ChatMessage>
    ): RoutingDecision {
        // Получить список доступных инструментов
        val availableTools = mcpRouterService.getAllAvailableTools()
        val toolsDescription = mcpRouterService.getToolsDescription()
        
        // Использовать LLM для принятия решения о роутинге
        val decision = llmService.decideRouting(
            query = userQuery,
            toolsDescription = toolsDescription,
            availableTools = availableTools,
            chatHistory = chatHistory
        )
        
        logger.debug("Routing decision: action=${decision.action}, tools=${decision.tools.size}, reasoning=${decision.reasoning}")
        
        return decision
    }
}

/**
 * Сервис для использования LLM в роутинге
 * Пока упрощенная версия, в будущем можно интегрировать с реальным LLM
 */
class LLMRouterService {
    private val logger = LoggerFactory.getLogger(LLMRouterService::class.java)
    
    /**
     * Принять решение о роутинге на основе запроса пользователя
     */
    suspend fun decideRouting(
        query: String,
        toolsDescription: String,
        availableTools: List<com.prike.domain.model.MCPTool>,
        chatHistory: List<ChatMessage>
    ): RoutingDecision {
        // Упрощенная логика роутинга на основе ключевых слов
        // В будущем можно заменить на реальный LLM вызов
        
        val queryLower = query.lowercase()
        
        // Проверка на запросы к базе знаний
        val ragKeywords = listOf("найди", "найти", "поиск", "информация", "документ", "заметка", "проект")
        if (ragKeywords.any { queryLower.contains(it) }) {
            return RoutingDecision(
                action = RoutingAction.RAG_SEARCH,
                reasoning = "Запрос требует поиска в базе знаний"
            )
        }
        
        // Проверка на аналитические запросы
        val analyticsKeywords = listOf("анализ", "анализировать", "метрики", "статистика", "данные", "отчет")
        if (analyticsKeywords.any { queryLower.contains(it) }) {
            return RoutingDecision(
                action = RoutingAction.ANALYTICS,
                reasoning = "Запрос требует анализа данных"
            )
        }
        
        // Проверка на использование MCP инструментов
        val mcpKeywords = mapOf(
            "git" to "git",
            "telegram" to "telegram",
            "отправить" to "telegram",
            "сообщение" to "telegram",
            "файл" to "filesystem",
            "прочитать" to "filesystem"
        )
        
        val matchingTools = mutableListOf<ToolCall>()
        mcpKeywords.forEach { (keyword, serverName) ->
            if (queryLower.contains(keyword)) {
                // Найти подходящий инструмент в доступных
                val tool = availableTools.find { 
                    it.serverName.lowercase() == serverName.lowercase() 
                }
                if (tool != null) {
                    matchingTools.add(
                        ToolCall(
                            server = tool.serverName,
                            tool = tool.name,
                            args = extractArguments(query, tool)
                        )
                    )
                }
            }
        }
        
        if (matchingTools.isNotEmpty()) {
            return RoutingDecision(
                action = RoutingAction.MCP_TOOLS,
                tools = matchingTools,
                reasoning = "Найдены подходящие MCP инструменты"
            )
        }
        
        // По умолчанию - прямой ответ
        return RoutingDecision(
            action = RoutingAction.DIRECT_ANSWER,
            reasoning = "Запрос не требует использования инструментов"
        )
    }
    
    /**
     * Извлечь аргументы из запроса для инструмента
     */
    private fun extractArguments(query: String, tool: com.prike.domain.model.MCPTool): Map<String, Any> {
        val args = mutableMapOf<String, Any>()
        
        // Простая логика извлечения аргументов
        // В будущем можно улучшить с помощью LLM
        
        when (tool.name.lowercase()) {
            "send_telegram_message" -> {
                // Извлечь сообщение из запроса
                val messageStart = query.indexOf("отправить") + 9
                val message = query.substring(messageStart).trim()
                args["message"] = message
                args["userId"] = "default" // Можно извлечь из контекста
            }
            "read_file" -> {
                // Извлечь путь к файлу
                val pathStart = query.indexOf("файл") + 4
                val path = query.substring(pathStart).trim()
                args["path"] = path
            }
        }
        
        return args
    }
}

