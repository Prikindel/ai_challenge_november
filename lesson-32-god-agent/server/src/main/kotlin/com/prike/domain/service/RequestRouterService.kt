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
        val ragKeywords = listOf("найди", "найти", "поиск", "информация", "документ", "заметка", "проект", "база знаний")
        if (ragKeywords.any { queryLower.contains(it) }) {
            val category = extractCategory(query)
            return RoutingDecision(
                action = RoutingAction.RAG_SEARCH,
                category = category,
                reasoning = "Запрос требует поиска в базе знаний${if (category != null) " (категория: $category)" else ""}"
            )
        }
        
        // Проверка на Git запросы
        val gitKeywords = listOf("git", "репозиторий", "коммит", "ветка", "статус", "git status", "git log", "git diff", "git branch")
        if (gitKeywords.any { queryLower.contains(it) }) {
            // Пытаемся извлечь имя репозитория из запроса, если не указано - используем значение по умолчанию
            val repoName = extractRepositoryName(query) 
                ?: extractRepositoryFromQuery(query)
                ?: "AI Challenge November"  // Значение по умолчанию из конфигурации
            
            val toolName = when {
                queryLower.contains("статус") || queryLower.contains("status") -> "git_status"
                queryLower.contains("коммит") || queryLower.contains("log") || queryLower.contains("история") -> "git_log"
                queryLower.contains("ветка") || queryLower.contains("branch") -> "git_branch"
                queryLower.contains("изменен") || queryLower.contains("diff") -> "git_diff"
                queryLower.contains("прочитай") || queryLower.contains("read") -> "git_read_file"
                else -> "git_status"
            }
            return RoutingDecision(
                action = RoutingAction.MCP_TOOLS,
                tools = listOf(
                    ToolCall(
                        server = "Git MCP",
                        tool = toolName,
                        args = mapOf("repository" to repoName)
                    )
                ),
                reasoning = "Запрос требует работы с Git репозиторием"
            )
        }
        
        // Проверка на Telegram запросы
        val telegramKeywords = listOf("telegram", "отправь сообщение", "напиши в telegram", "отправить в telegram", "отправь в telegram")
        if (telegramKeywords.any { queryLower.contains(it) }) {
            val message = extractTelegramMessage(query)
            // Используем chat_id из конфигурации, если userId не указан
            val userId = extractTelegramUserId(query) ?: extractTelegramChatId(query)
            return RoutingDecision(
                action = RoutingAction.MCP_TOOLS,
                tools = listOf(
                    ToolCall(
                        server = "Telegram MCP",
                        tool = "send_telegram_message",
                        args = mapOf(
                            "userId" to userId,
                            "message" to (message ?: "Привет!")
                        )
                    )
                ),
                reasoning = "Запрос требует отправки сообщения в Telegram"
            )
        }
        
        // Проверка на File System запросы
        val filesystemKeywords = listOf("файл", "директория", "прочитай файл", "покажи файл", "найди файл", "список файлов")
        if (filesystemKeywords.any { queryLower.contains(it) }) {
            val toolName = when {
                queryLower.contains("прочитай") || queryLower.contains("читай") -> "read_file"
                queryLower.contains("список") || queryLower.contains("list") -> "list_directory"
                queryLower.contains("найди") || queryLower.contains("поиск") -> "search_files"
                queryLower.contains("информация") || queryLower.contains("info") -> "file_info"
                else -> "read_file"
            }
            return RoutingDecision(
                action = RoutingAction.MCP_TOOLS,
                tools = listOf(
                    ToolCall(
                        server = "File System MCP",
                        tool = toolName,
                        args = emptyMap() // Аргументы будут извлечены из запроса
                    )
                ),
                reasoning = "Запрос требует работы с файловой системой"
            )
        }
        
        // Проверка на Calendar запросы
        val calendarKeywords = listOf("событие", "календарь", "встреча", "напоминание", "создай событие", "покажи события", "предстоящие события")
        if (calendarKeywords.any { queryLower.contains(it) }) {
            val toolName = when {
                queryLower.contains("создай") || queryLower.contains("создать") -> "create_event"
                queryLower.contains("покажи") || queryLower.contains("список") || queryLower.contains("list") -> "list_events"
                queryLower.contains("предстоящ") || queryLower.contains("upcoming") -> "get_upcoming_events"
                queryLower.contains("обнов") || queryLower.contains("update") -> "update_event"
                queryLower.contains("удали") || queryLower.contains("delete") -> "delete_event"
                else -> "list_events"
            }
            // Находим соответствующий инструмент для извлечения аргументов
            val calendarTool = availableTools.find { 
                it.serverName == "Calendar MCP" && it.name == toolName 
            }
            val args = if (calendarTool != null) {
                extractArguments(query, calendarTool)
            } else {
                emptyMap()
            }
            return RoutingDecision(
                action = RoutingAction.MCP_TOOLS,
                tools = listOf(
                    ToolCall(
                        server = "Calendar MCP",
                        tool = toolName,
                        args = args
                    )
                ),
                reasoning = "Запрос требует работы с календарем"
            )
        }
        
        // Проверка на аналитические запросы
        val analyticsKeywords = listOf("анализ", "анализировать", "метрики", "статистика", "данные", "отчет", "csv", "json", "база данных")
        if (analyticsKeywords.any { queryLower.contains(it) }) {
            val dataSource = extractDataSource(query)
            return RoutingDecision(
                action = RoutingAction.ANALYTICS,
                dataSource = dataSource,
                reasoning = "Запрос требует анализа данных${if (dataSource != null) " (источник: $dataSource)" else ""}"
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
    
    private fun extractRepositoryName(query: String): String? {
        // Попытка извлечь имя репозитория из запроса
        val patterns = listOf(
            Regex("репозитори[йя]\\s+(.+?)(?:\\s|$)", RegexOption.IGNORE_CASE),
            Regex("repository\\s+(.+?)(?:\\s|$)", RegexOption.IGNORE_CASE),
            Regex("\"(.+?)\"", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(query)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }
    
    private fun extractTelegramMessage(query: String): String? {
        // Извлечение сообщения для Telegram
        val patterns = listOf(
            Regex("telegram[:\"']\\s*(.+?)(?:$|\"|')", RegexOption.IGNORE_CASE),
            Regex("сообщение[:\"']\\s*(.+?)(?:$|\"|')", RegexOption.IGNORE_CASE),
            Regex("отправь[:\"']\\s*(.+?)(?:$|\"|')", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(query)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        // Если не найдено, берем текст после двоеточия
        val colonIndex = query.indexOf(':')
        if (colonIndex > 0) {
            return query.substring(colonIndex + 1).trim()
        }
        return null
    }
    
    private fun extractTelegramUserId(query: String): String? {
        // Извлечение ID пользователя Telegram (если указан)
        val pattern = Regex("пользовател[ью]\\s+(\\d+)", RegexOption.IGNORE_CASE)
        val match = pattern.find(query)
        return match?.groupValues?.get(1)
    }
    
    private fun extractTelegramChatId(query: String): String {
        // Пытаемся получить chat_id из переменных окружения или используем значение по умолчанию
        // В реальной реализации можно получить из конфигурации
        return System.getenv("TELEGRAM_CHAT_ID") ?: System.getenv("TELEGRAM_GROUP_ID") ?: "default"
    }
    
    private fun extractRepositoryFromQuery(query: String): String {
        // Попытка найти имя репозитория в запросе
        // Ищем паттерны типа "репозитория My Project" или "repository My Project"
        val patterns = listOf(
            Regex("репозитори[йя]\\s+([A-ZА-Я][A-Za-zА-Яа-я\\s]+?)(?:\\s|$)", RegexOption.IGNORE_CASE),
            Regex("repository\\s+([A-ZА-Я][A-Za-zА-Яа-я\\s]+?)(?:\\s|$)", RegexOption.IGNORE_CASE),
            Regex("\"([A-ZА-Я][A-Za-zА-Яа-я\\s]+?)\"", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(query)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        // По умолчанию используем первое имя репозитория из конфигурации
        return "AI Challenge November"
    }
    
    /**
     * Извлечь аргументы из запроса для инструмента
     */
    private fun extractArguments(query: String, tool: com.prike.domain.model.MCPTool): Map<String, Any> {
        val args = mutableMapOf<String, Any>()
        
        // Простая логика извлечения аргументов
        // В будущем можно улучшить с помощью LLM
        
        val queryLower = query.lowercase()
        
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
            "create_event", "update_event" -> {
                // Извлекаем название - ищем после "название" или в кавычках
                // Формат: название "Встреча с командой" или название: "Встреча с командой"
                val titlePatterns = listOf(
                    Regex("название\\s*[:]?\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
                    Regex("название\\s+([^,\\n]+?)(?:,|$)", RegexOption.IGNORE_CASE)
                )
                var titleFound = false
                for (pattern in titlePatterns) {
                    val match = pattern.find(query)
                    if (match != null) {
                        val title = match.groupValues[1].trim()
                        // Проверяем, что это не дата/время
                        if (title.isNotEmpty() && 
                            !title.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) && 
                            !title.contains("2024")) {
                            args["title"] = title
                            titleFound = true
                            break
                        }
                    }
                }
                // Если название не найдено через паттерн "название", ищем первое значение в кавычках
                if (!titleFound) {
                    val allQuoted = Regex("['\"]([^'\"]+)['\"]").findAll(query).toList()
                    if (allQuoted.isNotEmpty()) {
                        // Берем первое значение в кавычках, если оно не дата/время
                        val firstQuoted = allQuoted.first().groupValues[1].trim()
                        if (!firstQuoted.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) && 
                            !firstQuoted.contains("2024")) {
                            args["title"] = firstQuoted
                            titleFound = true
                        }
                    }
                }
                
                // Извлекаем описание
                val descriptionPatterns = listOf(
                    Regex("описание\\s*[:]?\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
                    Regex("описание\\s+([^,\\n]+?)(?:,|$)", RegexOption.IGNORE_CASE)
                )
                for (pattern in descriptionPatterns) {
                    val match = pattern.find(query)
                    if (match != null) {
                        args["description"] = match.groupValues[1].trim()
                        break
                    }
                }
                
                // Извлекаем начало - ищем ISO 8601 формат или после "начало"
                val startTimePatterns = listOf(
                    Regex("начало\\s*[:]?\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
                    Regex("начало\\s+['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
                    Regex("начало\\s+([\\dT:+-]+)", RegexOption.IGNORE_CASE),
                    // ISO 8601 формат (может быть в кавычках или без)
                    Regex("['\"](\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})['\"]", RegexOption.IGNORE_CASE),
                    Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})", RegexOption.IGNORE_CASE)
                )
                for (pattern in startTimePatterns) {
                    val match = pattern.find(query)
                    if (match != null) {
                        val startTime = match.groupValues[1].trim()
                        // Проверяем, что это похоже на дату/время
                        if (startTime.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*"))) {
                            args["start_time"] = startTime
                            break
                        }
                    }
                }
                
                // Извлекаем конец
                val endTimePatterns = listOf(
                    Regex("конец\\s*[:]?\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
                    Regex("конец\\s+([\\dT:+-]+)", RegexOption.IGNORE_CASE)
                )
                for (pattern in endTimePatterns) {
                    val match = pattern.find(query)
                    if (match != null) {
                        args["end_time"] = match.groupValues[1].trim()
                        break
                    }
                }
                
                // Извлекаем категорию - ищем после "категория" или последнее значение в кавычках
                val categoryPatterns = listOf(
                    Regex("категори[яи]\\s*[:]?\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
                    Regex("категори[яи]\\s+['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
                    Regex("категори[яи]\\s+([^,\\n]+?)(?:,|$)", RegexOption.IGNORE_CASE)
                )
                var categoryFound = false
                for (pattern in categoryPatterns) {
                    val match = pattern.find(query)
                    if (match != null) {
                        val category = match.groupValues[1].trim()
                        // Проверяем, что это не дата/время
                        if (category.isNotEmpty() && 
                            !category.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) && 
                            !category.contains("2024")) {
                            args["category"] = category
                            categoryFound = true
                            break
                        }
                    }
                }
                // Если категория не найдена через паттерн, ищем последнее значение в кавычках
                if (!categoryFound) {
                    val allQuoted = Regex("['\"]([^'\"]+)['\"]").findAll(query).toList()
                    if (allQuoted.size > 1) {
                        // Берем последнее значение в кавычках, если оно не дата и не название
                        val lastQuoted = allQuoted.last().groupValues[1].trim()
                        val firstQuoted = allQuoted.first().groupValues[1].trim()
                        if (!lastQuoted.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) && 
                            !lastQuoted.contains("2024") &&
                            lastQuoted != args["title"]?.toString() &&
                            lastQuoted != firstQuoted) {
                            args["category"] = lastQuoted
                        }
                    }
                }
                
                val isCompleted = Regex("выполнено\\s+(true|false)", RegexOption.IGNORE_CASE).find(queryLower)?.groupValues?.get(1)?.toBoolean()
                if (isCompleted != null) args["is_completed"] = isCompleted
            }
            "analyze_csv", "analyze_json" -> {
                // Попытка найти путь к файлу в запросе
                val filePattern = Regex("(?:файл|file)[\\s:]+([^\\s]+)")
                val match = filePattern.find(query)
                if (match != null) {
                    args["file_path"] = match.groupValues[1]
                }
                args["query"] = query
            }
            "analyze_database" -> {
                val dbPattern = Regex("(?:баз|database|бд)[\\s:]+([^\\s]+)")
                val match = dbPattern.find(query)
                if (match != null) {
                    args["db_path"] = match.groupValues[1]
                }
                args["query"] = query
            }
        }
        
        return args
    }
    
    /**
     * Определить категорию для RAG поиска
     */
    fun extractCategory(query: String): String? {
        val queryLower = query.lowercase()
        
        return when {
            queryLower.contains("проект") -> "projects"
            queryLower.contains("обучен") || queryLower.contains("изучен") -> "learning"
            queryLower.contains("личн") || queryLower.contains("цел") -> "personal"
            queryLower.contains("справоч") || queryLower.contains("референс") -> "references"
            else -> null
        }
    }
    
    /**
     * Определить источник данных для аналитики
     */
    fun extractDataSource(query: String): String? {
        val queryLower = query.lowercase()
        
        return when {
            queryLower.contains("csv") -> "data/analytics/metrics.csv"
            queryLower.contains("json") -> "data/analytics/logs.json"
            queryLower.contains("баз") || queryLower.contains("бд") -> "data/analytics/user_data.db"
            else -> null
        }
    }
}

