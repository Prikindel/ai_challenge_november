package com.prike.crmmcpserver

import com.prike.crmmcpserver.server.CRMMCPServer
import com.prike.crmmcpserver.storage.InMemoryCRMStorage
import com.prike.crmmcpserver.tools.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("CRMMCPServerMain")
    
    try {
        // Создание хранилища данных
        val storage = InMemoryCRMStorage()
        
        // Создание реестра инструментов
        val toolRegistry = ToolRegistry(storage = storage)
        
        // Создание и запуск MCP сервера
        val server = CRMMCPServer(
            serverInfo = Implementation(
                name = "crm-mcp-server",
                version = "1.0.0"
            ),
            toolRegistry = toolRegistry
        )
        
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start CRM MCP server: ${e.message}", e)
        System.exit(1)
    }
}

