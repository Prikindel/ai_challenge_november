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
                            // isConnected показывает реальное подключение через MCP протокол
                            // Для заглушек и адаптеров это может быть false, но сервер все равно доступен
                            // Для выключенных серверов (enabled: false) клиент не создается, поэтому isConnected всегда false
                            val isConnected = if (serverConfig.enabled) {
                                mcpRouterService.isServerConnected(name)
                            } else {
                                false // Выключенные серверы не подключены
                            }
                            MCPServerDto(
                                name = name,
                                enabled = serverConfig.enabled,
                                description = serverConfig.description,
                                isConnected = isConnected
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
                        logger.debug("Getting MCP tools...")
                        val tools = kotlinx.coroutines.runBlocking {
                            mcpRouterService.getAllAvailableTools()
                        }
                        logger.debug("Retrieved ${tools.size} tools from MCP servers")
                        
                        val toolsDto = tools.map { tool ->
                            MCPToolDto(
                                serverName = tool.serverName,
                                name = tool.name,
                                description = tool.description,
                                parameters = tool.parameters.mapValues { it.value.toString() }
                            )
                        }
                        
                        // Добавляем информацию о выключенных серверах
                        val config = mcpConfigService.getConfig()
                        val disabledServers = config.servers.filter { !it.value.enabled }
                        val disabledServersInfo = disabledServers.map { (name, serverConfig) ->
                            MCPToolDto(
                                serverName = serverConfig.name,
                                name = "disabled",
                                description = "Этот MCP сервер выключен в конфигурации (enabled: false). Включите его в config/mcp-servers.yaml, чтобы использовать.",
                                parameters = emptyMap()
                            )
                        }
                        
                        logger.debug("Sending ${toolsDto.size} tools and ${disabledServersInfo.size} disabled servers info to client")
                        call.respond(toolsDto + disabledServersInfo)
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


