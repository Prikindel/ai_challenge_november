package com.prike.mcpserver.tools

import com.prike.mcpserver.tools.handlers.GetChatHistoryHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_chat_history для MCP сервера
 * Делегирует обработку запросов GetChatHistoryHandler
 */
class ChatHistoryTool(
    private val handler: GetChatHistoryHandler
) {
    private val logger = LoggerFactory.getLogger(ChatHistoryTool::class.java)
    
    /**
     * Регистрация инструмента на сервере
     */
    fun register(server: Server) {
        server.addTool(
            name = "get_chat_history",
            description = "Получить историю переписки из веб-чата за указанный период времени. Используй Unix timestamp в миллисекундах (integer, не float/double). Текущее время указывается в системном промпте.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("startTime") {
                        put("type", "integer")
                        put("description", "Начало периода в миллисекундах (Unix timestamp). Должно быть целым числом (integer), не дробным. Используй текущее время из системного промпта, затем вычитай необходимое количество миллисекунд.")
                        put("format", "int64")
                    }
                    putJsonObject("endTime") {
                        put("type", "integer")
                        put("description", "Конец периода в миллисекундах (Unix timestamp). Должно быть целым числом (integer), не дробным. Обычно это текущее время из системного промпта.")
                        put("format", "int64")
                    }
                },
                required = listOf("startTime", "endTime")
            )
        ) { request ->
            logger.debug("Вызов инструмента get_chat_history с аргументами: ${request.arguments}")

            val startTime = extractLongParameter(request.arguments, "startTime")
                ?: throw IllegalArgumentException("startTime is required and must be a number")

            val endTime = extractLongParameter(request.arguments, "endTime")
                ?: throw IllegalArgumentException("endTime is required and must be a number")

            handler.handle(GetChatHistoryHandler.Params(startTime, endTime))
        }
    }

    /**
     * Извлечь Long параметр из JsonObject
     * Поддерживает как integer, так и double (конвертирует в Long)
     */
    private fun extractLongParameter(arguments: JsonObject, key: String): Long? {
        val value = arguments[key] ?: return null
        return when {
            value is JsonPrimitive -> {
                // Пробуем сначала как Long, потом как Double (если LLM пришлет double)
                value.longOrNull ?: value.doubleOrNull?.toLong()
            }
            value is Number -> value.toLong()
            else -> null
        }
    }
}

