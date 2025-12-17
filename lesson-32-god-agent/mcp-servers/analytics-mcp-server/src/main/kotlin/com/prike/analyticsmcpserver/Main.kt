package com.prike.analyticsmcpserver

import com.prike.analyticsmcpserver.server.AnalyticsMCPServer
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory
import java.io.File

fun main() {
    val logger = LoggerFactory.getLogger("AnalyticsMCPServerMain")
    
    try {
        // Определяем корень проекта (текущая рабочая директория)
        val projectRoot = File(System.getProperty("user.dir"))
        logger.info("Analytics MCP Server starting...")
        logger.info("Project root: ${projectRoot.absolutePath}")
        
        // Создание и запуск MCP сервера
        val server = AnalyticsMCPServer(
            serverInfo = Implementation(
                name = "analytics-mcp-server",
                version = "1.0.0"
            ),
            projectRoot = projectRoot
        )
        
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start Analytics MCP server: ${e.message}", e)
        System.exit(1)
    }
}

