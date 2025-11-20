package com.prike.presentation.controller

import com.prike.data.client.MCPClientManager
import com.prike.presentation.dto.ConnectionStatusDto
import com.prike.presentation.dto.ConnectionsResponse
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для работы с подключениями к MCP серверам
 */
class ConnectionController(
    private val mcpClientManager: MCPClientManager
) {
    private val logger = LoggerFactory.getLogger(ConnectionController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.route("/api/connections") {
            get {
                try {
                    val status = mcpClientManager.getConnectionStatus()
                    
                    val connections = status.map { (serverId, connected) ->
                        ConnectionStatusDto(
                            server = serverId,
                            connected = connected
                        )
                    }
                    
                    val connected = connections.count { it.connected }
                    
                    call.respond(ConnectionsResponse(
                        connections = connections,
                        total = connections.size,
                        connected = connected
                    ))
                } catch (e: Exception) {
                    logger.error("Error getting connections: ${e.message}", e)
                    call.respond(
                        ConnectionsResponse(
                            connections = emptyList(),
                            total = 0,
                            connected = 0
                        )
                    )
                }
            }
        }
    }
}

