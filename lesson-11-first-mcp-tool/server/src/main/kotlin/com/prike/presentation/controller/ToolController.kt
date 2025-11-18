package com.prike.presentation.controller

import com.prike.domain.agent.MCPToolAgent
import com.prike.presentation.dto.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

class ToolController(
    private val mcpToolAgent: MCPToolAgent
) {
    private val logger = LoggerFactory.getLogger(ToolController::class.java)
    
    fun configureRoutes(routing: Routing) {
        routing.route("/api/tools") {
            // GET /api/tools - получить список доступных инструментов
            get {
                call.handleGetTools()
            }
            
            // POST /api/tools/call - вызвать инструмент
            post("/call") {
                call.handleCallTool()
            }
        }
    }
    
    private suspend fun ApplicationCall.handleGetTools() {
        try {
            val tools = mcpToolAgent.getAvailableTools()
            respond(HttpStatusCode.OK, ToolsListDto(
                tools = tools.map { tool ->
                    ToolDto(
                        name = tool.name,
                        description = tool.description
                    )
                }
            ))
        } catch (e: Exception) {
            logger.error("Failed to get tools: ${e.message}", e)
            respond(HttpStatusCode.InternalServerError, ErrorDto(
                message = e.message ?: "Unknown error"
            ))
        }
    }
    
    private suspend fun ApplicationCall.handleCallTool() {
        try {
            val request = receive<CallToolRequestDto>()
            
            // Преобразуем Map<String, String> в Map<String, Any>
            val arguments = request.arguments.mapValues { it.value as Any }
            
            val result = mcpToolAgent.callTool(request.toolName, arguments)
            
            when (result) {
                is MCPToolAgent.ToolResult.Success -> {
                    respond(HttpStatusCode.OK, CallToolResponseDto(
                        success = true,
                        result = result.result
                    ))
                }
                is MCPToolAgent.ToolResult.Error -> {
                    respond(HttpStatusCode.InternalServerError, CallToolResponseDto(
                        success = false,
                        error = result.message
                    ))
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to call tool: ${e.message}", e)
            respond(HttpStatusCode.BadRequest, CallToolResponseDto(
                success = false,
                error = e.message ?: "Unknown error"
            ))
        }
    }
}

