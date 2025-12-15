package com.prike.data.client

import com.prike.config.TelegramConfig
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.delay
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * MCP клиент для подключения к Telegram MCP серверу
 */
class TelegramMCPClient(
    private val config: TelegramConfig,
    private val lessonRoot: File
) {
    private val logger = LoggerFactory.getLogger(TelegramMCPClient::class.java)
    private val client = Client(
        clientInfo = Implementation(
            name = "lesson-24-telegram-mcp-client",
            version = "1.0.0"
        )
    )
    
    private var isConnected = false
    private var transport: StdioClientTransport? = null
    private var mcpServerProcess: Process? = null
    
    companion object {
        private const val CONNECTION_INIT_DELAY_MS = 1000L
        private const val TOOLS_REQUEST_DELAY_MS = 500L
    }
    
    /**
     * Подключиться к Telegram MCP серверу
     */
    suspend fun connect() {
        if (!config.mcp.enabled) {
            logger.warn("Telegram MCP is disabled in configuration")
            return
        }
        
        if (isConnected) {
            logger.warn("Telegram MCP client is already connected")
            return
        }
        
        val jarPath = config.mcp.jarPath
        val process: Process
        val workingDir: File
        
        if (jarPath.isNotBlank() && jarPath != "class") {
            // Режим 1: Запуск через JAR файл
            val jarFile = when {
                File(jarPath).isAbsolute -> File(jarPath)
                else -> File(lessonRoot, jarPath)
            }
            
            val normalizedJarFile = jarFile.canonicalFile
            
            if (!normalizedJarFile.exists()) {
                throw IllegalStateException("Telegram MCP server JAR not found: ${normalizedJarFile.absolutePath}")
            }
            
            logger.info("Starting Telegram MCP server from JAR: ${jarFile.absolutePath}")
            
            workingDir = lessonRoot
            
            val errorLogFile = File.createTempFile("telegram-mcp-server-error", ".log")
            process = ProcessBuilder("java", "-jar", normalizedJarFile.absolutePath)
                .directory(workingDir)
                .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
                .start()
        } else {
            // Режим 2: Запуск через Gradle (development)
            workingDir = lessonRoot
            
            val mcpServerDir = File(workingDir, "telegram-mcp-server")
            if (!mcpServerDir.exists()) {
                throw IllegalStateException("Telegram MCP server directory not found: ${mcpServerDir.absolutePath}")
            }
            
            logger.info("Starting Telegram MCP server from Gradle (development mode): ${mcpServerDir.absolutePath}")
            
            val gradlew = File(mcpServerDir, "gradlew")
            if (gradlew.exists()) {
                val errorLogFile = File.createTempFile("telegram-mcp-server-error", ".log")
                process = ProcessBuilder(gradlew.absolutePath, "run", "--no-daemon", "--quiet")
                    .directory(mcpServerDir)
                    .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
                    .start()
            } else {
                throw IllegalStateException("Cannot find gradlew in ${mcpServerDir.absolutePath}")
            }
        }
        
        mcpServerProcess = process
        
        transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
        
        client.connect(transport!!)
        
        delay(CONNECTION_INIT_DELAY_MS)
        
        isConnected = true
        logger.info("Connected to Telegram MCP server")
    }
    
    /**
     * Отправить сообщение в Telegram через MCP
     */
    suspend fun sendMessage(userId: String, message: String): Boolean {
        if (!isConnected) {
            logger.error("Telegram MCP client is not connected")
            return false
        }
        
        return try {
            delay(TOOLS_REQUEST_DELAY_MS)
            
            val arguments = buildJsonObject {
                put("userId", userId)
                put("message", message)
            }
            
            val response = client.callTool(
                name = "send_telegram_message",
                arguments = arguments
            ) ?: throw IllegalStateException("Tool call returned null response")
            
            val result = response.content.joinToString("\n\n") { content ->
                when (content) {
                    is TextContent -> content.text ?: ""
                    else -> content.toString()
                }
            }
            
            logger.info("Message sent to Telegram via MCP: userId=$userId")
            true
        } catch (e: Exception) {
            logger.error("Failed to send message via Telegram MCP: ${e.message}", e)
            false
        }
    }
    
    /**
     * Отключиться от Telegram MCP сервера
     */
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
        logger.info("Disconnected from Telegram MCP server")
    }
    
    fun isConnected(): Boolean = isConnected
}
