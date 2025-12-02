package com.prike.data.client

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.delay
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Клиент для подключения к Git MCP серверу
 */
class GitMCPClient {
    private val logger = LoggerFactory.getLogger(GitMCPClient::class.java)
    private val client = Client(
        clientInfo = Implementation(
            name = "lesson-20-git-mcp-client",
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
            logger.warn("Git MCP client is already connected. Disconnecting first...")
            disconnect()
        }
        
        val process: Process
        val workingDir: File
        
        if (jarPath != null && jarPath != "class") {
            // Режим 1: Запуск через JAR файл (production)
            val jarFile = when {
                File(jarPath).isAbsolute -> File(jarPath)
                jarPath.startsWith("../") -> {
                    // Относительный путь с .. - разрешаем относительно lessonRoot
                    File(lessonRoot, jarPath)
                }
                else -> {
                    // Относительный путь - разрешаем относительно lessonRoot
                    File(lessonRoot, jarPath)
                }
            }
            
            // Нормализуем путь (убираем .. и .)
            val normalizedJarFile = jarFile.canonicalFile
            
            if (!normalizedJarFile.exists()) {
                throw IllegalStateException("Git MCP server JAR not found: ${normalizedJarFile.absolutePath}")
            }
            
            logger.info("Starting Git MCP server from JAR: ${jarFile.absolutePath}")
            
            // Определяем рабочую директорию
            workingDir = lessonRoot
            
            // Запуск MCP сервера как отдельного процесса через JAR
            val errorLogFile = File.createTempFile("git-mcp-server-error", ".log")
            process = ProcessBuilder("java", "-jar", normalizedJarFile.absolutePath)
                .directory(workingDir)
                .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
                .start()
        } else {
            // Режим 2: Запуск через Gradle (development)
            workingDir = lessonRoot
            
            val serverDir = File(workingDir, "git-mcp-server")
            if (!serverDir.exists()) {
                throw IllegalStateException("Git MCP server directory not found: ${serverDir.absolutePath}")
            }
            
            logger.info("Starting Git MCP server via Gradle from: ${serverDir.absolutePath}")
            
            val errorLogFile = File.createTempFile("git-mcp-server-error", ".log")
            process = ProcessBuilder("./gradlew", "run")
                .directory(serverDir)
                .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
                .start()
        }
        
        mcpServerProcess = process
        
        // Создаём транспорт для stdio
        transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
        
        // Подключаемся к серверу
        logger.info("Connecting to Git MCP server...")
        client.connect(transport!!)
        
        // Ждём инициализации соединения
        delay(CONNECTION_INIT_DELAY_MS)
        
        // Даём время на инициализацию соединения
        delay(TOOLS_REQUEST_DELAY_MS)
        
        isConnected = true
    }
    
    suspend fun disconnect() {
        if (!isConnected) {
            return
        }
        
        logger.info("Disconnecting from Git MCP server...")
        
        try {
            transport?.close()
        } catch (e: Exception) {
            logger.warn("Error closing transport: ${e.message}")
        }
        
        try {
            mcpServerProcess?.destroy()
            mcpServerProcess?.waitFor()
        } catch (e: Exception) {
            logger.warn("Error stopping process: ${e.message}")
        }
        
        transport = null
        mcpServerProcess = null
        isConnected = false
        
        logger.info("Disconnected from Git MCP server")
    }
    
    suspend fun callTool(toolName: String, arguments: JsonObject = buildJsonObject {}): String {
        if (!isConnected) {
            throw IllegalStateException("Git MCP client is not connected")
        }
        
        logger.debug("Calling tool: $toolName with arguments: $arguments")
        
        val result = client.callTool(
            name = toolName,
            arguments = arguments
        ) ?: throw IllegalStateException("Tool call returned null response")
        
        // Преобразуем результат в читаемую строку используя TextContent из SDK
        val resultText = result.content.joinToString("\n\n") { content ->
            when (content) {
                is TextContent -> content.text ?: ""
                else -> content.toString()
            }
        }
        
        if (resultText.isBlank()) {
            logger.warn("Git MCP client callTool returned empty result for tool: $toolName")
        }
        
        return resultText
    }
    
    suspend fun listTools(): List<MCPTool> {
        if (!isConnected) {
            throw IllegalStateException("Git MCP client is not connected")
        }
        
        logger.debug("Listing Git MCP tools...")
        val response = client.listTools()
        
        return response.tools.map { tool ->
            MCPTool(
                name = tool.name,
                description = tool.description ?: "",
                inputSchema = tool.inputSchema
            )
        }
    }
    
    fun isConnected(): Boolean {
        return isConnected && mcpServerProcess?.isAlive == true
    }
}

