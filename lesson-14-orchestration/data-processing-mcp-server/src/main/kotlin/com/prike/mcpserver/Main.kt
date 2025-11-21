package com.prike.mcpserver

import com.prike.mcpserver.server.MCPServer
import com.prike.mcpserver.tools.ToolRegistry
import com.prike.mcpserver.tools.handlers.AnalyzeSentimentHandler
import com.prike.mcpserver.tools.handlers.ExtractKeywordsHandler
import com.prike.mcpserver.tools.handlers.CalculateStatisticsHandler
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("DataProcessingMCPServerMain")
    
    try {
        logger.info("Starting Data Processing MCP Server")
        
        // Создание обработчиков инструментов
        val analyzeSentimentHandler = AnalyzeSentimentHandler()
        val extractKeywordsHandler = ExtractKeywordsHandler()
        val calculateStatisticsHandler = CalculateStatisticsHandler()
        
        // Создание реестра инструментов
        val toolRegistry = ToolRegistry(
            analyzeSentimentHandler = analyzeSentimentHandler,
            extractKeywordsHandler = extractKeywordsHandler,
            calculateStatisticsHandler = calculateStatisticsHandler
        )
        
        // Создание и запуск MCP сервера
        val server = MCPServer(
            serverInfo = Implementation(
                name = "Data Processing MCP Server",
                version = "1.0.0"
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

