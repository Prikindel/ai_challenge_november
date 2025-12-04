package com.prike.crmmcpserver.tools

import com.prike.crmmcpserver.tools.handlers.GetUserTicketsHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_user_tickets для MCP сервера
 */
class GetUserTicketsTool(
    private val handler: GetUserTicketsHandler
) {
    private val logger = LoggerFactory.getLogger(GetUserTicketsTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "get_user_tickets",
            description = "Получить все тикеты пользователя по userId. Возвращает список тикетов с основной информацией.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("userId", buildJsonObject {
                        put("type", "string")
                        put("description", "ID пользователя")
                    })
                },
                required = listOf("userId")
            )
        ) { request ->
            logger.debug("Вызов инструмента get_user_tickets с аргументами: ${request.arguments}")
            val params = GetUserTicketsHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

