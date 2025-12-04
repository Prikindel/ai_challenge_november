package com.prike.taskmcpserver.tools.handlers

import com.prike.taskmcpserver.model.Priority
import com.prike.taskmcpserver.model.TaskStatus
import com.prike.taskmcpserver.storage.TaskStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для обновления задачи
 */
data class UpdateTaskParams(
    val taskId: String,
    val title: String? = null,
    val description: String? = null,
    val status: TaskStatus? = null,
    val priority: Priority? = null,
    val assignee: String? = null,
    val dueDate: Long? = null
)

/**
 * Обработчик для инструмента update_task
 */
class UpdateTaskHandler(
    private val storage: TaskStorage
) : ToolHandler<UpdateTaskParams, String>() {
    
    override val logger = LoggerFactory.getLogger(UpdateTaskHandler::class.java)
    
    override fun execute(params: UpdateTaskParams): String {
        logger.info("Обновление задачи: taskId=${params.taskId}")
        
        val task = storage.updateTask(
            taskId = params.taskId,
            title = params.title,
            description = params.description,
            status = params.status,
            priority = params.priority,
            assignee = params.assignee,
            dueDate = params.dueDate
        )
        
        if (task == null) {
            return "Задача не найдена: ${params.taskId}"
        }
        
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
    
    override fun prepareResult(request: UpdateTaskParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): UpdateTaskParams {
            val taskId = arguments["taskId"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("taskId обязателен")
            val title = arguments["title"]?.jsonPrimitive?.content
            val description = arguments["description"]?.jsonPrimitive?.content
            val statusStr = arguments["status"]?.jsonPrimitive?.content
            val priorityStr = arguments["priority"]?.jsonPrimitive?.content
            val assignee = arguments["assignee"]?.jsonPrimitive?.content
            val dueDate = arguments["dueDate"]?.jsonPrimitive?.longOrNull
            
            return UpdateTaskParams(
                taskId = taskId,
                title = title,
                description = description,
                status = statusStr?.let { TaskStatus.valueOf(it) },
                priority = priorityStr?.let { Priority.valueOf(it) },
                assignee = assignee,
                dueDate = dueDate
            )
        }
    }
}

