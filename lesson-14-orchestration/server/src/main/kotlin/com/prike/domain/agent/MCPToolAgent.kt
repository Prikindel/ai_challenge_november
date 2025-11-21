package com.prike.domain.agent

import com.prike.data.client.MCPClientManager
import com.prike.data.dto.MCPTool
import com.prike.data.dto.ToolDto
import com.prike.data.dto.FunctionDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Агент для работы с MCP инструментами через MCPClientManager
 * Преобразует MCP инструменты в формат LLM tools
 */
class MCPToolAgent(
    private val mcpClientManager: MCPClientManager
) {
    private val logger = LoggerFactory.getLogger(MCPToolAgent::class.java)
    
    /**
     * Преобразует MCP инструменты в формат LLM tools (для function calling)
     * Использует описания инструментов напрямую из MCP серверов без изменений
     */
    suspend fun getLLMTools(): List<ToolDto> {
        val mcpTools = mcpClientManager.listAllTools()
        
        return mcpTools.map { mcpTool ->
            // Используем inputSchema от MCP напрямую
            val parameters = mcpTool.inputSchema ?: run {
                logger.warn("Tool '${mcpTool.name}' has no inputSchema, using empty schema")
                buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {})
                    put("required", buildJsonArray {})
                }
            }
            
            // Используем описание инструмента напрямую из MCP сервера
            // Не изменяем и не дополняем описания - они должны быть в самих MCP серверах
            ToolDto(
                type = "function",
                function = FunctionDto(
                    name = mcpTool.name,
                    description = mcpTool.description ?: "Инструмент ${mcpTool.name}",
                    parameters = parameters
                )
            )
        }
    }
    
    /**
     * Вызывает MCP инструмент по имени
     */
    suspend fun callTool(toolName: String, arguments: JsonObject): String {
        val serverId = mcpClientManager.findServerForTool(toolName)
            ?: throw IllegalArgumentException("Tool not found: $toolName")
        
        return mcpClientManager.callTool(serverId, toolName, arguments)
    }
}

