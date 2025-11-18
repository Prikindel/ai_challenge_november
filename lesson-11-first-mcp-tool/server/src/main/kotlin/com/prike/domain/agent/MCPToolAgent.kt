package com.prike.domain.agent

import com.prike.data.client.MCPClient
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

class MCPToolAgent(
    private val mcpClient: MCPClient
) {
    private val logger = LoggerFactory.getLogger(MCPToolAgent::class.java)
    
    suspend fun callTool(
        toolName: String,
        arguments: JsonObject
    ): ToolResult {
        return try {
            val result = mcpClient.callTool(toolName, arguments)
            ToolResult.Success(result)
        } catch (e: Exception) {
            logger.error("Error calling tool $toolName: ${e.message}", e)
            ToolResult.Error(e.message ?: "Unknown error", e)
        }
    }
    
    suspend fun getAvailableTools(): List<ToolInfo> {
        return try {
            mcpClient.listTools().map { tool ->
                ToolInfo.fromMCPTool(tool)
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
        val inputSchema: JsonObject? = null
    ) {
        companion object {
            fun fromMCPTool(tool: com.prike.data.dto.MCPTool): ToolInfo {
                return ToolInfo(
                    name = tool.name,
                    description = tool.description,
                    inputSchema = tool.inputSchema
                )
            }
        }
    }
}

