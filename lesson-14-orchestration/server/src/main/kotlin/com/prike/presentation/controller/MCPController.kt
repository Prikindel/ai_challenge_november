package com.prike.presentation.controller

import com.prike.data.client.MCPClientManager
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для API управления MCP серверами
 */
class MCPController(
    private val mcpClientManager: MCPClientManager
) {
    private val logger = LoggerFactory.getLogger(MCPController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.route("/api/mcp") {
            // Список подключённых MCP серверов
            get("/servers") {
                val servers = mcpClientManager.getConnectedServers().map { serverId ->
                    ServerInfoDto(serverId = serverId)
                }
                call.respond(ServersResponseDto(servers = servers))
            }
            
            // Список всех доступных инструментов
            get("/tools") {
                val tools = kotlinx.coroutines.runBlocking {
                    mcpClientManager.listAllTools()
                }
                call.respond(ToolsResponseDto(tools = tools.map { tool ->
                    ToolInfoDto(
                        name = tool.name,
                        description = tool.description
                    )
                }))
            }
            
            // Статус подключений
            get("/connections") {
                val status = mcpClientManager.getConnectionStatus()
                call.respond(ConnectionsResponseDto(connections = status))
            }
        }
    }
}

@Serializable
data class ServerInfoDto(
    val serverId: String
)

@Serializable
data class ServersResponseDto(
    val servers: List<ServerInfoDto>
)

@Serializable
data class ToolInfoDto(
    val name: String,
    val description: String?
)

@Serializable
data class ToolsResponseDto(
    val tools: List<ToolInfoDto>
)

@Serializable
data class ConnectionsResponseDto(
    val connections: Map<String, Boolean>
)

