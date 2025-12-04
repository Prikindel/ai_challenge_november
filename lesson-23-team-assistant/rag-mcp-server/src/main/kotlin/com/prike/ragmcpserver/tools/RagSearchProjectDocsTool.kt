package com.prike.ragmcpserver.tools

import com.prike.ragmcpserver.tools.handlers.RagSearchProjectDocsHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента rag_search_project_docs для MCP сервера
 */
class RagSearchProjectDocsTool(
    private val handler: RagSearchProjectDocsHandler
) {
    private val logger = LoggerFactory.getLogger(RagSearchProjectDocsTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "rag_search_project_docs",
            description = "Семантический поиск только в документации проекта (project/docs/ и project/README.md). Используй для вопросов о структуре проекта, API endpoints, схеме данных, правилах стиля кода.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Поисковый запрос (вопрос пользователя)")
                    })
                    put("topK", buildJsonObject {
                        put("type", "number")
                        put("description", "Количество результатов (по умолчанию: 10)")
                        put("default", 10)
                    })
                    put("minSimilarity", buildJsonObject {
                        put("type", "number")
                        put("description", "Минимальное сходство (0.0-1.0, по умолчанию: 0.0)")
                        put("default", 0.0)
                    })
                },
                required = listOf("query")
            )
        ) { request ->
            logger.debug("Вызов инструмента rag_search_project_docs с аргументами: ${request.arguments}")
            val params = RagSearchProjectDocsHandler.parseParams(request.arguments)
            kotlinx.coroutines.runBlocking {
                handler.handle(params)
            }
        }
    }
}

