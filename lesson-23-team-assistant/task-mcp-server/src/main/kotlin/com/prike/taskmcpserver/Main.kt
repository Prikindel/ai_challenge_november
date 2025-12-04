package com.prike.taskmcpserver

import com.prike.taskmcpserver.server.TaskMCPServer
import com.prike.taskmcpserver.storage.InMemoryTaskStorage
import com.prike.taskmcpserver.tools.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("TaskMCPServerMain")
    
    try {
        // Создание хранилища данных
        val storage = InMemoryTaskStorage()
        
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

