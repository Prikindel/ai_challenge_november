package com.prike.ragmcpserver.tools

import com.prike.ragmcpserver.tools.handlers.RagIndexDocumentsHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента rag_index_documents для MCP сервера
 */
class RagIndexDocumentsTool(
    private val handler: RagIndexDocumentsHandler
) {
    private val logger = LoggerFactory.getLogger(RagIndexDocumentsTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "rag_index_documents",
            description = "Индексирует документы из указанной папки. Разбивает на чанки, генерирует эмбеддинги и сохраняет в базу знаний.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("documentsPath", buildJsonObject {
                        put("type", "string")
                        put("description", "Путь к папке с документами относительно корня проекта. Если не указан, используется путь из конфигурации.")
                    })
                },
                required = emptyList()
            )
        ) { request ->
            logger.debug("Вызов инструмента rag_index_documents с аргументами: ${request.arguments}")
            val params = RagIndexDocumentsHandler.parseParams(request.arguments)
            kotlinx.coroutines.runBlocking {
                handler.handle(params)
            }
        }
    }
}

