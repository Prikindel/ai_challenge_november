package com.prike.data.client

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import kotlinx.coroutines.delay
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Клиент для подключения к Task MCP серверу
 */
class TaskMCPClient {
    private val logger = LoggerFactory.getLogger(TaskMCPClient::class.java)
    private val client = Client(
        clientInfo = Implementation(
            name = "lesson-23-task-mcp-client",
            version = "1.0.0"
        )
    )
    
    private var isConnected = false
    private var transport: StdioClientTransport? = null
    private var mcpServerProcess: Process? = null
    
    companion object {
        // Время ожидания инициализации MCP соединения (мс)
        private const val CONNECTION_INIT_DELAY_MS = 1000L
        
        // Время ожидания перед запросом инструментов (мс)
        private const val TOOLS_REQUEST_DELAY_MS = 500L
    }
    
    suspend fun connectToServer(jarPath: String?, lessonRoot: File) {
        // Проверяем, не подключены ли уже
        if (isConnected) {
            logger.warn("Task MCP client is already connected. Disconnecting first...")
            disconnect()
        }
        
        val process: Process
        val workingDir: File
        
        if (jarPath != null && jarPath != "class") {
            // Запускаем через JAR файл
            val jarFile = File(lessonRoot, jarPath)
            if (!jarFile.exists()) {
                throw IllegalStateException("Task MCP server JAR not found: ${jarFile.absolutePath}")
            }
            
            logger.info("Starting Task MCP server from JAR: ${jarFile.absolutePath}")
            val processBuilder = ProcessBuilder("java", "-jar", jarFile.absolutePath)
            processBuilder.directory(lessonRoot)
            process = processBuilder.start()
            workingDir = lessonRoot
        } else {
            // Запускаем через Gradle (для разработки)
            logger.info("Starting Task MCP server via Gradle")
            val taskServerDir = File(lessonRoot, "task-mcp-server")
            if (!taskServerDir.exists()) {
                throw IllegalStateException("Task MCP server directory not found: ${taskServerDir.absolutePath}")
            }
            
            val processBuilder = ProcessBuilder("./gradlew", "run", "--no-daemon")
            processBuilder.directory(taskServerDir)
            process = processBuilder.start()
            workingDir = taskServerDir
        }
        
        mcpServerProcess = process
        
        // Создаём транспорт для stdio
        transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
        
        // Подключаемся к серверу
        client.connect(transport!!)
        
        // Ждём инициализации соединения
        delay(CONNECTION_INIT_DELAY_MS)
        
        // Ждём перед запросом инструментов
        delay(TOOLS_REQUEST_DELAY_MS)
        
        // Запрашиваем список инструментов для проверки подключения
        try {
            val response = client.listTools()
            val toolNames = response.tools.map { it.name }.joinToString(", ")
            logger.info("Task MCP server connected. Available tools: $toolNames")
            isConnected = true
        } catch (e: Exception) {
            logger.error("Failed to list Task MCP tools after connection: ${e.message}", e)
            disconnect()
            throw IllegalStateException("Failed to connect to Task MCP server: ${e.message}", e)
        }
    }
    
    suspend fun disconnect() {
        try {
            if (isConnected) {
                client.close()
                isConnected = false
            }
        } catch (e: Exception) {
            logger.warn("Error closing Task MCP client: ${e.message}")
        } finally {
            transport = null
            mcpServerProcess?.destroy()
            mcpServerProcess = null
        }
    }
    
    fun isConnected(): Boolean {
        return isConnected && transport != null
    }
    
    suspend fun callTool(toolName: String, arguments: JsonObject = buildJsonObject {}): String {
        if (!isConnected) {
            throw IllegalStateException("Task MCP client is not connected")
        }
        
        logger.debug("Calling Task MCP tool: $toolName with arguments: $arguments")
        
        val result = client.callTool(
            name = toolName,
            arguments = arguments
        ) ?: throw IllegalStateException("Tool call returned null response")
        
        val resultText = result.content.joinToString("\n\n") { content ->
            when (content) {
                is io.modelcontextprotocol.kotlin.sdk.TextContent -> content.text ?: ""
                else -> content.toString()
            }
        }
        
        if (resultText.isBlank()) {
            logger.warn("Task MCP client callTool returned empty result for tool: $toolName")
        }
        
        return resultText
    }
    
    suspend fun listTools(): List<com.prike.data.client.MCPTool> {
        if (!isConnected) {
            throw IllegalStateException("Task MCP client is not connected")
        }
        
        logger.debug("Listing Task MCP tools...")
        val response = client.listTools()
        
        return response.tools.map { tool ->
            com.prike.data.client.MCPTool(
                name = tool.name,
                description = tool.description ?: "",
                inputSchema = tool.inputSchema
            )
        }
    }
}

