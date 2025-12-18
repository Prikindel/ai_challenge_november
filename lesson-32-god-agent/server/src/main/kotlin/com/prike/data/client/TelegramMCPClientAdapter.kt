package com.prike.data.client

import com.prike.domain.model.MCPTool
import com.prike.domain.model.MCPToolResult
import io.github.cdimascio.dotenv.dotenv
import kotlinx.serialization.json.*

/**
 * Адаптер для TelegramMCPClient, реализующий интерфейс MCPClientInterface
 */
class TelegramMCPClientAdapter(
    private val telegramMCPClient: TelegramMCPClient,
    private val serverName: String = "Telegram MCP"
) : MCPClientInterface {
    
    override suspend fun connect() {
        telegramMCPClient.connect()
    }
    
    override suspend fun disconnect() {
        telegramMCPClient.disconnect()
    }
    
    override fun isConnected(): Boolean {
        return telegramMCPClient.isConnected()
    }
    
    override suspend fun listTools(): List<MCPTool> {
        // Telegram MCP имеет инструмент send_telegram_message
        return listOf(
            MCPTool(
                serverName = serverName,
                name = "send_telegram_message",
                description = "Отправить сообщение в Telegram",
                parameters = mapOf(
                    "userId" to "string",
                    "message" to "string"
                )
            )
        )
    }
    
    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): MCPToolResult {
        if (toolName == "send_telegram_message") {
            // Если userId = "default", используем chat_id из конфигурации или переменных окружения
            val userIdRaw = arguments["userId"] as? String ?: return MCPToolResult.failure("userId is required")
            val userId = if (userIdRaw == "default") {
                // Пытаемся получить chat_id из переменных окружения через dotenv
                val dotenvInstance = try {
                    dotenv {
                        ignoreIfMissing = true
                    }
                } catch (e: Exception) {
                    null
                }
                dotenvInstance?.get("TELEGRAM_CHAT_ID") 
                    ?: dotenvInstance?.get("TELEGRAM_GROUP_ID")
                    ?: System.getenv("TELEGRAM_CHAT_ID") 
                    ?: System.getenv("TELEGRAM_GROUP_ID") 
                    ?: "default"
            } else {
                userIdRaw
            }
            
            val message = arguments["message"] as? String ?: return MCPToolResult.failure("message is required")
            
            // Если Telegram MCP сервер не подключен, пытаемся использовать прямую отправку через Telegram API
            if (!telegramMCPClient.isConnected()) {
                return sendMessageDirectly(userId, message)
            }
            
            val success = telegramMCPClient.sendMessage(userId, message)
            return if (success) {
                MCPToolResult.success("Message sent successfully to Telegram (userId: $userId)")
            } else {
                // Если MCP не сработал, пробуем прямую отправку
                sendMessageDirectly(userId, message)
            }
        }
        
        return MCPToolResult.failure("Unknown tool: $toolName")
    }
    
    /**
     * Прямая отправка сообщения через Telegram API (fallback, если MCP сервер недоступен)
     */
    private suspend fun sendMessageDirectly(userId: String, message: String): MCPToolResult {
        return try {
            // Используем dotenv для получения переменных из .env файла
            val dotenvInstance = try {
                dotenv {
                    ignoreIfMissing = true
                }
            } catch (e: Exception) {
                null
            }
            
            val botToken = dotenvInstance?.get("TELEGRAM_BOT_TOKEN") 
                ?: System.getenv("TELEGRAM_BOT_TOKEN")
                ?: return MCPToolResult.failure("TELEGRAM_BOT_TOKEN not found in environment variables or .env file")
            
            val chatId = if (userId == "default") {
                dotenvInstance?.get("TELEGRAM_CHAT_ID") 
                    ?: dotenvInstance?.get("TELEGRAM_GROUP_ID")
                    ?: System.getenv("TELEGRAM_CHAT_ID") 
                    ?: System.getenv("TELEGRAM_GROUP_ID")
                    ?: return MCPToolResult.failure("TELEGRAM_CHAT_ID or TELEGRAM_GROUP_ID not found in environment variables or .env file")
            } else {
                userId
            }
            
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val requestBody = buildJsonObject {
                put("chat_id", chatId)
                put("text", message)
            }
            
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                            Json.encodeToString(requestBody)
                        ))
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()
                )
            }
            
            if (response.statusCode() == 200) {
                MCPToolResult.success("Message sent successfully to Telegram via direct API (chatId: $chatId)")
            } else {
                MCPToolResult.failure("Failed to send message: HTTP ${response.statusCode()}, response: ${response.body()}")
            }
        } catch (e: Exception) {
            MCPToolResult.failure("Failed to send message via direct API: ${e.message}")
        }
    }
}

