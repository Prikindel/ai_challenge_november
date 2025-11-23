package com.prike.data.client

import com.prike.config.MCPConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Менеджер для управления несколькими MCP клиентами
 */
class MCPClientManager(
    private val config: MCPConfig,
    private val lessonRoot: File
) {
    private val logger = LoggerFactory.getLogger(MCPClientManager::class.java)
    private val clients = mutableMapOf<String, MCPClient>()
    
    /**
     * Подключиться ко всем MCP серверам из конфигурации
     */
    suspend fun initialize() {
        logger.info("Connecting to MCP servers...")
        
        if (config.servers.isEmpty()) {
            logger.warn("No MCP servers found in configuration")
            return
        }
        
        coroutineScope {
            val connectionJobs = config.servers.map { serverConfig ->
                async {
                    try {
                        logger.debug("Starting connection job for server ${serverConfig.id}")
                        connectToServer(serverConfig)
                        logger.debug("Connection job completed successfully for server ${serverConfig.id}")
                        true
                    } catch (e: Exception) {
                        logger.error("Failed to connect to server ${serverConfig.id}: ${e.message}", e)
                        false
                    }
                }
            }
            
            val results = connectionJobs.awaitAll()
            val successCount = results.count { it }
            logger.info("Connection results: $successCount/${config.servers.size} servers connected successfully")
        }
        
        logger.info("Connected to ${clients.size} MCP server(s): ${clients.keys.joinToString(", ")}")
    }
    
    /**
     * Подключиться к конкретному серверу
     */
    private suspend fun connectToServer(serverConfig: com.prike.config.MCPServerConfig) {
        if (clients.containsKey(serverConfig.id)) {
            logger.warn("Server ${serverConfig.id} is already connected")
            return
        }
        
        logger.info("Connecting to server: ${serverConfig.name} (${serverConfig.id})")
        
        try {
            val client = MCPClient(serverConfig.id)
            logger.debug("Created MCPClient for ${serverConfig.id}, starting connection...")
            client.connectToServer(serverConfig.jarPath, lessonRoot)
            logger.debug("MCPClient.connectToServer completed for ${serverConfig.id}, adding to clients map...")
            clients[serverConfig.id] = client
            logger.info("Successfully connected to server: ${serverConfig.name} (${serverConfig.id})")
        } catch (e: Exception) {
            logger.error("Error connecting to server ${serverConfig.id}: ${e.message}", e)
            throw e // Пробрасываем исключение дальше, чтобы оно было обработано в initialize()
        }
    }
    
    /**
     * Отключиться от всех серверов
     */
    suspend fun disconnectAll() {
        logger.info("Disconnecting from all MCP servers...")
        
        coroutineScope {
            clients.values.map { client ->
                async {
                    try {
                        client.disconnect()
                    } catch (e: Exception) {
                        logger.warn("Error disconnecting from ${client.getServerId()}: ${e.message}")
                    }
                }
            }.awaitAll()
        }
        
        clients.clear()
        logger.info("Disconnected from all MCP servers")
    }
    
    /**
     * Получить список всех доступных инструментов из всех серверов
     */
    suspend fun listAllTools(): List<com.prike.data.dto.MCPTool> {
        val allTools = mutableListOf<com.prike.data.dto.MCPTool>()
        
        coroutineScope {
            val toolJobs = clients.map { (serverId, client) ->
                async {
                    try {
                        client.listTools()
                    } catch (e: Exception) {
                        logger.error("Failed to list tools from server $serverId: ${e.message}", e)
                        emptyList<com.prike.data.dto.MCPTool>()
                    }
                }
            }
            
            toolJobs.awaitAll().forEach { tools ->
                allTools.addAll(tools)
            }
        }
        
        return allTools
    }
    
    /**
     * Вызвать инструмент из конкретного сервера
     */
    suspend fun callTool(serverId: String, toolName: String, arguments: kotlinx.serialization.json.JsonObject): String {
        val client = clients[serverId]
            ?: throw IllegalArgumentException("MCP server not found: $serverId")
        
        return client.callTool(toolName, arguments)
    }
    
    /**
     * Найти сервер, который предоставляет инструмент
     */
    fun findServerForTool(toolName: String): String? {
        return config.servers.find { it.tools.contains(toolName) }?.id
    }
    
    /**
     * Получить статус подключений
     */
    fun getConnectionStatus(): Map<String, Boolean> {
        return clients.mapValues { it.value.isConnected() }
    }
    
    /**
     * Получить список подключенных серверов
     */
    fun getConnectedServers(): List<String> {
        return clients.keys.toList()
    }
}

