package com.prike.mcpcommon.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.ktor.utils.io.streams.asInput
import kotlinx.io.asSink
import kotlinx.io.buffered
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

/**
 * Интерфейс для реестра инструментов
 * Каждый MCP сервер должен реализовать свой ToolRegistry
 */
interface ToolRegistry {
    /**
     * Регистрирует все инструменты на MCP сервере
     */
    fun registerTools(server: Server)
}

/**
 * Базовый класс для MCP серверов
 * Содержит общую логику запуска и управления сервером
 */
abstract class BaseMCPServer(
    protected val serverInfo: Implementation,
    protected val toolRegistry: ToolRegistry
) {
    protected val logger = LoggerFactory.getLogger(this::class.java)
    
    protected val server = Server(
        serverInfo = serverInfo,
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null)
            )
        )
    )
    
    /**
     * Запускает MCP сервер
     */
    fun start() {
        logger.info("Starting ${serverInfo.name} MCP Server: v${serverInfo.version}")
        
        // Регистрация всех инструментов из ToolRegistry
        toolRegistry.registerTools(server)
        
        // Запуск сервера с stdio транспортом
        val transport = StdioServerTransport(
            inputStream = System.`in`.asInput(),
            outputStream = System.out.asSink().buffered()
        )
        
        runBlocking {
            val session = server.createSession(transport)
            logger.info("${serverInfo.name} MCP Server started and waiting for connections...")
            
            val done = Job()
            session.onClose {
                logger.info("${serverInfo.name} MCP Server session closed")
                done.complete()
            }
            
            done.join()
        }
    }
}

