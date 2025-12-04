package com.prike.taskmcpserver.tools

import com.prike.taskmcpserver.tools.handlers.GetTaskHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_task для MCP сервера
 */
class GetTaskTool(
    private val handler: GetTaskHandler
) {
    private val logger = LoggerFactory.getLogger(GetTaskTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "get_task",
            description = "Получить задачу по ID. Возвращает полную информацию о задаче.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("taskId", buildJsonObject {
                        put("type", "string")
                        put("description", "ID задачи")
                    })
                },
                required = listOf("taskId")
            )
        ) { request ->
            logger.debug("Вызов инструмента get_task с аргументами: ${request.arguments}")
            val params = GetTaskHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

