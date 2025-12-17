package com.prike.data.client

import com.prike.domain.model.MCPTool
import com.prike.domain.model.MCPToolResult

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
            val userId = arguments["userId"] as? String ?: return MCPToolResult.failure("userId is required")
            val message = arguments["message"] as? String ?: return MCPToolResult.failure("message is required")
            
            val success = telegramMCPClient.sendMessage(userId, message)
            return if (success) {
                MCPToolResult.success("Message sent successfully")
            } else {
                MCPToolResult.failure("Failed to send message")
            }
        }
        
        return MCPToolResult.failure("Unknown tool: $toolName")
    }
}

