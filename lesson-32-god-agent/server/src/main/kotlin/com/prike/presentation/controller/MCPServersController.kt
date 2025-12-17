package com.prike.presentation.controller

import com.prike.domain.service.MCPConfigService
import com.prike.domain.service.MCPRouterService
import com.prike.presentation.controller.ErrorResponse
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для управления MCP серверами
 */
class MCPServersController(
    private val mcpConfigService: MCPConfigService,
    private val mcpRouterService: MCPRouterService
) {
    private val logger = LoggerFactory.getLogger(MCPServersController::class.java)
    
    fun registerRoutes(application: Application) {
        application.routing {
            route("/api/mcp-servers") {
                /**
                 * Получить список всех MCP серверов
                 */
                get {
                    try {
                        val config = mcpConfigService.getConfig()
                        val servers = config.servers.map { (name, serverConfig) ->
                            MCPServerDto(
                                name = name,
                                enabled = serverConfig.enabled,
                                description = serverConfig.description,
                                isConnected = mcpRouterService.isServerAvailable(name)
                            )
                        }
                        
                        call.respond(
                            MCPServersResponse(
                                enabled = config.enabled,
                                servers = servers
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("Failed to get MCP servers: ${e.message}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to get MCP servers: ${e.message}")
                        )
                    }
                }
                
                /**
                 * Получить список доступных инструментов от всех серверов
                 */
                get("/tools") {
                    try {
                        val tools = mcpRouterService.getAllAvailableTools()
                        val toolsDto = tools.map { tool ->
                            MCPToolDto(
                                serverName = tool.serverName,
                                name = tool.name,
                                description = tool.description,
                                parameters = tool.parameters.mapValues { it.value.toString() }
                            )
                        }
                        
                        call.respond(toolsDto)
                    } catch (e: Exception) {
                        logger.error("Failed to get MCP tools: ${e.message}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to get MCP tools: ${e.message}")
                        )
                    }
                }
                
                /**
                 * Подключить все MCP серверы
                 */
                post("/connect") {
                    try {
                        kotlinx.coroutines.runBlocking {
                            mcpRouterService.connectAllClients()
                        }
                        call.respond(
                            HttpStatusCode.OK,
                            mapOf("status" to "success", "message" to "All MCP servers connected")
                        )
                    } catch (e: Exception) {
                        logger.error("Failed to connect MCP servers: ${e.message}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to connect MCP servers: ${e.message}")
                        )
                    }
                }
                
                /**
                 * Отключить все MCP серверы
                 */
                post("/disconnect") {
                    try {
                        kotlinx.coroutines.runBlocking {
                            mcpRouterService.disconnectAllClients()
                        }
                        call.respond(
                            HttpStatusCode.OK,
                            mapOf("status" to "success", "message" to "All MCP servers disconnected")
                        )
                    } catch (e: Exception) {
                        logger.error("Failed to disconnect MCP servers: ${e.message}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to disconnect MCP servers: ${e.message}")
                        )
                    }
                }
            }
        }
    }
}

@Serializable
data class MCPServerDto(
    val name: String,
    val enabled: Boolean,
    val description: String,
    val isConnected: Boolean
)

@Serializable
data class MCPServersResponse(
    val enabled: Boolean,
    val servers: List<MCPServerDto>
)

@Serializable
data class MCPToolDto(
    val serverName: String,
    val name: String,
    val description: String,
    val parameters: Map<String, String> // Упрощаем до String для сериализации
)


