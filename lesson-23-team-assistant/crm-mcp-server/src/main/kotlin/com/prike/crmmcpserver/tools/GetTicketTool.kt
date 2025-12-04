package com.prike.crmmcpserver.tools

import com.prike.crmmcpserver.tools.handlers.GetTicketHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_ticket для MCP сервера
 */
class GetTicketTool(
    private val handler: GetTicketHandler
) {
    private val logger = LoggerFactory.getLogger(GetTicketTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "get_ticket",
            description = "Получить информацию о тикете по ID. Возвращает данные тикета: id, userId, subject, description, status, priority, messages, createdAt, updatedAt.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("ticketId", buildJsonObject {
                        put("type", "string")
                        put("description", "ID тикета")
                    })
                },
                required = listOf("ticketId")
            )
        ) { request ->
            logger.debug("Вызов инструмента get_ticket с аргументами: ${request.arguments}")
            val params = GetTicketHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

