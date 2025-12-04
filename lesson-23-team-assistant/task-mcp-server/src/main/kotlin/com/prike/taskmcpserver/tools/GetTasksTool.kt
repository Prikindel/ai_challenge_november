package com.prike.taskmcpserver.tools

import com.prike.taskmcpserver.tools.handlers.GetTasksHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_tasks для MCP сервера
 */
class GetTasksTool(
    private val handler: GetTasksHandler
) {
    private val logger = LoggerFactory.getLogger(GetTasksTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "get_tasks",
            description = "Получить список задач с фильтрами. Возвращает список задач с возможностью фильтрации по статусу, приоритету и исполнителю.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("status", buildJsonObject {
                        put("type", "string")
                        put("description", "Фильтр по статусу: TODO, IN_PROGRESS, IN_REVIEW, DONE, BLOCKED")
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
                        put("description", "Фильтр по приоритету: LOW, MEDIUM, HIGH, URGENT")
                        put("enum", buildJsonArray {
                            add("LOW")
                            add("MEDIUM")
                            add("HIGH")
                            add("URGENT")
                        })
                    })
                    put("assignee", buildJsonObject {
                        put("type", "string")
                        put("description", "Фильтр по исполнителю (ID пользователя)")
                    })
                },
                required = emptyList() // Все параметры опциональны
            )
        ) { request ->
            logger.debug("Вызов инструмента get_tasks с аргументами: ${request.arguments}")
            val params = GetTasksHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

