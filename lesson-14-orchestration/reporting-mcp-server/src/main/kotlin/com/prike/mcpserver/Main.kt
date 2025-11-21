package com.prike.mcpserver

import com.prike.mcpserver.server.MCPServer
import com.prike.mcpserver.telegram.TelegramBotService
import com.prike.mcpserver.tools.ToolRegistry
import com.prike.mcpserver.tools.handlers.GenerateReportHandler
import com.prike.mcpserver.tools.handlers.SaveToFileHandler
import com.prike.mcpserver.tools.handlers.SendTelegramMessageHandler
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("ReportingMCPServerMain")
    
    try {
        // Загрузка конфигурации
        val config = Config.load()
        logger.info("Configuration loaded: ${config.serverInfo.name}")
        
        // Создание Telegram Bot Service
        val telegramBotService = TelegramBotService(
            botToken = config.telegram.botToken,
            defaultChatId = config.telegram.chatId
        )
        
        // Создание обработчиков инструментов
        val generateReportHandler = GenerateReportHandler()
        val saveToFileHandler = SaveToFileHandler(
            basePath = config.fileSystem.basePath
        )
        val sendTelegramMessageHandler = SendTelegramMessageHandler(
            telegramBotService = telegramBotService,
            chatId = config.telegram.chatId
        )
        
        // Создание реестра инструментов
        val toolRegistry = ToolRegistry(
            generateReportHandler = generateReportHandler,
            saveToFileHandler = saveToFileHandler,
            sendTelegramMessageHandler = sendTelegramMessageHandler
        )
        
        // Создание и запуск MCP сервера
        val server = MCPServer(
            serverInfo = Implementation(
                name = config.serverInfo.name,
                version = config.serverInfo.version
            )
        ) { mcpServer ->
            // Регистрация инструментов
            toolRegistry.registerTools(mcpServer)
        }
        
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start MCP server: ${e.message}", e)
        System.exit(1)
    }
}

