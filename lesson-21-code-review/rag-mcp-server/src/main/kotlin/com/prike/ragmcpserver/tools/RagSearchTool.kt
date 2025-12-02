package com.prike.ragmcpserver.tools

import com.prike.ragmcpserver.tools.handlers.RagSearchHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента rag_search для MCP сервера
 */
class RagSearchTool(
    private val handler: RagSearchHandler
) {
    private val logger = LoggerFactory.getLogger(RagSearchTool::class.java)
    
    fun register(server: Server) {
        server.addTool(
            name = "rag_search",
            description = "Семантический поиск по документации проекта. Используй для общих вопросов о проекте, API, архитектуре, функциях и возможностях системы.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Поисковый запрос (вопрос пользователя)")
                    })
                    put("topK", buildJsonObject {
                        put("type", "number")
                        put("description", "Количество результатов (по умолчанию: 5)")
                        put("default", 5)
                    })
                    put("minSimilarity", buildJsonObject {
                        put("type", "number")
                        put("description", "Минимальное сходство (0.0-1.0, по умолчанию: 0.4)")
                        put("default", 0.4)
                    })
                },
                required = listOf("query")
            )
        ) { request ->
            logger.debug("Вызов инструмента rag_search с аргументами: ${request.arguments}")
            val params = RagSearchHandler.parseParams(request.arguments)
            kotlinx.coroutines.runBlocking {
                handler.handle(params)
            }
        }
    }
}

