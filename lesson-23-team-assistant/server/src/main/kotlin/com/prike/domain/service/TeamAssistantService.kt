package com.prike.domain.service

import com.prike.domain.model.ActionType
import com.prike.domain.model.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис для обработки вопросов команды
 */
class TeamAssistantService(
    private val taskMCPService: TaskMCPService?,
    private val ragMCPService: RagMCPService?,
    private val llmService: LLMService
) {
    private val logger = LoggerFactory.getLogger(TeamAssistantService::class.java)
    private val promptBuilder = TeamAssistantPromptBuilder()
    
    /**
     * Ответить на вопрос команды
     */
    suspend fun answerQuestion(request: TeamRequest): TeamResponse {
        logger.info("Processing team question: question=${request.question.take(100)}...")
        
        // 1. Получаем контекст команды (задачи, статус проекта)
        val context = getTeamContext(request.question)
        
        // 2. Ищем информацию в документации проекта через RAG
        val ragResults = searchProjectDocs(request.question)
        
        // 3. Формируем RAG-контекст
        val ragContext = ragResults.joinToString("\n\n") { it.content }
        
        // 4. Обновляем контекст с RAG-данными
        val fullContext = context.copy(ragContext = ragContext)
        
        // 5. Генерируем ответ через LLM с поддержкой инструментов
        val (answer, executedActions) = generateAnswerWithTools(request.question, fullContext)
        
        // 6. Генерируем рекомендации
        val recommendations = generateRecommendations(fullContext)
        
        // 7. Обновляем контекст после выполнения действий через инструменты
        val updatedContext = if (executedActions.isNotEmpty()) {
            getTeamContext(request.question)
        } else {
            fullContext
        }
        
        // 8. Формируем действия для отображения (объединяем с выполненными)
        val suggestedActions = generateActions(request.question, fullContext)
        val allActions = (executedActions + suggestedActions).distinctBy { it.type }
        
        // 9. Формируем источники
        val sources = ragResults.map { result ->
            Source(
                title = result.title ?: "Документация проекта",
                content = result.content,
                url = result.url
            )
        }
        
        return TeamResponse(
            answer = answer,
            tasks = updatedContext.tasks,
            recommendations = recommendations,
            actions = allActions,
            sources = sources
        )
    }
    
    /**
     * Получить контекст команды (статус проекта, задачи)
     */
    suspend fun getTeamContext(question: String): TeamContext {
        val projectStatus = if (taskMCPService != null) {
            taskMCPService.getProjectStatus()
        } else {
            null
        }
        
        // Получаем релевантные задачи на основе вопроса
        val tasks = getRelevantTasks(question)
        
        return TeamContext(
            projectStatus = projectStatus,
            tasks = tasks
        )
    }
    
    /**
     * Получить релевантные задачи на основе вопроса
     */
    private suspend fun getRelevantTasks(question: String): List<Task> {
        if (taskMCPService == null) {
            return emptyList()
        }
        
        return try {
            // Анализируем вопрос и определяем фильтры
            val lowerQuestion = question.lowercase()
            
            when {
                // Вопрос про задачи с высоким приоритетом
                lowerQuestion.contains("high") || lowerQuestion.contains("высок") || 
                lowerQuestion.contains("приоритет") && lowerQuestion.contains("высок") -> {
                    taskMCPService.getTasksByPriority(Priority.HIGH)
                }
                // Вопрос про заблокированные задачи
                lowerQuestion.contains("blocked") || lowerQuestion.contains("заблокирован") -> {
                    taskMCPService.getTasks(status = TaskStatus.BLOCKED)
                }
                // Вопрос про задачи в работе
                lowerQuestion.contains("in progress") || lowerQuestion.contains("в работе") ||
                lowerQuestion.contains("работа") -> {
                    taskMCPService.getTasks(status = TaskStatus.IN_PROGRESS)
                }
                // Вопрос про задачи на проверке
                lowerQuestion.contains("review") || lowerQuestion.contains("проверк") -> {
                    taskMCPService.getTasks(status = TaskStatus.IN_REVIEW)
                }
                // Вопрос про выполненные задачи
                lowerQuestion.contains("done") || lowerQuestion.contains("выполнен") -> {
                    taskMCPService.getTasks(status = TaskStatus.DONE)
                }
                // Вопрос про задачи к выполнению
                lowerQuestion.contains("todo") || lowerQuestion.contains("к выполнению") -> {
                    taskMCPService.getTasks(status = TaskStatus.TODO)
                }
                // По умолчанию возвращаем все задачи
                else -> {
                    taskMCPService.getTasks()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to get relevant tasks: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Поиск в документации проекта через RAG
     */
    private suspend fun searchProjectDocs(question: String): List<RagSearchResult> {
        if (ragMCPService == null) {
            logger.warn("RAG MCP service is not available")
            return emptyList()
        }
        
        return try {
            val arguments = buildJsonObject {
                put("query", question)
                put("topK", 5)
                put("filterPath", "project/docs/")
            }
            
            val result = ragMCPService.callTool("rag_search_project_docs", arguments)
            parseRagResults(result)
        } catch (e: Exception) {
            logger.error("Failed to search in project docs: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Генерация ответа через LLM с поддержкой инструментов Task MCP
     * LLM может использовать инструменты для создания задач
     */
    private suspend fun generateAnswerWithTools(question: String, context: TeamContext): Pair<String, List<Action>> {
        // Получаем список доступных Task MCP инструментов
        val availableTools = getAvailableTaskTools()
        
        // Формируем промпт с описанием инструментов
        val promptResult = promptBuilder.buildTeamPromptWithTools(question, context, availableTools)
        
        // Генерируем ответ через LLM
        val response = try {
            llmService.generateAnswer(
                question = promptResult.userPrompt,
                systemPrompt = promptResult.systemPrompt,
                temperature = 0.7
            )
        } catch (e: Exception) {
            logger.error("Failed to generate answer: ${e.message}", e)
            return Pair("Извините, произошла ошибка при генерации ответа. Пожалуйста, попробуйте позже.", emptyList())
        }
        
        // Парсим ответ и ищем вызовы инструментов
        val executedActions = parseAndExecuteToolCalls(response.answer, question)
        
        // Формируем финальный ответ с информацией о выполненных действиях
        val finalAnswer = buildFinalAnswer(response.answer, executedActions)
        
        return Pair(finalAnswer, executedActions)
    }
    
    /**
     * Получить список доступных Task MCP инструментов
     */
    private suspend fun getAvailableTaskTools(): List<com.prike.data.client.MCPTool> {
        return try {
            taskMCPService?.listTools() ?: emptyList()
        } catch (e: Exception) {
            logger.warn("Failed to get Task MCP tools: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Парсинг ответа LLM и выполнение вызовов инструментов
     * Ищем JSON блок с tool_calls в ответе LLM
     */
    private suspend fun parseAndExecuteToolCalls(answer: String, question: String): List<Action> {
        val executedActions = mutableListOf<Action>()
        
        // Ищем JSON блок с tool_calls (формат: ```json ... ```)
        val jsonBlockPattern = """```json\s*([\s\S]*?)```""".toRegex(RegexOption.IGNORE_CASE)
        val jsonMatches = jsonBlockPattern.findAll(answer)
        
        for (jsonMatch in jsonMatches) {
            val jsonContent = jsonMatch.groupValues[1].trim()
            try {
                val jsonObj = Json.parseToJsonElement(jsonContent) as? JsonObject
                val toolCalls = jsonObj?.get("tool_calls")?.jsonArray
                
                toolCalls?.forEach { toolCallElement ->
                    if (toolCallElement is JsonObject) {
                        val tool = toolCallElement["tool"]?.jsonPrimitive?.content?.lowercase()
                        val params = toolCallElement["params"]?.jsonObject
                        
                        when (tool) {
                            "create_task" -> {
                                executeCreateTaskTool(params, question, answer, executedActions)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Не удалось распарсить JSON блок с tool_calls: ${e.message}")
                // Fallback: пытаемся найти старый формат [TOOL: ...]
                parseLegacyToolCalls(answer, question, executedActions)
            }
        }
        
        // Если JSON блоков не найдено, пытаемся найти старый формат [TOOL: ...]
        if (executedActions.isEmpty()) {
            parseLegacyToolCalls(answer, question, executedActions)
        }
        
        return executedActions
    }
    
    /**
     * Выполнение инструмента create_task из JSON параметров
     */
    private suspend fun executeCreateTaskTool(
        params: JsonObject?,
        question: String,
        answer: String,
        executedActions: MutableList<Action>
    ) {
        if (params == null) return
        
        try {
            val title = params["title"]?.jsonPrimitive?.content 
                ?: extractTaskTitle(question, answer)
            val description = params["description"]?.jsonPrimitive?.content 
                ?: extractTaskDescription(question, answer)
            val priorityStr = params["priority"]?.jsonPrimitive?.content ?: "MEDIUM"
            val priority = try {
                Priority.valueOf(priorityStr.uppercase())
            } catch (e: Exception) {
                Priority.MEDIUM
            }
            val assignee = params["assignee"]?.jsonPrimitive?.content
            val dueDate = params["dueDate"]?.jsonPrimitive?.longOrNull
            
            if (title.isNotBlank()) {
                // Вызываем инструмент create_task через Task MCP
                val createdTask = taskMCPService?.createTask(
                    title = title,
                    description = description.ifBlank { "Создано ассистентом команды" },
                    priority = priority,
                    assignee = assignee,
                    dueDate = dueDate
                )
                
                if (createdTask != null) {
                    logger.info("Задача создана через инструмент create_task: ${createdTask.id} - ${createdTask.title}")
                    executedActions.add(
                        Action(
                            type = ActionType.CREATE_TASK,
                            description = "✅ Задача создана: ${createdTask.title} (ID: ${createdTask.id})",
                            task = createdTask
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Ошибка при выполнении инструмента create_task: ${e.message}", e)
        }
    }
    
    /**
     * Парсинг старого формата [TOOL: ...] для обратной совместимости
     */
    private suspend fun parseLegacyToolCalls(answer: String, question: String, executedActions: MutableList<Action>) {
        val toolCallPattern = """\[TOOL:\s*(\w+)(?:[,\s]+([^\]]+))?\]""".toRegex(RegexOption.IGNORE_CASE)
        val matches = toolCallPattern.findAll(answer)
        
        for (match in matches) {
            val toolName = match.groupValues[1].lowercase()
            val paramsStr = match.groupValues[2]
            
            when (toolName) {
                "create_task" -> {
                    try {
                        val params = parseToolParams(paramsStr)
                        val title = params["title"] ?: extractTaskTitle(question, answer)
                        val description = params["description"] ?: extractTaskDescription(question, answer)
                        val priorityStr = params["priority"] ?: "MEDIUM"
                        val priority = try {
                            Priority.valueOf(priorityStr.uppercase())
                        } catch (e: Exception) {
                            Priority.MEDIUM
                        }
                        
                        if (title.isNotBlank()) {
                            val createdTask = taskMCPService?.createTask(
                                title = title,
                                description = description.ifBlank { "Создано ассистентом команды" },
                                priority = priority,
                                assignee = params["assignee"],
                                dueDate = params["dueDate"]?.toLongOrNull()
                            )
                            
                            if (createdTask != null) {
                                logger.info("Задача создана через инструмент create_task (legacy format): ${createdTask.id} - ${createdTask.title}")
                                executedActions.add(
                                    Action(
                                        type = ActionType.CREATE_TASK,
                                        description = "✅ Задача создана: ${createdTask.title} (ID: ${createdTask.id})",
                                        task = createdTask
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Ошибка при выполнении инструмента create_task (legacy): ${e.message}", e)
                    }
                }
            }
        }
    }
    
    /**
     * Парсинг параметров инструмента из строки (для старого формата)
     */
    private fun parseToolParams(paramsStr: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (paramsStr.isBlank()) return params
        
        // Формат: key: "value", key2: "value2"
        val paramPattern = """(\w+):\s*["']([^"']+)["']""".toRegex()
        paramPattern.findAll(paramsStr).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            params[key] = value
        }
        
        return params
    }
    
    /**
     * Формирование финального ответа с информацией о выполненных действиях
     */
    private fun buildFinalAnswer(originalAnswer: String, executedActions: List<Action>): String {
        if (executedActions.isEmpty()) {
            // Убираем из ответа JSON блоки с tool_calls и старые паттерны
            return originalAnswer
                .replace(Regex("""```json\s*[\s\S]*?tool_calls[\s\S]*?```""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\[TOOL:[^\]]+\]"""), "")
                .trim()
        }
        
        val actionsInfo = executedActions.joinToString("\n") { action ->
            when (action.type) {
                ActionType.CREATE_TASK -> "✅ ${action.description}"
                else -> "✅ ${action.description}"
            }
        }
        
        // Убираем JSON блоки с tool_calls и старые паттерны, добавляем информацию о выполненных действиях
        val cleanedAnswer = originalAnswer
            .replace(Regex("""```json\s*[\s\S]*?tool_calls[\s\S]*?```""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[TOOL:[^\]]+\]"""), "")
            .trim()
        
        return if (cleanedAnswer.isNotBlank()) {
            "$cleanedAnswer\n\n**Выполненные действия:**\n$actionsInfo"
        } else {
            "**Выполненные действия:**\n$actionsInfo"
        }
    }
    
    /**
     * Генерация рекомендаций
     */
    private suspend fun generateRecommendations(context: TeamContext): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()
        
        if (context.tasks.isEmpty()) {
            return recommendations
        }
        
        // Анализируем задачи и генерируем рекомендации
        val blockedTasks = context.tasks.filter { it.status == TaskStatus.BLOCKED }
        val highPriorityTasks = context.tasks.filter { it.priority == Priority.HIGH || it.priority == Priority.URGENT }
        val tasksInProgress = context.tasks.filter { it.status == TaskStatus.IN_PROGRESS }
        
        // Рекомендация по заблокированным задачам
        blockedTasks.forEach { task ->
            recommendations.add(
                Recommendation(
                    priority = "HIGH",
                    task = task,
                    reason = "Задача заблокирована. Необходимо решить блокирующие задачи: ${task.blockedBy.joinToString(", ")}"
                )
            )
        }
        
        // Рекомендация по задачам с высоким приоритетом
        highPriorityTasks
            .filter { it.status != TaskStatus.DONE && it.status != TaskStatus.BLOCKED }
            .sortedByDescending { it.priority }
            .take(3)
            .forEach { task ->
                recommendations.add(
                    Recommendation(
                        priority = task.priority.name,
                        task = task,
                        reason = "Высокий приоритет. Рекомендуется начать выполнение."
                    )
                )
            }
        
        // Рекомендация по задачам, которые блокируют другие
        context.tasks
            .filter { it.blocks.isNotEmpty() && it.status != TaskStatus.DONE }
            .take(2)
            .forEach { task ->
                recommendations.add(
                    Recommendation(
                        priority = "HIGH",
                        task = task,
                        reason = "Блокирует другие задачи: ${task.blocks.joinToString(", ")}"
                    )
                )
            }
        
        return recommendations.distinctBy { it.task?.id }.take(5)
    }
    
    /**
     * Генерация действий
     */
    private fun generateActions(question: String, context: TeamContext): List<Action> {
        val actions = mutableListOf<Action>()
        val lowerQuestion = question.lowercase()
        
        // Если вопрос про создание задачи
        if (lowerQuestion.contains("создай") || lowerQuestion.contains("создать") || 
            lowerQuestion.contains("добавь") || lowerQuestion.contains("добавить")) {
            actions.add(
                Action(
                    type = com.prike.domain.model.ActionType.CREATE_TASK,
                    description = "Создать новую задачу"
                )
            )
        }
        
        // Если есть заблокированные задачи, предлагаем обновить статус
        context.tasks
            .filter { it.status == TaskStatus.BLOCKED && it.blockedBy.isEmpty() }
            .take(1)
            .forEach { task ->
                actions.add(
                    Action(
                        type = com.prike.domain.model.ActionType.UPDATE_TASK,
                        description = "Обновить статус задачи '${task.title}' на IN_PROGRESS",
                        task = task
                    )
                )
            }
        
        // Если вопрос про статус проекта
        if (lowerQuestion.contains("статус") || lowerQuestion.contains("статистик")) {
            actions.add(
                Action(
                    type = com.prike.domain.model.ActionType.VIEW_STATUS,
                    description = "Просмотреть статус проекта"
                )
            )
        }
        
        return actions
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
     * Извлечь название задачи из вопроса или ответа
     */
    private fun extractTaskTitle(question: String, answer: String): String {
        // Пытаемся найти название задачи в кавычках
        val quotePattern = """[""«»]([^""«»]+)[""«»]""".toRegex()
        quotePattern.find(question)?.let { return it.groupValues[1].trim() }
        quotePattern.find(answer)?.let { return it.groupValues[1].trim() }
        
        // Пытаемся найти после ключевых слов
        val patterns = listOf(
            """(?:создай|создать|добавь|добавить)\s+(?:задачу|task)\s+[""«»]?([^""«»\n]+)""".toRegex(RegexOption.IGNORE_CASE),
            """(?:задачу|task)\s+[""«»]?([^""«»\n]+)[""«»]?""".toRegex(RegexOption.IGNORE_CASE),
            """(?:название|title)[:：]\s*[""«»]?([^""«»\n]+)[""«»]?""".toRegex(RegexOption.IGNORE_CASE)
        )
        
        patterns.forEach { pattern ->
            pattern.find(question)?.let { return it.groupValues[1].trim() }
            pattern.find(answer)?.let { return it.groupValues[1].trim() }
        }
        
        // Если ничего не найдено, используем часть вопроса
        val words = question.split(Regex("""\s+"""))
        val startIndex = words.indexOfFirst { 
            it.lowercase() in listOf("создай", "создать", "добавь", "добавить", "задачу", "task")
        }
        if (startIndex >= 0 && startIndex < words.size - 1) {
            return words.subList(startIndex + 1, minOf(startIndex + 6, words.size)).joinToString(" ")
        }
        
        return "Новая задача"
    }
    
    /**
     * Извлечь описание задачи
     */
    private fun extractTaskDescription(question: String, answer: String): String {
        val patterns = listOf(
            """(?:описание|description)[:：]\s*[""«»]?([^""«»\n]+)[""«»]?""".toRegex(RegexOption.IGNORE_CASE),
            """(?:описание|description)[:：]\s*([^\n]+)""".toRegex(RegexOption.IGNORE_CASE)
        )
        
        patterns.forEach { pattern ->
            pattern.find(question)?.let { return it.groupValues[1].trim() }
            pattern.find(answer)?.let { return it.groupValues[1].trim() }
        }
        
        return ""
    }
    
    /**
     * Извлечь приоритет задачи
     */
    private fun extractTaskPriority(question: String, answer: String): Priority {
        val text = (question + " " + answer).lowercase()
        return when {
            text.contains("urgent") || text.contains("срочн") -> Priority.URGENT
            text.contains("high") || text.contains("высок") -> Priority.HIGH
            text.contains("low") || text.contains("низк") -> Priority.LOW
            else -> Priority.MEDIUM
        }
    }
    
    /**
     * Результат поиска в RAG
     */
    private data class RagSearchResult(
        val title: String?,
        val content: String,
        val url: String?,
        val similarity: Double
    )
}

