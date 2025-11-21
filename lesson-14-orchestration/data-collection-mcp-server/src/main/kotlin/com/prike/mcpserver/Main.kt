package com.prike.mcpserver

import com.prike.mcpserver.data.repository.ChatHistoryRepository
import com.prike.mcpserver.data.repository.TelegramMessageRepository
import com.prike.mcpserver.data.repository.FileRepository
import com.prike.mcpserver.server.MCPServer
import com.prike.mcpserver.tools.ToolRegistry
import com.prike.mcpserver.tools.handlers.GetChatHistoryHandler
import com.prike.mcpserver.tools.handlers.GetTelegramMessagesHandler
import com.prike.mcpserver.tools.handlers.GetFileContentHandler
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory
import java.io.File

fun main() {
    val logger = LoggerFactory.getLogger("DataCollectionMCPServerMain")
    
    try {
        // Загрузка конфигурации
        val config = Config.load()
        logger.info("Configuration loaded: ${config.serverInfo.name}")
        
        // Проверка существования БД для веб-чата
        val chatHistoryDbFile = File(config.database.chatHistory.path)
        if (!chatHistoryDbFile.exists()) {
            logger.warn("База данных для веб-чата не найдена: ${chatHistoryDbFile.absolutePath}")
            logger.warn("Убедитесь, что lesson-09-external-memory/data/memory.db существует")
        }
        
        // Проверка существования БД для Telegram
        val telegramDbFile = File(config.database.telegramMessages.path)
        if (!telegramDbFile.exists()) {
            logger.warn("База данных для Telegram не найдена: ${telegramDbFile.absolutePath}")
            logger.warn("Убедитесь, что lesson-12-reminder-mcp/data/summary.db существует")
        }
        
        // Создание репозиториев
        val chatHistoryRepository = ChatHistoryRepository(
            databasePath = config.database.chatHistory.path
        )
        
        val telegramMessageRepository = TelegramMessageRepository(
            databasePath = config.database.telegramMessages.path
        )
        
        val fileRepository = FileRepository(
            basePath = config.fileSystem.basePath
        )
        
        // Создание обработчиков инструментов
        val getChatHistoryHandler = GetChatHistoryHandler(chatHistoryRepository)
        val getTelegramMessagesHandler = GetTelegramMessagesHandler(
            telegramMessageRepository = telegramMessageRepository,
            groupId = config.telegram.groupId
        )
        val getFileContentHandler = GetFileContentHandler(fileRepository)
        
        // Создание реестра инструментов
        val toolRegistry = ToolRegistry(
            getChatHistoryHandler = getChatHistoryHandler,
            getTelegramMessagesHandler = getTelegramMessagesHandler,
            getFileContentHandler = getFileContentHandler
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

