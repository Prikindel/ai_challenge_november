package com.prike.ragmcpserver.data.client

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
 * Клиент для подключения к RAG MCP серверу
 */
class RagMCPClient {
    private val logger = LoggerFactory.getLogger(RagMCPClient::class.java)
    private val client = Client(
        clientInfo = Implementation(
            name = "lesson-25-rag-mcp-client",
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
    
    suspend fun connectToServer(jarPath: String?, lessonRoot: File) {
        if (isConnected) {
            logger.warn("RAG MCP client is already connected. Disconnecting first...")
            disconnect()
        }
        
        val process: Process
        val workingDir: File
        
        if (jarPath != null && jarPath != "class") {
            val jarFile = when {
                File(jarPath).isAbsolute -> File(jarPath)
                jarPath.startsWith("../") -> File(lessonRoot, jarPath)
                else -> File(lessonRoot, jarPath)
            }
            
            val normalizedJarFile = jarFile.canonicalFile
            
            if (!normalizedJarFile.exists()) {
                throw IllegalStateException("RAG MCP server JAR not found: ${normalizedJarFile.absolutePath}")
            }
            
            logger.info("Starting RAG MCP server from JAR: ${jarFile.absolutePath}")
            workingDir = lessonRoot
            
            val errorLogFile = File.createTempFile("rag-mcp-server-error", ".log")
            process = ProcessBuilder("java", "-jar", normalizedJarFile.absolutePath)
                .directory(workingDir)
                .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
                .start()
        } else {
            workingDir = lessonRoot
            val serverDir = File(workingDir, "rag-mcp-server")
            if (!serverDir.exists()) {
                throw IllegalStateException("RAG MCP server directory not found: ${serverDir.absolutePath}")
            }
            
            logger.info("Starting RAG MCP server via Gradle from: ${serverDir.absolutePath}")
            val errorLogFile = File.createTempFile("rag-mcp-server-error", ".log")
            process = ProcessBuilder("./gradlew", "run")
                .directory(serverDir)
                .redirectError(ProcessBuilder.Redirect.to(errorLogFile))
                .start()
        }
        
        mcpServerProcess = process
        
        transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
        
        logger.info("Connecting to RAG MCP server...")
        client.connect(transport!!)
        
        delay(CONNECTION_INIT_DELAY_MS)
        delay(TOOLS_REQUEST_DELAY_MS)
        
        isConnected = true
    }
    
    suspend fun disconnect() {
        if (!isConnected) {
            return
        }
        
        logger.info("Disconnecting from RAG MCP server...")
        
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
        
        logger.info("Disconnected from RAG MCP server")
    }
    
    suspend fun callTool(toolName: String, arguments: JsonObject = buildJsonObject {}): String {
        if (!isConnected) {
            throw IllegalStateException("RAG MCP client is not connected")
        }
        
        logger.debug("Calling RAG MCP tool: $toolName with arguments: $arguments")
        
        val result = client.callTool(
            name = toolName,
            arguments = arguments
        ) ?: throw IllegalStateException("Tool call returned null response")
        
        val resultText = result.content.joinToString("\n\n") { content ->
            when (content) {
                is TextContent -> content.text ?: ""
                else -> content.toString()
            }
        }
        
        if (resultText.isBlank()) {
            logger.warn("RAG MCP client callTool returned empty result for tool: $toolName")
        }
        
        return resultText
    }
    
    suspend fun listTools(): List<MCPTool> {
        if (!isConnected) {
            throw IllegalStateException("RAG MCP client is not connected")
        }
        
        logger.debug("Listing RAG MCP tools...")
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

/**
 * Упрощенное представление MCP инструмента
 */
data class MCPTool(
    val name: String,
    val description: String,
    val inputSchema: Any? // JsonObject из MCP SDK
)

