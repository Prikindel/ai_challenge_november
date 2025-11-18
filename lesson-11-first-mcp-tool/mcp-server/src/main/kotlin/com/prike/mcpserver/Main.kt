package com.prike.mcpserver

import com.prike.mcpserver.server.MCPServer
import com.prike.mcpserver.tools.ToolRegistry
import com.prike.mcpserver.api.TelegramApiClient
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("MCPServerMain")
    
    try {
        // Загрузка конфигурации
        val config = Config.load()
        logger.info("Configuration loaded: ${config.serverInfo.name}")
        
        // Создание HTTP клиента
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // Создание API клиента
        val apiClient = TelegramApiClient(
            httpClient = httpClient,
            baseUrl = config.api.baseUrl,
            token = config.api.token
        )
        
        // Создание реестра инструментов
        val toolRegistry = ToolRegistry(
            apiClient = apiClient,
            defaultChatId = config.api.defaultChatId
        )
        
        // Создание и запуск MCP сервера
        val server = MCPServer(
            serverInfo = Implementation(
                name = config.serverInfo.name,
                version = config.serverInfo.version
            ),
            toolRegistry = toolRegistry
        )
        
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start MCP server: ${e.message}", e)
        System.exit(1)
    }
}

