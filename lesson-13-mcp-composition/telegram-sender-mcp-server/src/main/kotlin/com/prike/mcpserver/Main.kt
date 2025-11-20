package com.prike.mcpserver

import com.prike.mcpserver.server.MCPServer
import com.prike.mcpserver.telegram.TelegramBotClient
import com.prike.mcpserver.tools.SendTelegramMessageTool
import com.prike.mcpserver.tools.handlers.SendTelegramMessageHandler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("TelegramSenderMCPServerMain")
    
    try {
        // Загрузка конфигурации
        val config = Config.load()
        logger.info("Configuration loaded: ${config.serverInfo.name}")
        
        // Создание HTTP клиента для Telegram API
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // Создание Telegram Bot Client для отправки сообщений
        val telegramBotClient = TelegramBotClient(
            httpClient = httpClient,
            baseUrl = "https://api.telegram.org",
            token = config.telegram.botToken
        )
        
        // Создание обработчика для отправки сообщений Telegram
        val sendTelegramMessageHandler = SendTelegramMessageHandler(telegramBotClient)
        
        // Создание инструмента для отправки сообщений Telegram
        val sendTelegramMessageTool = SendTelegramMessageTool(sendTelegramMessageHandler)
        
        // Создание и запуск MCP сервера
        val server = MCPServer(
            serverInfo = Implementation(
                name = config.serverInfo.name,
                version = config.serverInfo.version
            )
        ) { mcpServer ->
            // Регистрация инструментов
            sendTelegramMessageTool.register(mcpServer)
            logger.info("Инструмент send_telegram_message зарегистрирован")
        }
        
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start MCP server: ${e.message}", e)
        System.exit(1)
    }
}

