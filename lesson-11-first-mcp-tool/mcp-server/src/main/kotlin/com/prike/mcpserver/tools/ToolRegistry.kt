package com.prike.mcpserver.tools

import com.prike.mcpserver.api.TelegramApiClient
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class ToolRegistry(
    private val apiClient: TelegramApiClient
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
                logger.info("Calling get_bot_info tool")
                val botInfo = apiClient.getMe()
                
                val botInfoJson = buildJsonObject {
                    put("id", botInfo.id)
                    put("is_bot", botInfo.isBot)
                    put("first_name", botInfo.firstName)
                    botInfo.username?.let { put("username", it) }
                    botInfo.languageCode?.let { put("language_code", it) }
                }

                CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Информация о боте:\n${botInfoJson.toString()}"
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
            description = "Отправить сообщение в Telegram чат",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("chatId") {
                        put("type", "string")
                        put("description", "ID чата в Telegram (может быть числом или строкой)")
                    }
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "Текст сообщения для отправки")
                    }
                },
                required = listOf("chatId", "text")
            )
        ) { request ->
            try {
                logger.info("Calling send_message tool with arguments: ${request.arguments}")
                
                val chatId = request.arguments.get("chatId") as? String
                    ?: throw IllegalArgumentException("chatId is required")
                
                val text = request.arguments.get("text") as? String
                    ?: throw IllegalArgumentException("text is required")
                
                val message = apiClient.sendMessage(chatId, text)
                
                val messageJson = buildJsonObject {
                    put("message_id", message.messageId)
                    put("chat_id", message.chat.id)
                    put("chat_type", message.chat.type)
                    message.text?.let { put("text", it) }
                }
                
                CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Сообщение отправлено:\n${messageJson.toString()}"
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

