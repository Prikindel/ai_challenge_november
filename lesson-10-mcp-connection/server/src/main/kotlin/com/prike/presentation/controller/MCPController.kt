package com.prike.presentation.controller

import com.prike.config.MCPConfigLoader
import com.prike.domain.agent.MCPConnectionAgent
import com.prike.presentation.dto.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

class MCPController(
    private val mcpConnectionAgent: MCPConnectionAgent,
    private val mcpConfigLoader: MCPConfigLoader,
    private val lessonRoot: String
) {
    private val logger = LoggerFactory.getLogger(MCPController::class.java)
    
    fun configureRoutes(routing: Routing) {
        routing.route("/api/mcp") {
            // GET /api/mcp/servers - получить список доступных серверов
            get("/servers") {
                this@MCPController.handleGetServers(call)
            }
            
            // POST /api/mcp/connect - подключиться к серверу
            post("/connect") {
                this@MCPController.handleConnect(call)
            }
            
            // POST /api/mcp/disconnect - отключиться от сервера
            post("/disconnect") {
                this@MCPController.handleDisconnect(call)
            }
        }
    }
    
    private suspend fun handleGetServers(call: ApplicationCall) {
        try {
            val config = mcpConfigLoader.loadConfig(lessonRoot)
            val servers = config.servers.map { server ->
                ServerInfoDto(
                    name = server.name,
                    type = server.type,
                    enabled = server.enabled,
                    description = server.description
                )
            }
            call.respond(HttpStatusCode.OK, ServersListDto(servers = servers))
        } catch (e: Exception) {
            logger.error("Failed to get servers", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto(message = e.message ?: "Unknown error"))
        }
    }
    
    private suspend fun handleConnect(call: ApplicationCall) {
        try {
            val request = call.receive<ConnectRequestDto>()
            val config = mcpConfigLoader.loadConfig(lessonRoot)
            val serverConfig = config.servers.find { it.name == request.serverName }
                ?: throw IllegalArgumentException("Server not found: ${request.serverName}")
            
            if (!serverConfig.enabled) {
                call.respond(HttpStatusCode.BadRequest, ErrorDto(message = "Server is disabled"))
                return
            }
            
            logger.info("Connecting to MCP server: ${serverConfig.name}")
            
            // Отключаемся от предыдущего сервера, если подключены
            mcpConnectionAgent.disconnect()
            
            val result = mcpConnectionAgent.connectToServer(serverConfig)
            
            when (result) {
                is MCPConnectionAgent.ConnectionResult.Success -> {
                    logger.info("Successfully connected to MCP server: ${result.serverName}")
                    call.respond(HttpStatusCode.OK, ConnectResponseDto(
                        success = true,
                        serverName = result.serverName,
                        serverDescription = result.serverDescription,
                        tools = result.tools.map { tool ->
                            ToolDto(
                                name = tool.name,
                                description = tool.description,
                                inputSchema = tool.inputSchema
                            )
                        },
                        resources = result.resources.map { resource ->
                            ResourceDto(
                                uri = resource.uri,
                                name = resource.name,
                                description = resource.description,
                                mimeType = resource.mimeType
                            )
                        }
                    ))
                }
                is MCPConnectionAgent.ConnectionResult.Error -> {
                    logger.error("Failed to connect to MCP server: ${result.message}", result.cause)
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto(
                        message = result.message
                    ))
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to connect", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto(message = e.message ?: "Unknown error"))
        }
    }
    
    private suspend fun handleDisconnect(call: ApplicationCall) {
        try {
            mcpConnectionAgent.disconnect()
            logger.info("Disconnected from MCP server")
            call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "Disconnected"))
        } catch (e: Exception) {
            logger.error("Failed to disconnect", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto(message = e.message ?: "Unknown error"))
        }
    }
}

