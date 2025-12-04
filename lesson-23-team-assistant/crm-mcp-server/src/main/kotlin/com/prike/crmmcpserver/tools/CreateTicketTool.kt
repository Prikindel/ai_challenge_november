package com.prike.crmmcpserver.tools

import com.prike.crmmcpserver.tools.handlers.CreateTicketHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента create_ticket для MCP сервера
 */
class CreateTicketTool(
    private val handler: CreateTicketHandler
) {
    private val logger = LoggerFactory.getLogger(CreateTicketTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "create_ticket",
            description = "Создать новый тикет поддержки. Возвращает данные созданного тикета.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("userId", buildJsonObject {
                        put("type", "string")
                        put("description", "ID пользователя")
                    })
                    put("subject", buildJsonObject {
                        put("type", "string")
                        put("description", "Тема тикета")
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "Описание проблемы")
                    })
                },
                required = listOf("userId", "subject", "description")
            )
        ) { request ->
            logger.debug("Вызов инструмента create_ticket с аргументами: ${request.arguments}")
            val params = CreateTicketHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

