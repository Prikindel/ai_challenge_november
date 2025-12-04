package com.prike.taskmcpserver.tools

import com.prike.taskmcpserver.tools.handlers.GetTasksByPriorityHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_tasks_by_priority для MCP сервера
 */
class GetTasksByPriorityTool(
    private val handler: GetTasksByPriorityHandler
) {
    private val logger = LoggerFactory.getLogger(GetTasksByPriorityTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "get_tasks_by_priority",
            description = "Получить задачи по приоритету. Возвращает список всех задач с указанным приоритетом.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("priority", buildJsonObject {
                        put("type", "string")
                        put("description", "Приоритет задач: LOW, MEDIUM, HIGH, URGENT")
                        put("enum", buildJsonArray {
                            add("LOW")
                            add("MEDIUM")
                            add("HIGH")
                            add("URGENT")
                        })
                    })
                },
                required = listOf("priority")
            )
        ) { request ->
            logger.debug("Вызов инструмента get_tasks_by_priority с аргументами: ${request.arguments}")
            val params = GetTasksByPriorityHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

