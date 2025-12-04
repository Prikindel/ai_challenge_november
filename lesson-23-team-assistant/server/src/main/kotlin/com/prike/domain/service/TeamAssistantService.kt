package com.prike.domain.service

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
        val actions = generateActions(request.question, fullContext)
        
        // 8. Формируем источники
        val sources = ragResults.map { result ->
            Source(
                title = result.title ?: "Документация проекта",
                content = result.content,
                url = result.url
            )
        }
        
        return TeamResponse(
            answer = answer,
            tasks = fullContext.tasks,
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
        // Формируем промпт с контекстом
        val systemPrompt = buildSystemPrompt(context)
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
            "Извините, произошла ошибка при генерации ответа. Пожалуйста, попробуйте позже."
        }
    }
    
    /**
     * Построение системного промпта
     */
    private fun buildSystemPrompt(context: TeamContext): String {
        return buildString {
            appendLine("Ты — ассистент команды разработки. Твоя задача — помогать команде управлять задачами, анализировать статус проекта и давать рекомендации.")
            appendLine()
            appendLine("Ты имеешь доступ к:")
            appendLine("- Задачам команды (статус, приоритет, исполнитель, зависимости)")
            appendLine("- Статусу проекта (статистика по задачам)")
            appendLine("- Документации проекта (через RAG)")
            appendLine()
            appendLine("Твои возможности:")
            appendLine("- Анализировать задачи и предлагать приоритеты")
            appendLine("- Выявлять блокирующие задачи")
            appendLine("- Давать рекомендации по управлению задачами")
            appendLine("- Отвечать на вопросы о статусе проекта")
            appendLine()
            if (context.projectStatus != null) {
                appendLine("Текущий статус проекта:")
                appendLine("- Всего задач: ${context.projectStatus.totalTasks}")
                appendLine("- В работе: ${context.projectStatus.tasksInProgress}")
                appendLine("- Выполнено: ${context.projectStatus.tasksDone}")
                appendLine("- Заблокировано: ${context.projectStatus.blockedTasks}")
                appendLine()
            }
            appendLine("Отвечай на русском языке, будь конкретным и полезным.")
        }
    }
    
    /**
     * Построение пользовательского промпта
     */
    private fun buildUserPrompt(question: String, context: TeamContext): String {
        return buildString {
            appendLine("Вопрос команды: $question")
            appendLine()
            
            if (context.tasks.isNotEmpty()) {
                appendLine("Релевантные задачи:")
                context.tasks.forEachIndexed { index, task ->
                    appendLine("${index + 1}. ${task.title}")
                    appendLine("   Статус: ${task.status.name}, Приоритет: ${task.priority.name}")
                    if (task.assignee != null) {
                        appendLine("   Исполнитель: ${task.assignee}")
                    }
                    if (task.blockedBy.isNotEmpty()) {
                        appendLine("   Блокируется задачами: ${task.blockedBy.joinToString(", ")}")
                    }
                    if (task.blocks.isNotEmpty()) {
                        appendLine("   Блокирует задачи: ${task.blocks.joinToString(", ")}")
                    }
                    appendLine()
                }
            }
            
            if (context.ragContext.isNotEmpty()) {
                appendLine("Контекст из документации проекта:")
                appendLine(context.ragContext)
                appendLine()
            }
            
            appendLine("Ответь на вопрос команды, используя информацию о задачах и документации проекта.")
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
                    type = ActionType.CREATE_TASK,
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
                        type = ActionType.UPDATE_TASK,
                        description = "Обновить статус задачи '${task.title}' на IN_PROGRESS",
                        task = task
                    )
                )
            }
        
        // Если вопрос про статус проекта
        if (lowerQuestion.contains("статус") || lowerQuestion.contains("статистик")) {
            actions.add(
                Action(
                    type = ActionType.VIEW_STATUS,
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
     * Результат поиска в RAG
     */
    private data class RagSearchResult(
        val title: String?,
        val content: String,
        val url: String?,
        val similarity: Double
    )
}

