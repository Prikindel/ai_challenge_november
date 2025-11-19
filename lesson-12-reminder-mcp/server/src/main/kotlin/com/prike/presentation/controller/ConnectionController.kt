package com.prike.presentation.controller

import com.prike.data.client.MCPClientManager
import com.prike.presentation.dto.ConnectionStatusDto
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для управления подключениями к MCP серверам
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
                    val connectedCount = status.values.count { it }
                    val totalCount = status.size
                    
                    call.respond(ConnectionStatusDto(
                        sources = status,
                        connectedCount = connectedCount,
                        totalCount = totalCount
                    ))
                } catch (e: Exception) {
                    logger.error("Error getting connection status: ${e.message}", e)
                    call.respond(
                        ConnectionStatusDto(
                            sources = emptyMap(),
                            connectedCount = 0,
                            totalCount = 0
                        )
                    )
                }
            }
        }
    }
}

