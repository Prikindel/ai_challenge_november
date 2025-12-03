package com.prike.crmmcpserver.tools

import com.prike.crmmcpserver.tools.handlers.GetUserHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_user для MCP сервера
 */
class GetUserTool(
    private val handler: GetUserHandler
) {
    private val logger = LoggerFactory.getLogger(GetUserTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "get_user",
            description = "Получить информацию о пользователе по ID или email. Возвращает данные пользователя: id, email, name, status, subscription, createdAt.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("userId", buildJsonObject {
                        put("type", "string")
                        put("description", "ID пользователя")
                    })
                    put("email", buildJsonObject {
                        put("type", "string")
                        put("description", "Email пользователя")
                    })
                },
                required = emptyList() // Один из параметров должен быть указан
            )
        ) { request ->
            logger.debug("Вызов инструмента get_user с аргументами: ${request.arguments}")
            val params = GetUserHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

