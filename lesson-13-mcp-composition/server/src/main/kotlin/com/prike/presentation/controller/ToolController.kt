package com.prike.presentation.controller

import com.prike.data.client.MCPClientManager
import com.prike.config.Config
import com.prike.presentation.dto.AllToolsResponse
import com.prike.presentation.dto.ToolDto
import com.prike.presentation.dto.ToolsByServerDto
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Контроллер для работы с инструментами MCP
 */
class ToolController(
    private val mcpClientManager: MCPClientManager,
    private val config: com.prike.config.MCPConfig
) {
    private val logger = LoggerFactory.getLogger(ToolController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.route("/api/tools") {
            get {
                try {
                    val allTools = runBlocking {
                        mcpClientManager.listAllTools()
                    }
                    
                    // Группируем инструменты по серверам
                    val toolsByServer = mutableMapOf<String, MutableList<com.prike.data.dto.MCPTool>>()
                    
                    allTools.forEach { tool ->
                        // Находим сервер для инструмента
                        val serverId = mcpClientManager.findServerForTool(tool.name)
                        if (serverId != null) {
                            toolsByServer.getOrPut(serverId) { mutableListOf() }.add(tool)
                        }
                    }
                    
                    val toolsByServerDto = toolsByServer.map { (serverId, tools) ->
                        val serverConfig = config.servers.find { it.id == serverId }
                        ToolsByServerDto(
                            server = serverId,
                            serverName = serverConfig?.name ?: serverId,
                            tools = tools.map { tool ->
                                ToolDto(
                                    name = tool.name,
                                    description = tool.description,
                                    server = serverId
                                )
                            }
                        )
                    }
                    
                    val total = toolsByServerDto.sumOf { it.tools.size }
                    
                    call.respond(AllToolsResponse(
                        tools = toolsByServerDto,
                        total = total
                    ))
                } catch (e: Exception) {
                    logger.error("Error getting tools: ${e.message}", e)
                    call.respond(
                        AllToolsResponse(
                            tools = emptyList(),
                            total = 0
                        )
                    )
                }
            }
        }
    }
}

