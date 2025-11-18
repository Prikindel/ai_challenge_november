package com.prike.presentation.controller

import com.prike.data.client.MCPClient
import com.prike.presentation.dto.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import java.io.File
import org.slf4j.LoggerFactory

class MCPConnectionController(
    private val mcpClient: MCPClient,
    private val lessonRoot: String
) {
    private val logger = LoggerFactory.getLogger(MCPConnectionController::class.java)
    
    fun configureRoutes(routing: Routing) {
        routing.route("/api/mcp") {
            // POST /api/mcp/connect - подключиться к MCP серверу
            post("/connect") {
                call.handleConnect()
            }
            
            // POST /api/mcp/disconnect - отключиться от MCP сервера
            post("/disconnect") {
                call.handleDisconnect()
            }
            
            // GET /api/mcp/status - получить статус подключения
            get("/status") {
                call.handleStatus()
            }
        }
    }
    
    private suspend fun ApplicationCall.handleConnect() {
        try {
            // Проверяем, не подключены ли уже
            if (mcpClient.isConnected()) {
                logger.info("MCP client is already connected")
                respond(HttpStatusCode.OK, ConnectMCPResponseDto(
                    success = true,
                    message = "Already connected to MCP server"
                ))
                return
            }
            
            val request = receive<ConnectMCPRequestDto>()
            
            // Если serverJarPath не указан или равен "class", используем режим разработки (через Gradle)
            val jarPath = if (request.serverJarPath == null || request.serverJarPath == "class") {
                logger.info("Connecting to MCP server in development mode (via Gradle)")
                null
            } else {
                val fullPath = File(lessonRoot, request.serverJarPath).absolutePath
                logger.info("Connecting to MCP server from JAR: $fullPath")
                
                if (!File(fullPath).exists()) {
                    logger.warn("MCP server JAR not found: $fullPath")
                    respond(HttpStatusCode.NotFound, ErrorDto(
                        message = "MCP server JAR not found: $fullPath"
                    ))
                    return
                }
                fullPath
            }
            
            mcpClient.connectToServer(jarPath)
            
            respond(HttpStatusCode.OK, ConnectMCPResponseDto(
                success = true,
                message = "Connected to MCP server"
            ))
        } catch (e: Exception) {
            logger.error("Failed to connect to MCP server: ${e.message}", e)
            respond(HttpStatusCode.InternalServerError, ErrorDto(
                message = "Failed to connect: ${e.message}"
            ))
        }
    }
    
    private suspend fun ApplicationCall.handleDisconnect() {
        try {
            mcpClient.disconnect()
            respond(HttpStatusCode.OK, ConnectMCPResponseDto(
                success = true,
                message = "Disconnected from MCP server"
            ))
        } catch (e: Exception) {
            logger.error("Failed to disconnect: ${e.message}", e)
            respond(HttpStatusCode.InternalServerError, ErrorDto(
                message = "Failed to disconnect: ${e.message}"
            ))
        }
    }
    
    private suspend fun ApplicationCall.handleStatus() {
        try {
            val isConnected = mcpClient.isConnected()
            respond(HttpStatusCode.OK, ConnectMCPResponseDto(
                success = isConnected,
                message = if (isConnected) "Connected" else "Not connected"
            ))
        } catch (e: Exception) {
            logger.error("Failed to get status: ${e.message}", e)
            respond(HttpStatusCode.InternalServerError, ErrorDto(
                message = "Failed to get status: ${e.message}"
            ))
        }
    }
}

