package com.prike.mcpserver.tools

import com.prike.mcpserver.tools.handlers.GetTelegramMessagesHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_telegram_messages для MCP сервера
 * Делегирует обработку запросов GetTelegramMessagesHandler
 */
class TelegramMessagesTool(
    private val handler: GetTelegramMessagesHandler
) {
    private val logger = LoggerFactory.getLogger(TelegramMessagesTool::class.java)
    
    /**
     * Регистрация инструмента на сервере
     * groupId берется из конфигурации (env), не передается как параметр
     */
    fun register(server: Server) {
        server.addTool(
            name = "get_telegram_messages",
            description = "Получить сообщения из Telegram группы за указанный период времени. ID группы берется из конфигурации (TELEGRAM_GROUP_ID).",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("startTime") {
                        put("type", "number")
                        put("description", "Начало периода (Unix timestamp в миллисекундах)")
                    }
                    putJsonObject("endTime") {
                        put("type", "number")
                        put("description", "Конец периода (Unix timestamp в миллисекундах)")
                    }
                },
                required = listOf("startTime", "endTime")
            )
        ) { request ->
            logger.debug("Вызов инструмента get_telegram_messages с аргументами: ${request.arguments}")

            val startTime = extractLongParameter(request.arguments, "startTime")
                ?: throw IllegalArgumentException("startTime is required and must be a number")

            val endTime = extractLongParameter(request.arguments, "endTime")
                ?: throw IllegalArgumentException("endTime is required and must be a number")

            handler.handle(GetTelegramMessagesHandler.Params(startTime, endTime))
        }
    }

    /**
     * Извлечь Long параметр из JsonObject
     */
    private fun extractLongParameter(arguments: JsonObject, key: String): Long? {
        val value = arguments[key] ?: return null
        return when {
            value is JsonPrimitive -> value.longOrNull
            value is Number -> value.toLong()
            else -> null
        }
    }
}

