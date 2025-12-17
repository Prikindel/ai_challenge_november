package com.prike.data.client

import com.prike.domain.model.MCPTool
import com.prike.domain.model.MCPToolResult
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
 * Универсальный интерфейс для работы с MCP серверами
 */
interface MCPClientInterface {
    /**
     * Подключиться к MCP серверу
     */
    suspend fun connect()
    
    /**
     * Отключиться от MCP сервера
     */
    suspend fun disconnect()
    
    /**
     * Проверить, подключен ли клиент
     */
    fun isConnected(): Boolean
    
    /**
     * Получить список доступных инструментов
     */
    suspend fun listTools(): List<MCPTool>
    
    /**
     * Вызвать инструмент
     */
    suspend fun callTool(toolName: String, arguments: Map<String, Any>): MCPToolResult
}

/**
 * Базовый MCP клиент на основе MCP SDK
 */
abstract class BaseMCPClient(
    protected val serverName: String,
    protected val lessonRoot: File
) : MCPClientInterface {
    protected val logger = LoggerFactory.getLogger(this::class.java)
    protected val client = Client(
        clientInfo = Implementation(
            name = "god-agent-mcp-client",
            version = "1.0.0"
        )
    )
    
    @Volatile
    private var _isConnected = false
    protected var transport: StdioClientTransport? = null
    protected var mcpServerProcess: Process? = null
    
    companion object {
        private const val CONNECTION_INIT_DELAY_MS = 1000L
        private const val TOOLS_REQUEST_DELAY_MS = 500L
    }
    
    /**
     * Запустить процесс MCP сервера
     */
    protected abstract fun startServerProcess(): Process
    
    override suspend fun connect() {
        if (_isConnected) {
            logger.warn("$serverName MCP client is already connected")
            return
        }
        
        try {
            val process = startServerProcess()
            mcpServerProcess = process
            
            transport = StdioClientTransport(
                input = process.inputStream.asSource().buffered(),
                output = process.outputStream.asSink().buffered()
            )
            
            client.connect(transport!!)
            delay(CONNECTION_INIT_DELAY_MS)
            
            _isConnected = true
            logger.info("Connected to $serverName MCP server")
        } catch (e: Exception) {
            logger.error("Failed to connect to $serverName MCP server: ${e.message}", e)
            throw e
        }
    }
    
    override suspend fun disconnect() {
        try {
            transport?.close()
            mcpServerProcess?.destroy()
        } catch (e: Exception) {
            logger.warn("Error during disconnect from $serverName: ${e.message}")
        }
        _isConnected = false
        transport = null
        mcpServerProcess = null
        logger.info("Disconnected from $serverName MCP server")
    }
    
    override fun isConnected(): Boolean = _isConnected
    
    override suspend fun listTools(): List<MCPTool> {
        if (!_isConnected) {
            logger.error("$serverName MCP client is not connected")
            return emptyList()
        }
        
        return try {
            delay(TOOLS_REQUEST_DELAY_MS)
            
            val toolsResponse = client.listTools() ?: return emptyList()
            
            toolsResponse.tools.map { tool ->
                MCPTool(
                    serverName = serverName,
                    name = tool.name,
                    description = tool.description ?: "",
                    parameters = emptyMap() // Схема параметров будет обрабатываться при вызове инструмента
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to list tools from $serverName: ${e.message}", e)
            emptyList()
        }
    }
    
    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): MCPToolResult {
        if (!_isConnected) {
            return MCPToolResult.failure("$serverName MCP client is not connected")
        }
        
        return try {
            delay(TOOLS_REQUEST_DELAY_MS)
            
            val argumentsJson = buildJsonObject {
                arguments.forEach { (key, value) ->
                    put(key, value.toString())
                }
            }
            
            val response = client.callTool(
                name = toolName,
                arguments = argumentsJson
            ) ?: return MCPToolResult.failure("Tool call returned null response")
            
            val result = response.content.joinToString("\n\n") { content ->
                when (content) {
                    is TextContent -> content.text ?: ""
                    else -> content.toString()
                }
            }
            
            MCPToolResult.success(result)
        } catch (e: Exception) {
            logger.error("Failed to call tool $toolName from $serverName: ${e.message}", e)
            MCPToolResult.failure(e.message ?: "Unknown error")
        }
    }
    
}

