package com.prike.gitmcpserver

import com.prike.gitmcpserver.server.GitMCPServer
import com.prike.gitmcpserver.tools.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory
import java.io.File

fun main() {
    val logger = LoggerFactory.getLogger("GitMCPServerMain")
    
    try {
        // Определяем корень проекта (текущая рабочая директория)
        val projectRoot = File(System.getProperty("user.dir"))
        logger.info("Project root: ${projectRoot.absolutePath}")
        
        // Создание реестра инструментов с projectRoot
        val toolRegistry = ToolRegistry(projectRoot = projectRoot)
        
        // Создание и запуск MCP сервера
        val server = GitMCPServer(
            serverInfo = Implementation(
                name = "git-mcp-server",
                version = "1.0.0"
            ),
            toolRegistry = toolRegistry
        )
        
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start Git MCP server: ${e.message}", e)
        System.exit(1)
    }
}

