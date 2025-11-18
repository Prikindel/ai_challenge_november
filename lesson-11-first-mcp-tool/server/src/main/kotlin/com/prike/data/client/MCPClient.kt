package com.prike.data.client

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import com.prike.Config
import com.prike.data.dto.MCPTool
import kotlinx.coroutines.delay
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.io.File

class MCPClient {
    private val logger = LoggerFactory.getLogger(MCPClient::class.java)
    private val client = Client(
        clientInfo = Implementation(
            name = "lesson-11-mcp-client",
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
    
    suspend fun connectToServer(jarPath: String? = null) {
        // Проверяем, не подключены ли уже
        if (isConnected) {
            logger.warn("MCP client is already connected. Disconnecting first...")
            disconnect()
        }
        
        val process: Process
        val workingDir: File
        
        if (jarPath != null && jarPath != "class") {
            // Режим 1: Запуск через JAR файл (production)
            val jarFile = File(jarPath)
            if (!jarFile.exists()) {
                throw IllegalStateException("MCP server JAR not found: $jarPath")
            }
            
            logger.info("Starting MCP server from JAR: $jarPath")
            
            // Определяем рабочую директорию (lesson-11-first-mcp-tool)
            workingDir = jarFile.parentFile?.parentFile?.parentFile // build/libs -> build -> mcp-server -> lesson-11-first-mcp-tool
                ?: throw IllegalStateException("Cannot determine working directory for MCP server")
            
            // Запуск MCP сервера как отдельного процесса через JAR
            process = ProcessBuilder("java", "-jar", jarPath)
                .directory(workingDir)
                .start()
        } else {
            // Режим 2: Запуск через класс напрямую (development)
            workingDir = File(Config.getLessonRoot())
            
            val mcpServerDir = File(workingDir, "mcp-server")
            if (!mcpServerDir.exists()) {
                throw IllegalStateException("MCP server directory not found: ${mcpServerDir.absolutePath}")
            }
            
            logger.info("Starting MCP server from class (development mode)")
            
            // Запуск через Gradle (проще всего для разработки)
            // Используем gradlew из mcp-server директории
            // ВАЖНО: Перенаправляем stderr в отдельный поток, чтобы вывод Gradle не мешал MCP протоколу
            val gradlew = File(mcpServerDir, "gradlew")
            if (gradlew.exists()) {
                process = ProcessBuilder(gradlew.absolutePath, "run", "--no-daemon", "--quiet")
                    .directory(mcpServerDir)
                    .redirectError(ProcessBuilder.Redirect.to(File.createTempFile("mcp-server-gradle", ".log")))
                    .start()
            } else {
                // Fallback: пытаемся найти gradlew в корне проекта
                val rootGradlew = File(workingDir, "gradlew")
                if (rootGradlew.exists()) {
                    process = ProcessBuilder(rootGradlew.absolutePath, ":mcp-server:run", "--no-daemon", "--quiet")
                        .directory(workingDir)
                        .redirectError(ProcessBuilder.Redirect.to(File.createTempFile("mcp-server-gradle", ".log")))
                        .start()
                } else {
                    throw IllegalStateException(
                        "Cannot find gradlew. Please use JAR mode or ensure gradlew exists in mcp-server directory."
                    )
                }
            }
        }
        
        mcpServerProcess = process
        
        // Подключение к stdin/stdout процесса
        transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
        
        // Подключаемся к серверу
        client.connect(transport!!)
        
        // Даём время на инициализацию соединения и обмен сообщениями initialize
        delay(CONNECTION_INIT_DELAY_MS)
        
        isConnected = true
        logger.info("Connected to MCP server")
    }
    
    suspend fun listTools(): List<MCPTool> {
        if (!isConnected) {
            throw IllegalStateException("MCP client not connected")
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
            logger.warn("Error during disconnect: ${e.message}")
        }
        isConnected = false
        transport = null
        mcpServerProcess = null
        logger.info("Disconnected from MCP server")
    }
    
    suspend fun callTool(toolName: String, arguments: JsonObject): String {
        if (!isConnected) {
            throw IllegalStateException("MCP client not connected")
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
            logger.warn("MCPClient.callTool returned empty result for tool: $toolName")
        }
        
        return result
    }
    
    fun isConnected(): Boolean = isConnected
}

