package com.prike.taskmcpserver.tools.handlers

import com.prike.taskmcpserver.model.Priority
import com.prike.taskmcpserver.storage.InMemoryTaskStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для получения задач по приоритету
 */
data class GetTasksByPriorityParams(
    val priority: Priority
)

/**
 * Обработчик для инструмента get_tasks_by_priority
 */
class GetTasksByPriorityHandler(
    private val storage: InMemoryTaskStorage
) : ToolHandler<GetTasksByPriorityParams, String>() {
    
    override val logger = LoggerFactory.getLogger(GetTasksByPriorityHandler::class.java)
    
    override fun execute(params: GetTasksByPriorityParams): String {
        logger.info("Получение задач по приоритету: priority=${params.priority}")
        
        val tasks = storage.getTasksByPriority(params.priority)
        
        return buildJsonObject {
            put("tasks", buildJsonArray {
                tasks.forEach { task ->
                    add(buildJsonObject {
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
                    })
                }
            })
            put("count", tasks.size)
        }.toString()
    }
    
    override fun prepareResult(request: GetTasksByPriorityParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): GetTasksByPriorityParams {
            val priorityStr = arguments["priority"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("priority обязателен")
            val priority = try {
                Priority.valueOf(priorityStr)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Неверный приоритет: $priorityStr. Допустимые значения: LOW, MEDIUM, HIGH, URGENT")
            }
            return GetTasksByPriorityParams(priority = priority)
        }
    }
}

