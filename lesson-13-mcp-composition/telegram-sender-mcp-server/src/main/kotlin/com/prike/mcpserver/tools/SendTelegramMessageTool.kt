package com.prike.mcpserver.tools

import com.prike.mcpserver.tools.handlers.SendTelegramMessageHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента send_telegram_message для MCP сервера
 */
class SendTelegramMessageTool(
    private val handler: SendTelegramMessageHandler
) {
    private val logger = LoggerFactory.getLogger(SendTelegramMessageTool::class.java)
    
    /**
     * Регистрация инструмента на сервере
     */
    fun register(server: Server) {
        server.addTool(
            name = "send_telegram_message",
            description = "Отправить сообщение пользователю в Telegram (личное сообщение). Используется для отправки summary или других сообщений пользователю.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("userId") {
                        put("type", "string")
                        put("description", "ID пользователя для отправки (личное сообщение)")
                    }
                    putJsonObject("message") {
                        put("type", "string")
                        put("description", "Текст сообщения для отправки (поддерживает Markdown)")
                    }
                },
                required = listOf("userId", "message")
            )
        ) { request ->
            logger.debug("Вызов инструмента send_telegram_message с аргументами: ${request.arguments}")
            
            val params = SendTelegramMessageHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

