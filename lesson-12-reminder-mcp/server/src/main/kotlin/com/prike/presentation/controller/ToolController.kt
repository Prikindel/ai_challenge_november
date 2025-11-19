package com.prike.presentation.controller

import com.prike.data.client.MCPClientManager
import com.prike.presentation.dto.AllToolsResponse
import com.prike.presentation.dto.ToolDto
import com.prike.presentation.dto.ToolsBySourceDto
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Контроллер для работы с инструментами MCP
 */
class ToolController(
    private val mcpClientManager: MCPClientManager
) {
    private val logger = LoggerFactory.getLogger(ToolController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.route("/api/tools") {
            get {
                try {
                    val allTools = runBlocking {
                        mcpClientManager.getAllTools()
                    }
                    
                    val toolsBySource = allTools.map { (sourceId, tools) ->
                        ToolsBySourceDto(
                            source = sourceId,
                            sourceName = sourceId, // Можно расширить, добавив имя из конфига
                            tools = tools.map { tool ->
                                ToolDto(
                                    name = tool.name,
                                    description = tool.description,
                                    source = sourceId
                                )
                            }
                        )
                    }
                    
                    val total = toolsBySource.sumOf { it.tools.size }
                    
                    call.respond(AllToolsResponse(
                        tools = toolsBySource,
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

