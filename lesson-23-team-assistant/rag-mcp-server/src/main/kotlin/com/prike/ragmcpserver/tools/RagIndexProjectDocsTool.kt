package com.prike.ragmcpserver.tools

import com.prike.ragmcpserver.tools.handlers.RagIndexProjectDocsHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента rag_index_project_docs для MCP сервера
 */
class RagIndexProjectDocsTool(
    private val handler: RagIndexProjectDocsHandler
) {
    private val logger = LoggerFactory.getLogger(RagIndexProjectDocsTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "rag_index_project_docs",
            description = "Индексирует документацию проекта (project/docs/ и project/README.md). Используй для обновления базы знаний с документацией проекта.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("projectDocsPath", buildJsonObject {
                        put("type", "string")
                        put("description", "Путь к папке с документацией проекта (по умолчанию: project/docs)")
                    })
                    put("projectReadmePath", buildJsonObject {
                        put("type", "string")
                        put("description", "Путь к README проекта (по умолчанию: project/README.md)")
                    })
                },
                required = emptyList()
            )
        ) { request ->
            logger.debug("Вызов инструмента rag_index_project_docs с аргументами: ${request.arguments}")
            val params = RagIndexProjectDocsHandler.parseParams(request.arguments)
            kotlinx.coroutines.runBlocking {
                handler.handle(params)
            }
        }
    }
}

