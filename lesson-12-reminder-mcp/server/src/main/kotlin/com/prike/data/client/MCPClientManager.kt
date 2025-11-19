package com.prike.data.client

import com.prike.config.AppConfig
import com.prike.config.DataSourceConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Менеджер для управления несколькими MCP клиентами
 */
class MCPClientManager(
    private val config: AppConfig,
    private val lessonRoot: File
) {
    private val logger = LoggerFactory.getLogger(MCPClientManager::class.java)
    private val clients = mutableMapOf<String, MCPClient>()
    
    /**
     * Подключиться ко всем включенным источникам данных
     */
    suspend fun connectAll() {
        logger.info("Connecting to MCP servers...")
        
        val enabledSources = config.dataSources.filter { it.value.enabled }
        if (enabledSources.isEmpty()) {
            logger.warn("No enabled data sources found in configuration")
            return
        }
        
        coroutineScope {
            val connectionJobs = enabledSources.map { (sourceId, sourceConfig) ->
                async {
                    try {
                        connectToSource(sourceId, sourceConfig)
                    } catch (e: Exception) {
                        logger.error("Failed to connect to source $sourceId: ${e.message}", e)
                        null
                    }
                }
            }
            
            connectionJobs.awaitAll()
        }
        
        logger.info("Connected to ${clients.size} MCP server(s)")
    }
    
    /**
     * Подключиться к конкретному источнику
     */
    suspend fun connectToSource(sourceId: String, sourceConfig: DataSourceConfig) {
        if (clients.containsKey(sourceId)) {
            logger.warn("Source $sourceId is already connected")
            return
        }
        
        logger.info("Connecting to source: ${sourceConfig.name} ($sourceId)")
        
        val client = MCPClient(sourceId)
        client.connectToServer(sourceConfig.mcpServer.jarPath, lessonRoot)
        clients[sourceId] = client
        
        logger.info("Successfully connected to source: ${sourceConfig.name}")
    }
    
    /**
     * Отключиться от всех источников
     */
    suspend fun disconnectAll() {
        logger.info("Disconnecting from all MCP servers...")
        
        coroutineScope {
            clients.values.map { client ->
                async {
                    try {
                        client.disconnect()
                    } catch (e: Exception) {
                        logger.warn("Error disconnecting from ${client.getSourceName()}: ${e.message}")
                    }
                }
            }.awaitAll()
        }
        
        clients.clear()
        logger.info("Disconnected from all MCP servers")
    }
    
    /**
     * Получить список всех доступных инструментов из всех источников
     */
    suspend fun getAllTools(): Map<String, List<com.prike.data.dto.MCPTool>> {
        val allTools = mutableMapOf<String, List<com.prike.data.dto.MCPTool>>()
        
        coroutineScope {
            val toolJobs = clients.map { (sourceId, client) ->
                async {
                    try {
                        sourceId to client.listTools()
                    } catch (e: Exception) {
                        logger.error("Failed to list tools from source $sourceId: ${e.message}", e)
                        sourceId to emptyList<com.prike.data.dto.MCPTool>()
                    }
                }
            }
            
            toolJobs.awaitAll().forEach { (sourceId, tools) ->
                allTools[sourceId] = tools
            }
        }
        
        return allTools
    }
    
    /**
     * Вызвать инструмент из конкретного источника
     */
    suspend fun callTool(sourceId: String, toolName: String, arguments: kotlinx.serialization.json.JsonObject): String {
        val client = clients[sourceId]
            ?: throw IllegalStateException("Source $sourceId is not connected")
        
        return client.callTool(toolName, arguments)
    }
    
    /**
     * Получить статус подключений
     */
    fun getConnectionStatus(): Map<String, Boolean> {
        return clients.mapValues { it.value.isConnected() }
    }
    
    /**
     * Получить список подключенных источников
     */
    fun getConnectedSources(): List<String> {
        return clients.keys.toList()
    }
}

