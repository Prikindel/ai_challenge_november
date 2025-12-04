package com.prike.taskmcpserver.tools.handlers

import com.prike.taskmcpserver.storage.TaskStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для получения задачи
 */
data class GetTaskParams(
    val taskId: String
)

/**
 * Обработчик для инструмента get_task
 */
class GetTaskHandler(
    private val storage: TaskStorage
) : ToolHandler<GetTaskParams, String>() {
    
    override val logger = LoggerFactory.getLogger(GetTaskHandler::class.java)
    
    override fun execute(params: GetTaskParams): String {
        logger.info("Получение задачи: taskId=${params.taskId}")
        
        val task = storage.getTask(params.taskId)
        
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
    
    override fun prepareResult(request: GetTaskParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): GetTaskParams {
            val taskId = arguments["taskId"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("taskId обязателен")
            return GetTaskParams(taskId = taskId)
        }
    }
}

