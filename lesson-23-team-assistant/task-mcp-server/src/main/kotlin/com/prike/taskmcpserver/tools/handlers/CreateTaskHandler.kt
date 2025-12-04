package com.prike.taskmcpserver.tools.handlers

import com.prike.taskmcpserver.model.Priority
import com.prike.taskmcpserver.storage.TaskStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для создания задачи
 */
data class CreateTaskParams(
    val title: String,
    val description: String,
    val priority: Priority,
    val assignee: String? = null,
    val dueDate: Long? = null
)

/**
 * Обработчик для инструмента create_task
 */
class CreateTaskHandler(
    private val storage: TaskStorage
) : ToolHandler<CreateTaskParams, String>() {
    
    override val logger = LoggerFactory.getLogger(CreateTaskHandler::class.java)
    
    override fun execute(params: CreateTaskParams): String {
        logger.info("Создание задачи: title=${params.title}, priority=${params.priority}")
        
        val task = storage.createTask(
            title = params.title,
            description = params.description,
            priority = params.priority,
            assignee = params.assignee,
            dueDate = params.dueDate
        )
        
        return buildJsonObject {
            put("id", task.id)
            put("title", task.title)
            put("description", task.description)
            put("status", task.status.name)
            put("priority", task.priority.name)
            put("assignee", task.assignee ?: "")
            task.dueDate?.let { put("dueDate", it) }
            put("blockedBy", buildJsonArray {
                task.blockedBy.forEach { add(it) }
            })
            put("blocks", buildJsonArray {
                task.blocks.forEach { add(it) }
            })
            put("createdAt", task.createdAt)
            put("updatedAt", task.updatedAt)
        }.toString()
    }
    
    override fun prepareResult(request: CreateTaskParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): CreateTaskParams {
            val title = arguments["title"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("title обязателен")
            val description = arguments["description"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("description обязателен")
            val priorityStr = arguments["priority"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("priority обязателен")
            val priority = try {
                Priority.valueOf(priorityStr)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Неверный приоритет: $priorityStr. Допустимые значения: LOW, MEDIUM, HIGH, URGENT")
            }
            val assignee = arguments["assignee"]?.jsonPrimitive?.content
            val dueDate = arguments["dueDate"]?.jsonPrimitive?.longOrNull
            
            return CreateTaskParams(
                title = title,
                description = description,
                priority = priority,
                assignee = assignee,
                dueDate = dueDate
            )
        }
    }
}

