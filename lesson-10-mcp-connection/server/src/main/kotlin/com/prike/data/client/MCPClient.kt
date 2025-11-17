package com.prike.data.client

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import com.prike.data.dto.Tool
import com.prike.data.dto.Resource
import kotlinx.coroutines.delay
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream

class MCPClient {
    private val logger = LoggerFactory.getLogger(MCPClient::class.java)
    private val client = Client(
        clientInfo = Implementation(
            name = "lesson-10-mcp-client",
            version = "1.0.0"
        )
    )
    
    private var isConnected = false
    private var transport: StdioClientTransport? = null
    
    suspend fun connectStdio(
        inputStream: InputStream,
        outputStream: OutputStream
    ) {
        transport = StdioClientTransport(
            input = inputStream.asSource().buffered(),
            output = outputStream.asSink().buffered()
        )
        
        // Подключаемся к серверу
        // client.connect() автоматически вызывает transport.start()
        client.connect(transport!!)
        
        // Даём время на инициализацию соединения и обмен сообщениями initialize
        delay(1000)
        
        isConnected = true
    }
    
    suspend fun listTools(): List<Tool> {
        if (!isConnected) {
            throw IllegalStateException("MCP client not connected")
        }
        
        // Даём дополнительное время перед запросом инструментов
        delay(500)
        
        val response = client.listTools()
        return response.tools.map { tool ->
            Tool(
                name = tool.name,
                description = tool.description,
                inputSchema = null // inputSchema опционален и не используется в текущей реализации
                // При необходимости можно преобразовать JsonObject в Map<String, Any>
                // используя kotlinx.serialization или другие библиотеки
            )
        }
    }
    
    suspend fun listResources(): List<Resource> {
        if (!isConnected) {
            throw IllegalStateException("MCP client not connected")
        }
        
        return try {
            val response = client.listResources()
            response.resources.map { resource ->
                Resource(
                    uri = resource.uri,
                    name = resource.name,
                    description = resource.description,
                    mimeType = resource.mimeType
                )
            }
        } catch (e: Exception) {
            // Проверяем, не поддерживает ли сервер ресурсы
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("does not support resources", ignoreCase = true) ||
                errorMessage.contains("not support resources", ignoreCase = true) ||
                errorMessage.contains("ResourcesList", ignoreCase = true)) {
                emptyList()
            } else {
                // Другие ошибки пробрасываем дальше
                logger.error("Failed to list resources: ${e.message}", e)
                throw e
            }
        }
    }
    
    suspend fun disconnect() {
        try {
            transport?.close()
        } catch (e: Exception) {
            // Игнорируем ошибки при закрытии
        }
        isConnected = false
        transport = null
    }
    
    suspend fun callTool(toolName: String, arguments: Map<String, Any>?): String {
        if (!isConnected) {
            throw IllegalStateException("MCP client not connected")
        }
        
        // Логируем аргументы для отладки
        logger.debug("MCPClient.callTool: toolName=$toolName, arguments=$arguments")
        
        val response = client.callTool(
            name = toolName,
            arguments = arguments ?: emptyMap()
        ) ?: throw IllegalStateException("Tool call returned null response")
        
        // Преобразуем результат в читаемую строку
        // response.content - это список Content элементов
        return response.content.joinToString("\n\n") { content ->
            // Извлекаем текст из TextContent, убирая префикс "TextContent(text=" и суффикс ", annotations=null)"
            val contentStr = content.toString()
            if (contentStr.startsWith("TextContent(text=") && contentStr.endsWith(", annotations=null)")) {
                // Извлекаем текст между кавычками
                val startIndex = contentStr.indexOf("text=") + 5
                val endIndex = contentStr.lastIndexOf(", annotations")
                if (startIndex < endIndex) {
                    val text = contentStr.substring(startIndex, endIndex).trim()
                    // Убираем кавычки, если есть
                    if (text.startsWith("\"") && text.endsWith("\"")) {
                        text.substring(1, text.length - 1)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                    } else {
                        text
                    }
                } else {
                    contentStr
                }
            } else {
                contentStr
            }
        }
    }
    
    fun isConnected(): Boolean = isConnected
}

