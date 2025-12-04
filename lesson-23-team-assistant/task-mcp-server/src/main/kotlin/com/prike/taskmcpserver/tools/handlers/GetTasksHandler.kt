package com.prike.taskmcpserver.tools.handlers

import com.prike.taskmcpserver.model.Priority
import com.prike.taskmcpserver.model.TaskStatus
import com.prike.taskmcpserver.storage.InMemoryTaskStorage
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для получения списка задач
 */
data class GetTasksParams(
    val status: TaskStatus? = null,
    val priority: Priority? = null,
    val assignee: String? = null
)

/**
 * Обработчик для инструмента get_tasks
 */
class GetTasksHandler(
    private val storage: InMemoryTaskStorage
) : ToolHandler<GetTasksParams, String>() {
    
    override val logger = LoggerFactory.getLogger(GetTasksHandler::class.java)
    
    override fun execute(params: GetTasksParams): String {
        logger.info("Получение задач: status=${params.status}, priority=${params.priority}, assignee=${params.assignee}")
        
        val tasks = storage.getTasks(
            status = params.status,
            priority = params.priority,
            assignee = params.assignee
        )
        
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
    
    override fun prepareResult(request: GetTasksParams, result: String): TextContent {
        return TextContent(text = result)
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): GetTasksParams {
            val statusStr = arguments["status"]?.jsonPrimitive?.content
            val priorityStr = arguments["priority"]?.jsonPrimitive?.content
            val assignee = arguments["assignee"]?.jsonPrimitive?.content
            
            return GetTasksParams(
                status = statusStr?.let { TaskStatus.valueOf(it) },
                priority = priorityStr?.let { Priority.valueOf(it) },
                assignee = assignee
            )
        }
    }
}

