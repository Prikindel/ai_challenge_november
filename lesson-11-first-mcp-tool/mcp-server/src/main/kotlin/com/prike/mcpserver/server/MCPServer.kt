package com.prike.mcpserver.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import com.prike.mcpserver.tools.ToolRegistry
import io.ktor.utils.io.streams.asInput
import kotlinx.io.asSink
import kotlinx.io.buffered
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

class MCPServer(
    private val serverInfo: Implementation,
    private val toolRegistry: ToolRegistry
) {
    private val logger = LoggerFactory.getLogger(MCPServer::class.java)
    
    private val server = Server(
        serverInfo = serverInfo,
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null)
            )
        )
    )
    
    fun start() {
        logger.info("Starting MCP Server: ${serverInfo.name} v${serverInfo.version}")
        
        // Регистрация всех инструментов из ToolRegistry
        toolRegistry.registerTools(server)
        
        // Запуск сервера с stdio транспортом
        val transport = StdioServerTransport(
            inputStream = System.`in`.asInput(),
            outputStream = System.out.asSink().buffered()
        )
        
        runBlocking {
            val session = server.createSession(transport)
            logger.info("MCP Server started and waiting for connections...")
            
            val done = Job()
            session.onClose {
                logger.info("MCP Server session closed")
                done.complete()
            }
            
            done.join()
        }
    }
}

