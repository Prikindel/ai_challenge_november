package com.prike.mcpserver

import com.prike.mcpserver.data.repository.ChatHistoryRepository
import com.prike.mcpserver.server.MCPServer
import com.prike.mcpserver.tools.ChatHistoryTool
import com.prike.mcpserver.tools.handlers.GetChatHistoryHandler
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory
import java.io.File

fun main() {
    val logger = LoggerFactory.getLogger("MCPServerMain")
    
    try {
        // Загрузка конфигурации
        val config = Config.load()
        logger.info("Configuration loaded: ${config.serverInfo.name}")
        
        // Проверка существования БД
        val dbFile = File(config.webChat.memoryDbPath)
        if (!dbFile.exists()) {
            logger.warn("База данных не найдена: ${dbFile.absolutePath}")
            logger.warn("Убедитесь, что lesson-09-external-memory/data/memory.db существует")
        }
        
        // Создание репозитория для чтения истории чата
        val chatHistoryRepository = ChatHistoryRepository(
            databasePath = config.webChat.memoryDbPath
        )
        
        // Создание обработчика инструмента
        val getChatHistoryHandler = GetChatHistoryHandler(chatHistoryRepository)
        
        // Создание инструмента
        val chatHistoryTool = ChatHistoryTool(getChatHistoryHandler)
        
        // Создание и запуск MCP сервера
        val server = MCPServer(
            serverInfo = Implementation(
                name = config.serverInfo.name,
                version = config.serverInfo.version
            )
        ) { mcpServer ->
            // Регистрация инструментов
            chatHistoryTool.register(mcpServer)
            logger.info("Инструмент get_chat_history зарегистрирован")
        }
        
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start MCP server: ${e.message}", e)
        System.exit(1)
    }
}

