package com.prike.ragmcpserver.tools

import com.prike.ragmcpserver.tools.handlers.RagIndexSupportDocsHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента rag_index_support_docs для MCP сервера
 */
class RagIndexSupportDocsTool(
    private val handler: RagIndexSupportDocsHandler
) {
    private val logger = LoggerFactory.getLogger(RagIndexSupportDocsTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "rag_index_support_docs",
            description = "Индексирует документацию поддержки (FAQ, troubleshooting, user guide, auth guide) из папки project/docs/support/. Используй для обновления базы знаний с документацией поддержки.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("supportDocsPath", buildJsonObject {
                        put("type", "string")
                        put("description", "Путь к папке с документацией поддержки (по умолчанию: project/docs/support)")
                    })
                },
                required = emptyList()
            )
        ) { request ->
            logger.debug("Вызов инструмента rag_index_support_docs с аргументами: ${request.arguments}")
            val params = RagIndexSupportDocsHandler.parseParams(request.arguments)
            kotlinx.coroutines.runBlocking {
                handler.handle(params)
            }
        }
    }
}

