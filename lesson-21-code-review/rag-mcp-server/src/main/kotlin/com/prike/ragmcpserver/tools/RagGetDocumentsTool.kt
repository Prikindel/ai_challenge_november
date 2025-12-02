package com.prike.ragmcpserver.tools

import com.prike.ragmcpserver.tools.handlers.RagGetDocumentsHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента rag_get_documents для MCP сервера
 */
class RagGetDocumentsTool(
    private val handler: RagGetDocumentsHandler
) {
    private val logger = LoggerFactory.getLogger(RagGetDocumentsTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "rag_get_documents",
            description = "Получить список всех индексированных документов",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    // Параметры не требуются
                },
                required = emptyList()
            )
        ) { request ->
            logger.debug("Вызов инструмента rag_get_documents с аргументами: ${request.arguments}")
            val params = RagGetDocumentsHandler.parseParams(request.arguments)
            kotlinx.coroutines.runBlocking {
                handler.handle(params)
            }
        }
    }
}

