package com.prike.domain.service

import com.prike.data.client.MCPClientInterface
import com.prike.domain.model.MCPTool
import com.prike.domain.model.MCPToolResult
import org.slf4j.LoggerFactory

/**
 * Сервис для роутинга запросов к MCP серверам
 */
class MCPRouterService(
    private val mcpConfigService: MCPConfigService,
    private val mcpClients: Map<String, MCPClientInterface>
) {
    private val logger = LoggerFactory.getLogger(MCPRouterService::class.java)
    
    /**
     * Получить список всех доступных инструментов из всех MCP серверов
     */
    suspend fun getAllAvailableTools(): List<MCPTool> {
        val enabledServers = mcpConfigService.getEnabledServers()
        logger.debug("Getting tools from ${enabledServers.size} enabled servers")
        logger.debug("Available clients: ${mcpClients.keys.joinToString()}")
        
        val allTools = enabledServers.flatMap { serverConfig ->
            logger.debug("Processing server: ${serverConfig.name}")
            val client = mcpClients[serverConfig.name]
            if (client == null) {
                logger.warn("MCP client not found for server: ${serverConfig.name}. Available clients: ${mcpClients.keys.joinToString()}")
                emptyList()
            } else {
                try {
                    // Некоторые клиенты могут возвращать инструменты без подключения
                    // (например, TelegramMCPClientAdapter)
                    val tools = client.listTools()
                    logger.debug("Server ${serverConfig.name} returned ${tools.size} tools")
                    if (tools.isEmpty() && !client.isConnected()) {
                        logger.debug("MCP client not connected and no tools available: ${serverConfig.name}")
                    }
                    tools
                } catch (e: Exception) {
                    logger.error("Failed to list tools from ${serverConfig.name}: ${e.message}", e)
                    emptyList()
                }
            }
        }
        
        logger.debug("Total tools retrieved: ${allTools.size}")
        return allTools
    }
    
    /**
     * Выполнить инструмент по имени сервера и инструмента
     */
    suspend fun executeTool(
        serverName: String,
        toolName: String,
        arguments: Map<String, Any>
    ): MCPToolResult {
        val client = mcpClients[serverName]
            ?: return MCPToolResult.failure("MCP server not found: $serverName")
        
        if (!client.isConnected()) {
            return MCPToolResult.failure("MCP server not connected: $serverName")
        }
        
        if (!mcpConfigService.isServerEnabled(serverName)) {
            return MCPToolResult.failure("MCP server is disabled: $serverName")
        }
        
        return try {
            client.callTool(toolName, arguments)
        } catch (e: Exception) {
            logger.error("Failed to execute tool $toolName from $serverName: ${e.message}", e)
            MCPToolResult.failure(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Получить описание всех доступных инструментов для LLM
     */
    suspend fun getToolsDescription(): String {
        val tools = getAllAvailableTools()
        
        if (tools.isEmpty()) {
            return "Нет доступных MCP инструментов."
        }
        
        return tools.groupBy { it.serverName }
            .map { (serverName, serverTools) ->
                """
                ## $serverName
                ${serverTools.joinToString("\n") { tool ->
                    "- **${tool.name}**: ${tool.description}"
                }}
                """.trimIndent()
            }
            .joinToString("\n\n")
    }
    
    /**
     * Получить список доступных серверов
     */
    fun getAvailableServers(): List<String> {
        return mcpConfigService.getEnabledServers().map { it.name }
    }
    
    /**
     * Проверить, доступен ли сервер
     * Возвращает true, если сервер включен в конфигурации и имеет клиент
     * Для некоторых клиентов (например, Telegram адаптер) подключение не обязательно
     */
    fun isServerAvailable(serverName: String): Boolean {
        if (!mcpConfigService.isServerEnabled(serverName)) {
            return false
        }
        val client = mcpClients[serverName] ?: return false
        // Для адаптеров и заглушек считаем доступными, если они есть в мапе
        // Реальное подключение проверяется отдельно через isConnected()
        return true
    }
    
    /**
     * Проверить, подключен ли сервер через MCP протокол
     */
    fun isServerConnected(serverName: String): Boolean {
        val client = mcpClients[serverName] ?: return false
        return client.isConnected()
    }
    
    /**
     * Подключить все клиенты
     */
    suspend fun connectAllClients() {
        val enabledServers = mcpConfigService.getEnabledServers()
        logger.info("Connecting ${enabledServers.size} enabled MCP clients...")
        enabledServers.forEach { serverConfig ->
            val client = mcpClients[serverConfig.name]
            if (client != null) {
                try {
                    client.connect()
                    logger.info("Successfully connected to ${serverConfig.name} MCP client.")
                } catch (e: Exception) {
                    logger.error("Failed to connect to ${serverConfig.name} MCP client: ${e.message}", e)
                }
            } else {
                logger.warn("No MCP client implementation found for server: ${serverConfig.name}")
            }
        }
    }
    
    /**
     * Отключить все клиенты
     */
    suspend fun disconnectAllClients() {
        logger.info("Disconnecting all MCP clients...")
        mcpClients.values.forEach { client ->
            try {
                client.disconnect()
            } catch (e: Exception) {
                logger.warn("Error disconnecting client: ${e.message}")
            }
        }
    }
}

