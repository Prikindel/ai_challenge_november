package com.prike.data.client

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import com.prike.data.dto.MCPTool
import kotlinx.coroutines.delay
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Клиент для подключения к одному MCP серверу
 */
class MCPClient(
    private val serverId: String
) {
    private val logger = LoggerFactory.getLogger(MCPClient::class.java)
    private val client = Client(
        clientInfo = Implementation(
            name = "lesson-14-orchestration-client",
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
    
    suspend fun connectToServer(jarPath: String, lessonRoot: File) {
        // Проверяем, не подключены ли уже
        if (isConnected) {
            logger.warn("MCP client for $serverId is already connected. Disconnecting first...")
            disconnect()
        }
        
        // Режим: Запуск через JAR файл
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
            throw IllegalStateException("MCP server JAR not found: ${normalizedJarFile.absolutePath}")
        }
        
        logger.info("[$serverId] Starting MCP server from JAR: ${jarFile.absolutePath}")
        
        // Определяем рабочую директорию (lesson-14-orchestration)
        val workingDir = lessonRoot
        
        // Запуск MCP сервера как отдельного процесса через JAR
        // ВАЖНО: stderr перенаправляем в отдельный файл, чтобы логи не мешали MCP протоколу
        val errorLogFile = File.createTempFile("mcp-server-$serverId-error", ".log")
        val process = ProcessBuilder("java", "-jar", normalizedJarFile.absolutePath)
            .directory(workingDir)
            .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
            .start()
        
        mcpServerProcess = process
        
        // Подключение к stdin/stdout процесса
        transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
        
        // Подключаемся к серверу
        try {
            logger.debug("[$serverId] Calling client.connect()...")
            client.connect(transport!!)
            logger.debug("[$serverId] client.connect() completed, waiting for initialization...")
            
            // Даём время на инициализацию соединения и обмен сообщениями initialize
            delay(CONNECTION_INIT_DELAY_MS)
            
            isConnected = true
            logger.info("[$serverId] Connected to MCP server")
        } catch (e: Exception) {
            logger.error("[$serverId] Error during connection: ${e.message}", e)
            isConnected = false
            throw e
        }
    }
    
    suspend fun listTools(): List<MCPTool> {
        if (!isConnected) {
            throw IllegalStateException("MCP client for $serverId not connected")
        }
        
        // Даём дополнительное время перед запросом инструментов
        delay(TOOLS_REQUEST_DELAY_MS)
        
        val response = client.listTools()
        return response.tools.map { tool ->
            MCPTool.fromMCPTool(tool)
        }
    }
    
    suspend fun disconnect() {
        try {
            transport?.close()
            mcpServerProcess?.destroy()
        } catch (e: Exception) {
            logger.warn("[$serverId] Error during disconnect: ${e.message}")
        }
        isConnected = false
        transport = null
        mcpServerProcess = null
        logger.info("[$serverId] Disconnected from MCP server")
    }
    
    suspend fun callTool(toolName: String, arguments: JsonObject): String {
        if (!isConnected) {
            throw IllegalStateException("MCP client for $serverId not connected")
        }
        
        val response = client.callTool(
            name = toolName,
            arguments = arguments
        ) ?: throw IllegalStateException("Tool call returned null response")
        
        // Преобразуем результат в читаемую строку используя TextContent из SDK
        val result = response.content.joinToString("\n\n") { content ->
            when (content) {
                is TextContent -> content.text ?: ""
                else -> content.toString()
            }
        }
        
        if (result.isBlank()) {
            logger.warn("[$serverId] MCPClient.callTool returned empty result for tool: $toolName")
        }
        
        return result
    }
    
    fun isConnected(): Boolean = isConnected
    
    fun getServerId(): String = serverId
}

