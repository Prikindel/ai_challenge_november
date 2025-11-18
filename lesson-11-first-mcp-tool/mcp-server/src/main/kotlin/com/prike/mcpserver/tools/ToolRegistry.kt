package com.prike.mcpserver.tools

import com.prike.mcpserver.api.TelegramApiClient
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class ToolRegistry(
    private val apiClient: TelegramApiClient,
    private val defaultChatId: String? = null  // ID чата по умолчанию (захардкоженный)
) {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    
    fun registerTools(server: Server) {
        // Регистрация инструмента get_bot_info
        server.addTool(
            name = "get_bot_info",
            description = "Получить информацию о Telegram боте (getMe)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    // Нет параметров для этого инструмента
                },
                required = emptyList()
            )
        ) { request ->
            try {
                val botInfo = apiClient.getMe()
                
                // Формируем читаемый текст вместо JSON
                val botInfoText = buildString {
                    append("Информация о Telegram боте:\n")
                    append("- ID: ${botInfo.id}\n")
                    append("- Имя: ${botInfo.firstName}\n")
                    append("- Это бот: ${if (botInfo.isBot) "Да" else "Нет"}\n")
                    botInfo.username?.let { append("- Username: @$it\n") }
                    botInfo.languageCode?.let { append("- Язык: $it\n") }
                }.trim()

                CallToolResult(
                    content = listOf(
                        TextContent(
                            text = botInfoText.trim()
                        )
                    )
                )
            } catch (e: Exception) {
                logger.error("Error in get_bot_info: ${e.message}", e)
                CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка при получении информации о боте: ${e.message}"
                        )
                    )
                )
            }
        }
        
        // Регистрация инструмента send_message
        server.addTool(
            name = "send_message",
            description = "Отправить сообщение в Telegram чат. Используется захардкоженный ID чата из конфигурации.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "Текст сообщения для отправки")
                    }
                },
                required = listOf("text")
            )
        ) { request ->
            try {
                // Используем захардкоженный chatId из конфигурации
                val chatId = defaultChatId
                    ?: throw IllegalArgumentException("TELEGRAM_CHAT_ID not set in .env file. Please set it in the project root .env file")
                
                // Извлекаем text из аргументов
                val textJson = request.arguments["text"]
                val text = when {
                    textJson != null -> {
                        when {
                            textJson is JsonPrimitive -> textJson.contentOrNull ?: textJson.toString().trim('"')
                            else -> textJson.toString().trim('"')
                        }
                    }
                    else -> null
                } ?: throw IllegalArgumentException("text is required")
                
                val message = apiClient.sendMessage(chatId, text)
                
                // Формируем читаемый текст вместо JSON
                val messageText = buildString {
                    append("Сообщение успешно отправлено в Telegram!\n")
                    append("- ID сообщения: ${message.messageId}\n")
                    append("- ID чата: ${message.chat.id}\n")
                    append("- Тип чата: ${message.chat.type}\n")
                    append("- Текст сообщения: \"${message.text ?: text}\"")
                }
                
                CallToolResult(
                    content = listOf(
                        TextContent(
                            text = messageText.trim()
                        )
                    )
                )
            } catch (e: Exception) {
                logger.error("Error in send_message: ${e.message}", e)
                CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка при отправке сообщения: ${e.message}"
                        )
                    )
                )
            }
        }
    }
}

