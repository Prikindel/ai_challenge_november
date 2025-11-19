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
    private val sourceName: String
) {
    private val logger = LoggerFactory.getLogger(MCPClient::class.java)
    private val client = Client(
        clientInfo = Implementation(
            name = "lesson-12-mcp-client",
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
            logger.warn("MCP client for $sourceName is already connected. Disconnecting first...")
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
                throw IllegalStateException("MCP server JAR not found: ${normalizedJarFile.absolutePath}")
            }
            
            logger.info("[$sourceName] Starting MCP server from JAR: ${jarFile.absolutePath}")
            
            // Определяем рабочую директорию (lesson-12-reminder-mcp)
            workingDir = lessonRoot
            
            // Запуск MCP сервера как отдельного процесса через JAR
            // ВАЖНО: stderr перенаправляем в отдельный файл, чтобы логи не мешали MCP протоколу
            val errorLogFile = File.createTempFile("mcp-server-$sourceName-error", ".log")
            process = ProcessBuilder("java", "-jar", normalizedJarFile.absolutePath)
                .directory(workingDir)
                .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
                .start()
        } else {
            // Режим 2: Запуск через Gradle (development)
            workingDir = lessonRoot
            
            // Определяем путь к серверу из jarPath (например, "../chat-history-mcp-server")
            val serverDirName = when {
                jarPath == null || jarPath == "class" -> {
                    // Пытаемся определить из sourceName
                    when (sourceName) {
                        "webChat" -> "chat-history-mcp-server"
                        "telegram" -> "telegram-mcp-server"
                        else -> throw IllegalStateException("Cannot determine server directory for source: $sourceName")
                    }
                }
                else -> {
                    // Извлекаем имя директории из пути
                    val path = jarPath.replace("../", "").replace("build/libs/.*".toRegex(), "")
                    path.split("/").firstOrNull() ?: throw IllegalStateException("Cannot determine server directory from path: $jarPath")
                }
            }
            
            val mcpServerDir = File(workingDir, serverDirName)
            if (!mcpServerDir.exists()) {
                throw IllegalStateException("MCP server directory not found: ${mcpServerDir.absolutePath}")
            }
            
            logger.info("[$sourceName] Starting MCP server from Gradle (development mode): ${mcpServerDir.absolutePath}")
            
            // Запуск через Gradle
            // ВАЖНО: stderr перенаправляем в отдельный файл, чтобы логи не мешали MCP протоколу
            val gradlew = File(mcpServerDir, "gradlew")
            if (gradlew.exists()) {
                val errorLogFile = File.createTempFile("mcp-server-$sourceName-error", ".log")
                process = ProcessBuilder(gradlew.absolutePath, "run", "--no-daemon", "--quiet")
                    .directory(mcpServerDir)
                    .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
                    .start()
            } else {
                throw IllegalStateException("Cannot find gradlew in ${mcpServerDir.absolutePath}")
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
        logger.info("[$sourceName] Connected to MCP server")
    }
    
    suspend fun listTools(): List<MCPTool> {
        if (!isConnected) {
            throw IllegalStateException("MCP client for $sourceName not connected")
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
            logger.warn("[$sourceName] Error during disconnect: ${e.message}")
        }
        isConnected = false
        transport = null
        mcpServerProcess = null
        logger.info("[$sourceName] Disconnected from MCP server")
    }
    
    suspend fun callTool(toolName: String, arguments: JsonObject): String {
        if (!isConnected) {
            throw IllegalStateException("MCP client for $sourceName not connected")
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
            logger.warn("[$sourceName] MCPClient.callTool returned empty result for tool: $toolName")
        }
        
        return result
    }
    
    fun isConnected(): Boolean = isConnected
    
    fun getSourceName(): String = sourceName
}

