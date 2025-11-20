package com.prike.domain.agent

import com.prike.data.client.MCPClientManager
import com.prike.data.dto.MCPTool
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Агент для работы с MCP инструментами через MCPClientManager
 */
class MCPToolAgent(
    private val mcpClientManager: MCPClientManager
) {
    private val logger = LoggerFactory.getLogger(MCPToolAgent::class.java)
    
    suspend fun callTool(
        sourceId: String,
        toolName: String,
        arguments: JsonObject
    ): ToolResult {
        return try {
            val result = mcpClientManager.callTool(sourceId, toolName, arguments)
            ToolResult.Success(result)
        } catch (e: Exception) {
            logger.error("Error calling tool $toolName from source $sourceId: ${e.message}", e)
            ToolResult.Error(e.message ?: "Unknown error", e)
        }
    }
    
    suspend fun getAvailableTools(): List<ToolInfo> {
        return try {
            val allTools = mcpClientManager.getAllTools()
            allTools.flatMap { (sourceId, tools) ->
                tools.map { tool ->
                    ToolInfo.fromMCPTool(tool, sourceId)
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting available tools: ${e.message}", e)
            emptyList()
        }
    }
    
    sealed class ToolResult {
        data class Success(val result: String) : ToolResult()
        data class Error(val message: String, val cause: Throwable? = null) : ToolResult()
    }
    
    data class ToolInfo(
        val name: String,
        val description: String?,
        val sourceId: String, // ID источника данных
        val inputSchema: JsonObject? = null
    ) {
        companion object {
            fun fromMCPTool(tool: MCPTool, sourceId: String): ToolInfo {
                return ToolInfo(
                    name = tool.name,
                    description = tool.description,
                    sourceId = sourceId,
                    inputSchema = tool.inputSchema
                )
            }
        }
    }
}

