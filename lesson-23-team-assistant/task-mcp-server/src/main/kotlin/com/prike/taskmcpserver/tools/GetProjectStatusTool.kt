package com.prike.taskmcpserver.tools

import com.prike.taskmcpserver.tools.handlers.GetProjectStatusHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_project_status для MCP сервера
 */
class GetProjectStatusTool(
    private val handler: GetProjectStatusHandler
) {
    private val logger = LoggerFactory.getLogger(GetProjectStatusTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "get_project_status",
            description = "Получить статус проекта. Возвращает статистику по задачам: общее количество, распределение по статусам и приоритетам, количество заблокированных задач.",
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { request ->
            logger.debug("Вызов инструмента get_project_status")
            handler.handle(Unit)
        }
    }
}

