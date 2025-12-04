package com.prike.taskmcpserver

import com.prike.taskmcpserver.server.TaskMCPServer
import com.prike.taskmcpserver.storage.TaskRepository
import java.io.File
import com.prike.taskmcpserver.tools.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("TaskMCPServerMain")
    
    try {
        // Определяем путь к БД (относительно текущей директории проекта)
        val dbPath = System.getenv("TASK_DB_PATH") ?: 
            File("data", "tasks.db").absolutePath
        
        logger.info("Используется база данных задач: $dbPath")
        
        // Создание хранилища данных (SQLite)
        val storage = TaskRepository(databasePath = dbPath)
        
        // Создание реестра инструментов
        val toolRegistry = ToolRegistry(storage = storage)
        
        // Создание и запуск MCP сервера
        val server = TaskMCPServer(
            serverInfo = Implementation(
                name = "task-mcp-server",
                version = "1.0.0"
            ),
            toolRegistry = toolRegistry
        )
        
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start Task MCP server: ${e.message}", e)
        System.exit(1)
    }
}

