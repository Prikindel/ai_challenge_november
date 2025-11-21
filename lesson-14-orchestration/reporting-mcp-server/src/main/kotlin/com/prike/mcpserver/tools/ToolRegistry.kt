package com.prike.mcpserver.tools

import com.prike.mcpserver.tools.handlers.GenerateReportHandler
import com.prike.mcpserver.tools.handlers.SaveToFileHandler
import com.prike.mcpserver.tools.handlers.SendTelegramMessageHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Реестр инструментов для Reporting MCP Server
 */
class ToolRegistry(
    private val generateReportHandler: GenerateReportHandler,
    private val saveToFileHandler: SaveToFileHandler,
    private val sendTelegramMessageHandler: SendTelegramMessageHandler
) {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    
    /**
     * Регистрация всех инструментов на сервере
     */
    fun registerTools(server: Server) {
        registerGenerateReport(server)
        registerSaveToFile(server)
        registerSendTelegramMessage(server)
        logger.info("Все инструменты зарегистрированы (3 инструмента)")
    }
    
    /**
     * Регистрация инструмента generate_report
     */
    private fun registerGenerateReport(server: Server) {
        server.addTool(
            name = "generate_report",
            description = """
                Генерирует отчёт из заголовка и содержимого в указанном формате.
                Поддерживает форматы Markdown и обычный текст.
                
                Используй этот инструмент, когда нужно сформировать структурированный отчёт.
                Например, для создания отчётов по анализу данных, статистике, результатам обработки.
                
                Параметры:
                - title: заголовок отчёта
                - content: содержимое отчёта (Markdown или текст)
                - format: формат отчёта ("markdown" или "text", по умолчанию "markdown")
                
                Возвращает JSON объект:
                {"report": "...", "format": "markdown", "length": 1000}
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Заголовок отчёта")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Содержимое отчёта (Markdown или текст)")
                    }
                    putJsonObject("format") {
                        put("type", "string")
                        put("description", "Формат отчёта: 'markdown' или 'text' (по умолчанию 'markdown')")
                    }
                },
                required = listOf("title", "content")
            )
        ) { request ->
            logger.debug("Вызов инструмента generate_report с аргументами: ${request.arguments}")

            val title = extractStringParameter(request.arguments, "title")
                ?: throw IllegalArgumentException("title is required and must be a string")

            val content = extractStringParameter(request.arguments, "content")
                ?: throw IllegalArgumentException("content is required and must be a string")

            val format = extractStringParameter(request.arguments, "format") ?: "markdown"

            generateReportHandler.handle(GenerateReportHandler.Params(title, content, format))
        }
    }
    
    /**
     * Регистрация инструмента save_to_file
     */
    private fun registerSaveToFile(server: Server) {
        server.addTool(
            name = "save_to_file",
            description = """
                Сохраняет содержимое в файл по указанному пути.
                Создаёт директорию, если её нет.
                
                Используй этот инструмент, когда нужно сохранить данные в файл.
                Например, для сохранения отчётов, результатов анализа, логов.
                
                Параметры:
                - filePath: путь к файлу (относительный или абсолютный)
                - content: содержимое для сохранения
                
                Возвращает JSON объект:
                {"success": true, "filePath": "...", "size": 1000}
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Путь к файлу (относительный или абсолютный)")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Содержимое для сохранения")
                    }
                },
                required = listOf("filePath", "content")
            )
        ) { request ->
            logger.debug("Вызов инструмента save_to_file с аргументами: ${request.arguments}")

            val filePath = extractStringParameter(request.arguments, "filePath")
                ?: throw IllegalArgumentException("filePath is required and must be a string")

            val content = extractStringParameter(request.arguments, "content")
                ?: throw IllegalArgumentException("content is required and must be a string")

            saveToFileHandler.handle(SaveToFileHandler.Params(filePath, content))
        }
    }
    
    /**
     * Регистрация инструмента send_telegram_message
     */
    private fun registerSendTelegramMessage(server: Server) {
        server.addTool(
            name = "send_telegram_message",
            description = """
                Отправляет сообщение в Telegram пользователю.
                Использует Telegram Bot API для отправки сообщений.
                
                Используй этот инструмент, когда нужно отправить сообщение пользователю в Telegram.
                Например, для отправки отчётов, уведомлений, результатов обработки.
                
                Параметры:
                - message: текст сообщения для отправки
                
                Примечание: ID пользователя берётся из конфигурации сервера (TELEGRAM_CHAT_ID).
                Не нужно передавать userId - он автоматически используется из конфигурации.
                
                Возвращает JSON объект:
                {"success": true, "messageId": 123} или {"success": false, "error": "..."}
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("message") {
                        put("type", "string")
                        put("description", "Текст сообщения для отправки")
                    }
                },
                required = listOf("message")
            )
        ) { request ->
            logger.debug("Вызов инструмента send_telegram_message с аргументами: ${request.arguments}")

            val message = extractStringParameter(request.arguments, "message")
                ?: throw IllegalArgumentException("message is required and must be a string")

            sendTelegramMessageHandler.handle(SendTelegramMessageHandler.Params(message))
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

