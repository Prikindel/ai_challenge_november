package com.prike.mcpserver.tools

import com.prike.mcpserver.tools.handlers.GetChatHistoryHandler
import com.prike.mcpserver.tools.handlers.GetTelegramMessagesHandler
import com.prike.mcpserver.tools.handlers.GetFileContentHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Реестр инструментов для Data Collection MCP Server
 */
class ToolRegistry(
    private val getChatHistoryHandler: GetChatHistoryHandler,
    private val getTelegramMessagesHandler: GetTelegramMessagesHandler,
    private val getFileContentHandler: GetFileContentHandler
) {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    
    /**
     * Регистрация всех инструментов на сервере
     */
    fun registerTools(server: Server) {
        registerGetChatHistory(server)
        registerGetTelegramMessages(server)
        registerGetFileContent(server)
        logger.info("Все инструменты зарегистрированы (3 инструмента)")
    }
    
    /**
     * Регистрация инструмента get_chat_history
     */
    private fun registerGetChatHistory(server: Server) {
        server.addTool(
            name = "get_chat_history",
            description = """
                Получить историю переписки из веб-чата за указанный период времени.
                Возвращает массив сообщений с ролями (user/assistant) и содержимым.
                
                Используй этот инструмент, когда нужно получить переписку из веб-чата.
                Например, для анализа сообщений, суммаризации или поиска информации.
                
                Параметры:
                - startTime: начало периода (Unix timestamp в миллисекундах)
                - endTime: конец периода (Unix timestamp в миллисекундах)
                
                Возвращает JSON массив сообщений:
                [{"role": "user|assistant", "content": "...", "timestamp": ...}]
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("startTime") {
                        put("type", "number")
                        put("description", "Начало периода в миллисекундах (Unix timestamp * 1000)")
                    }
                    putJsonObject("endTime") {
                        put("type", "number")
                        put("description", "Конец периода в миллисекундах (Unix timestamp * 1000)")
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

            getChatHistoryHandler.handle(GetChatHistoryHandler.Params(startTime, endTime))
        }
    }
    
    /**
     * Регистрация инструмента get_telegram_messages
     */
    private fun registerGetTelegramMessages(server: Server) {
        server.addTool(
            name = "get_telegram_messages",
            description = """
                Получить сообщения из Telegram за указанный период времени.
                Возвращает массив сообщений из указанной группы Telegram.
                
                Используй этот инструмент, когда нужно получить сообщения из Telegram.
                Например, для анализа переписки в группе, поиска информации или суммаризации.
                
                Параметры:
                - startTime: начало периода (Unix timestamp в миллисекундах)
                - endTime: конец периода (Unix timestamp в миллисекундах)
                
                Примечание: ID группы берётся из конфигурации, не нужно передавать его в параметрах.
                
                Возвращает JSON массив сообщений:
                [{"id": "...", "content": "...", "author": "...", "timestamp": ...}]
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("startTime") {
                        put("type", "number")
                        put("description", "Начало периода в миллисекундах (Unix timestamp * 1000)")
                    }
                    putJsonObject("endTime") {
                        put("type", "number")
                        put("description", "Конец периода в миллисекундах (Unix timestamp * 1000)")
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

            getTelegramMessagesHandler.handle(GetTelegramMessagesHandler.Params(startTime, endTime))
        }
    }
    
    /**
     * Регистрация инструмента get_file_content
     */
    private fun registerGetFileContent(server: Server) {
        server.addTool(
            name = "get_file_content",
            description = """
                Прочитать содержимое файла по указанному пути.
                Поддерживает как абсолютные, так и относительные пути.
                
                Используй этот инструмент, когда нужно прочитать содержимое файла.
                Например, для чтения конфигурационных файлов, логов, документов и т.д.
                
                Параметры:
                - filePath: путь к файлу (относительный или абсолютный)
                
                Возвращает содержимое файла как текст.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Путь к файлу (относительный или абсолютный)")
                    }
                },
                required = listOf("filePath")
            )
        ) { request ->
            logger.debug("Вызов инструмента get_file_content с аргументами: ${request.arguments}")

            val filePath = extractStringParameter(request.arguments, "filePath")
                ?: throw IllegalArgumentException("filePath is required and must be a string")

            getFileContentHandler.handle(GetFileContentHandler.Params(filePath))
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
    
    /**
     * Извлечь String параметр из JsonObject
     */
    private fun extractStringParameter(arguments: JsonObject, key: String): String? {
        val value = arguments[key] ?: return null
        return when {
            value is JsonPrimitive -> value.contentOrNull
            value is String -> value
            else -> null
        }
    }
}

