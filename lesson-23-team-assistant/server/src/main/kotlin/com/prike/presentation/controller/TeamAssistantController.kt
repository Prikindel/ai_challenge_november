package com.prike.presentation.controller

import com.prike.domain.model.*
import com.prike.domain.service.TeamAssistantService
import com.prike.domain.service.TaskMCPService
import com.prike.presentation.dto.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Контроллер для API ассистента команды
 */
class TeamAssistantController(
    private val teamAssistantService: TeamAssistantService?,
    private val taskMCPService: TaskMCPService?
) {
    private val logger = LoggerFactory.getLogger(TeamAssistantController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Задать вопрос ассистенту команды
            post("/api/team/ask") {
                try {
                    if (teamAssistantService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("Team assistant service is not available")
                        )
                        return@post
                    }
                    
                    val request = call.receive<TeamQuestionRequest>()
                    
                    // Валидация
                    if (request.question.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Question cannot be blank")
                        )
                        return@post
                    }
                    
                    // Создаём TeamRequest
                    val teamRequest = TeamRequest(
                        question = request.question
                    )
                    
                    // Получаем ответ от TeamAssistantService
                    val response = runBlocking {
                        teamAssistantService.answerQuestion(teamRequest)
                    }
                    
                    // Преобразуем в DTO
                    val tasksDto = response.tasks.map { task ->
                        TaskDto(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            status = task.status.name,
                            priority = task.priority.name,
                            assignee = task.assignee,
                            dueDate = task.dueDate,
                            blockedBy = task.blockedBy,
                            blocks = task.blocks,
                            createdAt = task.createdAt,
                            updatedAt = task.updatedAt
                        )
                    }
                    
                    val recommendationsDto = response.recommendations.map { rec ->
                        RecommendationDto(
                            priority = rec.priority,
                            task = rec.task?.let { task ->
                                TaskDto(
                                    id = task.id,
                                    title = task.title,
                                    description = task.description,
                                    status = task.status.name,
                                    priority = task.priority.name,
                                    assignee = task.assignee,
                                    dueDate = task.dueDate,
                                    blockedBy = task.blockedBy,
                                    blocks = task.blocks,
                                    createdAt = task.createdAt,
                                    updatedAt = task.updatedAt
                                )
                            },
                            reason = rec.reason
                        )
                    }
                    
                    val actionsDto = response.actions.map { action ->
                        ActionDto(
                            type = action.type.name,
                            description = action.description,
                            task = action.task?.let { task ->
                                TaskDto(
                                    id = task.id,
                                    title = task.title,
                                    description = task.description,
                                    status = task.status.name,
                                    priority = task.priority.name,
                                    assignee = task.assignee,
                                    dueDate = task.dueDate,
                                    blockedBy = task.blockedBy,
                                    blocks = task.blocks,
                                    createdAt = task.createdAt,
                                    updatedAt = task.updatedAt
                                )
                            }
                        )
                    }
                    
                    val sourcesDto = response.sources.map { source ->
                        SourceDto(
                            title = source.title,
                            content = source.content,
                            url = source.url
                        )
                    }
                    
                    call.respond(
                        TeamQuestionResponse(
                            answer = response.answer,
                            tasks = tasksDto,
                            recommendations = recommendationsDto,
                            actions = actionsDto,
                            sources = sourcesDto
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to process team question", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to process team question: ${e.message}")
                    )
                }
            }
            
            // Получить список задач
            get("/api/team/tasks") {
                try {
                    if (taskMCPService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("Task MCP service is not available")
                        )
                        return@get
                    }
                    
                    val status = call.request.queryParameters["status"]
                    val priority = call.request.queryParameters["priority"]
                    val assignee = call.request.queryParameters["assignee"]
                    
                    val tasks = runBlocking {
                        taskMCPService.getTasks(
                            status = status?.let { TaskStatus.valueOf(it) },
                            priority = priority?.let { Priority.valueOf(it) },
                            assignee = assignee
                        )
                    }
                    
                    val tasksDto = tasks.map { task ->
                        TaskDto(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            status = task.status.name,
                            priority = task.priority.name,
                            assignee = task.assignee,
                            dueDate = task.dueDate,
                            blockedBy = task.blockedBy,
                            blocks = task.blocks,
                            createdAt = task.createdAt,
                            updatedAt = task.updatedAt
                        )
                    }
                    
                    call.respond(TasksListResponse(tasks = tasksDto))
                } catch (e: Exception) {
                    logger.error("Failed to get tasks", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get tasks: ${e.message}")
                    )
                }
            }
            
            // Получить задачу по ID
            get("/api/team/tasks/{taskId}") {
                try {
                    if (taskMCPService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("Task MCP service is not available")
                        )
                        return@get
                    }
                    
                    val taskId = call.parameters["taskId"]
                        ?: throw IllegalArgumentException("taskId is required")
                    
                    val task = runBlocking {
                        taskMCPService.getTask(taskId)
                    }
                    
                    if (task == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Task not found: $taskId")
                        )
                        return@get
                    }
                    
                    call.respond(
                        TaskDto(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            status = task.status.name,
                            priority = task.priority.name,
                            assignee = task.assignee,
                            dueDate = task.dueDate,
                            blockedBy = task.blockedBy,
                            blocks = task.blocks,
                            createdAt = task.createdAt,
                            updatedAt = task.updatedAt
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to get task", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get task: ${e.message}")
                    )
                }
            }
            
            // Создать новую задачу
            post("/api/team/tasks") {
                try {
                    if (taskMCPService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("Task MCP service is not available")
                        )
                        return@post
                    }
                    
                    val request = call.receive<CreateTaskRequest>()
                    
                    // Валидация
                    if (request.title.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("title cannot be blank")
                        )
                        return@post
                    }
                    
                    if (request.description.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("description cannot be blank")
                        )
                        return@post
                    }
                    
                    val priority = try {
                        Priority.valueOf(request.priority)
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid priority: ${request.priority}. Must be LOW, MEDIUM, HIGH, or URGENT")
                        )
                        return@post
                    }
                    
                    val task = runBlocking {
                        taskMCPService.createTask(
                            title = request.title,
                            description = request.description,
                            priority = priority,
                            assignee = request.assignee,
                            dueDate = request.dueDate
                        )
                    }
                    
                    if (task == null) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to create task")
                        )
                        return@post
                    }
                    
                    call.respond(
                        HttpStatusCode.Created,
                        TaskDto(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            status = task.status.name,
                            priority = task.priority.name,
                            assignee = task.assignee,
                            dueDate = task.dueDate,
                            blockedBy = task.blockedBy,
                            blocks = task.blocks,
                            createdAt = task.createdAt,
                            updatedAt = task.updatedAt
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to create task", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to create task: ${e.message}")
                    )
                }
            }
            
            // Обновить задачу
            put("/api/team/tasks/{taskId}") {
                try {
                    if (taskMCPService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("Task MCP service is not available")
                        )
                        return@put
                    }
                    
                    val taskId = call.parameters["taskId"]
                        ?: throw IllegalArgumentException("taskId is required")
                    
                    val request = call.receive<UpdateTaskRequest>()
                    
                    val status = request.status?.let {
                        try {
                            TaskStatus.valueOf(it)
                        } catch (e: IllegalArgumentException) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("Invalid status: $it. Must be TODO, IN_PROGRESS, IN_REVIEW, DONE, or BLOCKED")
                            )
                            return@put
                        }
                    }
                    
                    val priority = request.priority?.let {
                        try {
                            Priority.valueOf(it)
                        } catch (e: IllegalArgumentException) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("Invalid priority: $it. Must be LOW, MEDIUM, HIGH, or URGENT")
                            )
                            return@put
                        }
                    }
                    
                    val task = runBlocking {
                        taskMCPService.updateTask(
                            taskId = taskId,
                            title = request.title,
                            description = request.description,
                            status = status,
                            priority = priority,
                            assignee = request.assignee,
                            dueDate = request.dueDate
                        )
                    }
                    
                    if (task == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Task not found: $taskId")
                        )
                        return@put
                    }
                    
                    call.respond(
                        TaskDto(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            status = task.status.name,
                            priority = task.priority.name,
                            assignee = task.assignee,
                            dueDate = task.dueDate,
                            blockedBy = task.blockedBy,
                            blocks = task.blocks,
                            createdAt = task.createdAt,
                            updatedAt = task.updatedAt
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to update task", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to update task: ${e.message}")
                    )
                }
            }
            
            // Получить статус проекта
            get("/api/team/status") {
                try {
                    if (taskMCPService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("Task MCP service is not available")
                        )
                        return@get
                    }
                    
                    val projectStatus = runBlocking {
                        taskMCPService.getProjectStatus()
                    }
                    
                    if (projectStatus == null) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to get project status")
                        )
                        return@get
                    }
                    
                    call.respond(
                        ProjectStatusDto(
                            totalTasks = projectStatus.totalTasks,
                            tasksByStatus = projectStatus.tasksByStatus.mapKeys { it.key.name },
                            tasksByPriority = projectStatus.tasksByPriority.mapKeys { it.key.name },
                            blockedTasks = projectStatus.blockedTasks,
                            tasksInProgress = projectStatus.tasksInProgress,
                            tasksDone = projectStatus.tasksDone
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to get project status", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get project status: ${e.message}")
                    )
                }
            }
        }
    }
}

