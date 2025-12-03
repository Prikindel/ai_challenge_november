package com.prike.ragmcpserver.tools

import com.prike.ragmcpserver.tools.handlers.RagGetStatisticsHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента rag_get_statistics для MCP сервера
 */
class RagGetStatisticsTool(
    private val handler: RagGetStatisticsHandler
) {
    private val logger = LoggerFactory.getLogger(RagGetStatisticsTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "rag_get_statistics",
            description = "Получить статистику базы знаний: количество документов и чанков",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    // Параметры не требуются
                },
                required = emptyList()
            )
        ) { request ->
            logger.debug("Вызов инструмента rag_get_statistics с аргументами: ${request.arguments}")
            val params = RagGetStatisticsHandler.parseParams(request.arguments)
            kotlinx.coroutines.runBlocking {
                handler.handle(params)
            }
        }
    }
}

