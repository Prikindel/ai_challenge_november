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
        
        // 5. Генерируем ответ через LLM
        val answer = generateAnswer(request.question, fullContext)
        
        // 6. Генерируем рекомендации
        val recommendations = generateRecommendations(fullContext)
        
        // 7. Генерируем действия
        var actions = generateActions(request.question, fullContext)
        
        // 8. Автоматически выполняем действия, если они требуют создания задачи
        val executedActions = executeActions(request.question, answer, actions)
        actions = executedActions
        
        // 9. Обновляем контекст после выполнения действий
        val updatedContext = if (executedActions.any { it.type == ActionType.CREATE_TASK }) {
            getTeamContext(request.question)
        } else {
            fullContext
        }
        
        // 10. Формируем источники
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
            actions = actions,
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
     * Генерация ответа через LLM
     */
    private suspend fun generateAnswer(question: String, context: TeamContext): String {
        // Формируем промпт с контекстом через TeamAssistantPromptBuilder
        val promptResult = promptBuilder.buildTeamPrompt(question, context)
        
        return try {
            val response = llmService.generateAnswer(
                question = promptResult.userPrompt,
                systemPrompt = promptResult.systemPrompt,
                temperature = 0.7
            )
            response.answer
        } catch (e: Exception) {
            logger.error("Failed to generate answer: ${e.message}", e)
            "Извините, произошла ошибка при генерации ответа. Пожалуйста, попробуйте позже."
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
     * Выполнить действия автоматически
     */
    private suspend fun executeActions(question: String, answer: String, actions: List<Action>): List<Action> {
        val executedActions = actions.toMutableList()
        val lowerQuestion = question.lowercase()
        val lowerAnswer = answer.lowercase()
        
        // Если вопрос про создание задачи и есть действие CREATE_TASK
        if ((lowerQuestion.contains("создай") || lowerQuestion.contains("создать") || 
             lowerQuestion.contains("добавь") || lowerQuestion.contains("добавить")) &&
            executedActions.any { it.type == ActionType.CREATE_TASK }) {
            
            try {
                // Пытаемся извлечь информацию о задаче из вопроса или ответа
                val taskTitle = extractTaskTitle(question, answer)
                val taskDescription = extractTaskDescription(question, answer)
                val taskPriority = extractTaskPriority(question, answer)
                
                if (taskTitle.isNotBlank()) {
                    // Создаём задачу через Task MCP
                    val createdTask = taskMCPService?.createTask(
                        title = taskTitle,
                        description = taskDescription.ifBlank { "Создано ассистентом команды" },
                        priority = taskPriority,
                        assignee = null,
                        dueDate = null
                    )
                    
                    if (createdTask != null) {
                        logger.info("Автоматически создана задача: ${createdTask.id} - ${createdTask.title}")
                        // Обновляем действие, указывая что оно выполнено
                        val actionIndex = executedActions.indexOfFirst { it.type == ActionType.CREATE_TASK }
                        if (actionIndex >= 0) {
                            executedActions[actionIndex] = executedActions[actionIndex].copy(
                                description = "✅ Задача создана: ${createdTask.title} (ID: ${createdTask.id})"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Ошибка при автоматическом создании задачи: ${e.message}", e)
            }
        }
        
        return executedActions
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

