package com.prike.domain.service

import com.prike.data.client.RagMCPClient
import com.prike.data.client.MCPTool
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Сервис для работы с RAG MCP сервером
 */
class RagMCPService(
    private val ragMCPClient: RagMCPClient,
    private val lessonRoot: File,
    private val ragMCPJarPath: String? = null
) {
    private val logger = LoggerFactory.getLogger(RagMCPService::class.java)
    
    /**
     * Подключение к RAG MCP серверу
     * Запускает сервер с передачей RagServiceProvider
     */
    suspend fun connect() {
        try {
            // Запускаем MCP сервер как отдельный процесс
            // Но для передачи RagServiceProvider нужно использовать другой подход
            // Пока что просто подключаемся к серверу
            ragMCPClient.connectToServer(ragMCPJarPath, lessonRoot)
            logger.info("RAG MCP service connected successfully")
            
            // Проверяем доступные инструменты после подключения
            val tools = listTools()
            logger.info("RAG MCP tools available: ${tools.size} tools - ${tools.map { it.name }.joinToString(", ")}")
        } catch (e: Exception) {
            logger.error("Failed to connect to RAG MCP server: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Отключение от RAG MCP сервера
     */
    suspend fun disconnect() {
        try {
            ragMCPClient.disconnect()
            logger.info("RAG MCP service disconnected")
        } catch (e: Exception) {
            logger.warn("Error disconnecting from RAG MCP server: ${e.message}")
        }
    }
    
    /**
     * Получить список доступных инструментов
     */
    suspend fun listTools(): List<MCPTool> {
        return try {
            if (!ragMCPClient.isConnected()) {
                logger.warn("RAG MCP client is not connected, attempting to reconnect...")
                connect()
            }
            val tools = ragMCPClient.listTools()
            logger.info("Available RAG MCP tools: ${tools.map { it.name }.joinToString(", ")}")
            tools
        } catch (e: Exception) {
            logger.error("Failed to list RAG MCP tools: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Вызвать инструмент RAG MCP сервера
     */
    suspend fun callTool(toolName: String, arguments: kotlinx.serialization.json.JsonObject): String {
        return try {
            if (!ragMCPClient.isConnected()) {
                logger.warn("RAG MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            // Проверяем доступность инструмента перед вызовом
            val availableTools = listTools()
            val toolExists = availableTools.any { it.name == toolName }
            if (!toolExists) {
                logger.warn("Tool $toolName not found in available tools: ${availableTools.map { it.name }.joinToString(", ")}")
                throw IllegalStateException("Tool $toolName not found")
            }
            
            ragMCPClient.callTool(toolName, arguments)
        } catch (e: Exception) {
            logger.error("Failed to call RAG MCP tool $toolName: ${e.message}", e)
            throw e
        }
    }
    
    fun isConnected(): Boolean {
        return ragMCPClient.isConnected()
    }
}

