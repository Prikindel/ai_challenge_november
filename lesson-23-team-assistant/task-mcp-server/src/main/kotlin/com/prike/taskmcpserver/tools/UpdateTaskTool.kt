package com.prike.taskmcpserver.tools

import com.prike.taskmcpserver.tools.handlers.UpdateTaskHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента update_task для MCP сервера
 */
class UpdateTaskTool(
    private val handler: UpdateTaskHandler
) {
    private val logger = LoggerFactory.getLogger(UpdateTaskTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "update_task",
            description = "Обновить задачу. Возвращает обновлённые данные задачи. Все параметры опциональны, обновляются только указанные.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("taskId", buildJsonObject {
                        put("type", "string")
                        put("description", "ID задачи")
                    })
                    put("title", buildJsonObject {
                        put("type", "string")
                        put("description", "Новое название задачи (опционально)")
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "Новое описание задачи (опционально)")
                    })
                    put("status", buildJsonObject {
                        put("type", "string")
                        put("description", "Новый статус: TODO, IN_PROGRESS, IN_REVIEW, DONE, BLOCKED (опционально)")
                        put("enum", buildJsonArray {
                            add("TODO")
                            add("IN_PROGRESS")
                            add("IN_REVIEW")
                            add("DONE")
                            add("BLOCKED")
                        })
                    })
                    put("priority", buildJsonObject {
                        put("type", "string")
                        put("description", "Новый приоритет: LOW, MEDIUM, HIGH, URGENT (опционально)")
                        put("enum", buildJsonArray {
                            add("LOW")
                            add("MEDIUM")
                            add("HIGH")
                            add("URGENT")
                        })
                    })
                    put("assignee", buildJsonObject {
                        put("type", "string")
                        put("description", "Новый исполнитель (ID пользователя, опционально)")
                    })
                    put("dueDate", buildJsonObject {
                        put("type", "number")
                        put("description", "Новый срок выполнения в миллисекундах (timestamp, опционально)")
                    })
                },
                required = listOf("taskId")
            )
        ) { request ->
            logger.debug("Вызов инструмента update_task с аргументами: ${request.arguments}")
            val params = UpdateTaskHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

