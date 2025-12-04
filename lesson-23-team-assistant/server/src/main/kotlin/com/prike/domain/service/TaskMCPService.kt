package com.prike.domain.service

import com.prike.data.client.TaskMCPClient
import com.prike.domain.model.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Сервис для работы с Task MCP сервером
 */
class TaskMCPService(
    private val taskMCPClient: TaskMCPClient,
    private val lessonRoot: File,
    private val taskMCPJarPath: String? = null
) {
    private val logger = LoggerFactory.getLogger(TaskMCPService::class.java)
    
    /**
     * Подключение к Task MCP серверу
     */
    suspend fun connect() {
        try {
            taskMCPClient.connectToServer(taskMCPJarPath, lessonRoot)
            logger.info("Task MCP service connected successfully")
        } catch (e: Exception) {
            logger.error("Failed to connect to Task MCP server: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Отключение от Task MCP сервера
     */
    suspend fun disconnect() {
        try {
            taskMCPClient.disconnect()
            logger.info("Task MCP service disconnected")
        } catch (e: Exception) {
            logger.warn("Error disconnecting from Task MCP server: ${e.message}")
        }
    }
    
    /**
     * Получить список задач с фильтрами
     */
    suspend fun getTasks(
        status: TaskStatus? = null,
        priority: Priority? = null,
        assignee: String? = null
    ): List<Task> {
        return try {
            if (!taskMCPClient.isConnected()) {
                logger.warn("Task MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                status?.let { put("status", it.name) }
                priority?.let { put("priority", it.name) }
                assignee?.let { put("assignee", it) }
            }
            
            val result = taskMCPClient.callTool("get_tasks", arguments)
            parseTasks(result)
        } catch (e: Exception) {
            logger.error("Failed to get tasks: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Получить задачу по ID
     */
    suspend fun getTask(taskId: String): Task? {
        return try {
            if (!taskMCPClient.isConnected()) {
                logger.warn("Task MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                put("taskId", taskId)
            }
            
            val result = taskMCPClient.callTool("get_task", arguments)
            
            if (result.contains("не найдена") || result.contains("not found")) {
                logger.warn("Task not found: $taskId")
                return null
            }
            
            parseTask(result)
        } catch (e: Exception) {
            logger.error("Failed to get task: ${e.message}", e)
            null
        }
    }
    
    /**
     * Создать новую задачу
     */
    suspend fun createTask(
        title: String,
        description: String,
        priority: Priority,
        assignee: String? = null,
        dueDate: Long? = null
    ): Task? {
        return try {
            if (!taskMCPClient.isConnected()) {
                logger.warn("Task MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                put("title", title)
                put("description", description)
                put("priority", priority.name)
                assignee?.let { put("assignee", it) }
                dueDate?.let { put("dueDate", it) }
            }
            
            val result = taskMCPClient.callTool("create_task", arguments)
            parseTask(result)
        } catch (e: Exception) {
            logger.error("Failed to create task: ${e.message}", e)
            null
        }
    }
    
    /**
     * Обновить задачу
     */
    suspend fun updateTask(
        taskId: String,
        title: String? = null,
        description: String? = null,
        status: TaskStatus? = null,
        priority: Priority? = null,
        assignee: String? = null,
        dueDate: Long? = null
    ): Task? {
        return try {
            if (!taskMCPClient.isConnected()) {
                logger.warn("Task MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                put("taskId", taskId)
                title?.let { put("title", it) }
                description?.let { put("description", it) }
                status?.let { put("status", it.name) }
                priority?.let { put("priority", it.name) }
                assignee?.let { put("assignee", it) }
                dueDate?.let { put("dueDate", it) }
            }
            
            val result = taskMCPClient.callTool("update_task", arguments)
            
            if (result.contains("не найдена") || result.contains("not found")) {
                logger.warn("Task not found: $taskId")
                return null
            }
            
            parseTask(result)
        } catch (e: Exception) {
            logger.error("Failed to update task: ${e.message}", e)
            null
        }
    }
    
    /**
     * Получить статус проекта
     */
    suspend fun getProjectStatus(): ProjectStatus? {
        return try {
            if (!taskMCPClient.isConnected()) {
                logger.warn("Task MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val result = taskMCPClient.callTool("get_project_status", buildJsonObject {})
            parseProjectStatus(result)
        } catch (e: Exception) {
            logger.error("Failed to get project status: ${e.message}", e)
            null
        }
    }
    
    /**
     * Получить задачи по приоритету
     */
    suspend fun getTasksByPriority(priority: Priority): List<Task> {
        return try {
            if (!taskMCPClient.isConnected()) {
                logger.warn("Task MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = buildJsonObject {
                put("priority", priority.name)
            }
            
            val result = taskMCPClient.callTool("get_tasks_by_priority", arguments)
            parseTasks(result)
        } catch (e: Exception) {
            logger.error("Failed to get tasks by priority: ${e.message}", e)
            emptyList()
        }
    }
    
    fun isConnected(): Boolean {
        return taskMCPClient.isConnected()
    }
    
    // Парсинг JSON ответов
    
    private fun parseTask(json: String): Task? {
        return try {
            val obj = Json.parseToJsonElement(json) as? JsonObject ?: return null
            Task(
                id = obj["id"]?.jsonPrimitive?.content ?: return null,
                title = obj["title"]?.jsonPrimitive?.content ?: return null,
                description = obj["description"]?.jsonPrimitive?.content ?: return null,
                status = TaskStatus.valueOf(obj["status"]?.jsonPrimitive?.content ?: "TODO"),
                priority = Priority.valueOf(obj["priority"]?.jsonPrimitive?.content ?: "MEDIUM"),
                assignee = obj["assignee"]?.jsonPrimitive?.content,
                dueDate = obj["dueDate"]?.jsonPrimitive?.longOrNull,
                blockedBy = obj["blockedBy"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList(),
                blocks = obj["blocks"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList(),
                createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        } catch (e: Exception) {
            logger.error("Failed to parse task: ${e.message}", e)
            null
        }
    }
    
    private fun parseTasks(json: String): List<Task> {
        return try {
            val obj = Json.parseToJsonElement(json) as? JsonObject ?: return emptyList()
            val tasksArray = obj["tasks"]?.jsonArray ?: return emptyList()
            
            tasksArray.mapNotNull { element ->
                if (element is JsonObject) {
                    try {
                        Task(
                            id = element["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            title = element["title"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            description = element["description"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            status = TaskStatus.valueOf(element["status"]?.jsonPrimitive?.content ?: "TODO"),
                            priority = Priority.valueOf(element["priority"]?.jsonPrimitive?.content ?: "MEDIUM"),
                            assignee = element["assignee"]?.jsonPrimitive?.content,
                            dueDate = element["dueDate"]?.jsonPrimitive?.longOrNull,
                            blockedBy = element["blockedBy"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList(),
                            blocks = element["blocks"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList(),
                            createdAt = element["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                            updatedAt = element["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to parse task from array: ${e.message}")
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse tasks: ${e.message}", e)
            emptyList()
        }
    }
    
    private fun parseProjectStatus(json: String): ProjectStatus? {
        return try {
            val obj = Json.parseToJsonElement(json) as? JsonObject ?: return null
            ProjectStatus(
                totalTasks = obj["totalTasks"]?.jsonPrimitive?.intOrNull ?: 0,
                tasksByStatus = obj["tasksByStatus"]?.jsonObject?.entries?.associate { 
                    it.key to (it.value.jsonPrimitive.intOrNull ?: 0)
                } ?: emptyMap(),
                tasksByPriority = obj["tasksByPriority"]?.jsonObject?.entries?.associate {
                    it.key to (it.value.jsonPrimitive.intOrNull ?: 0)
                } ?: emptyMap(),
                blockedTasks = obj["blockedTasks"]?.jsonPrimitive?.intOrNull ?: 0,
                tasksInProgress = obj["tasksInProgress"]?.jsonPrimitive?.intOrNull ?: 0,
                tasksDone = obj["tasksDone"]?.jsonPrimitive?.intOrNull ?: 0
            )
        } catch (e: Exception) {
            logger.error("Failed to parse project status: ${e.message}", e)
            null
        }
    }
}

