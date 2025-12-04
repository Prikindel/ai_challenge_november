package com.prike.crmmcpserver.tools

import com.prike.crmmcpserver.tools.handlers.AddTicketMessageHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента add_ticket_message для MCP сервера
 */
class AddTicketMessageTool(
    private val handler: AddTicketMessageHandler
) {
    private val logger = LoggerFactory.getLogger(AddTicketMessageTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "add_ticket_message",
            description = "Добавить сообщение в тикет. Автор может быть 'user' или 'support'. Возвращает данные созданного сообщения.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("ticketId", buildJsonObject {
                        put("type", "string")
                        put("description", "ID тикета")
                    })
                    put("author", buildJsonObject {
                        put("type", "string")
                        put("description", "Автор сообщения: 'user' или 'support'")
                    })
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "Текст сообщения")
                    })
                },
                required = listOf("ticketId", "author", "content")
            )
        ) { request ->
            logger.debug("Вызов инструмента add_ticket_message с аргументами: ${request.arguments}")
            val params = AddTicketMessageHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

