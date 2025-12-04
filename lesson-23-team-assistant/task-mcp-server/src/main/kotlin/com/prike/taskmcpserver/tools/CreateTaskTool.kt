package com.prike.taskmcpserver.tools

import com.prike.taskmcpserver.tools.handlers.CreateTaskHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента create_task для MCP сервера
 */
class CreateTaskTool(
    private val handler: CreateTaskHandler
) {
    private val logger = LoggerFactory.getLogger(CreateTaskTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "create_task",
            description = "Создать новую задачу. Возвращает данные созданной задачи.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("title", buildJsonObject {
                        put("type", "string")
                        put("description", "Название задачи")
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "Описание задачи")
                    })
                    put("priority", buildJsonObject {
                        put("type", "string")
                        put("description", "Приоритет задачи: LOW, MEDIUM, HIGH, URGENT")
                        put("enum", buildJsonArray {
                            add("LOW")
                            add("MEDIUM")
                            add("HIGH")
                            add("URGENT")
                        })
                    })
                    put("assignee", buildJsonObject {
                        put("type", "string")
                        put("description", "ID исполнителя (опционально)")
                    })
                    put("dueDate", buildJsonObject {
                        put("type", "number")
                        put("description", "Срок выполнения в миллисекундах (timestamp, опционально)")
                    })
                },
                required = listOf("title", "description", "priority")
            )
        ) { request ->
            logger.debug("Вызов инструмента create_task с аргументами: ${request.arguments}")
            val params = CreateTaskHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

