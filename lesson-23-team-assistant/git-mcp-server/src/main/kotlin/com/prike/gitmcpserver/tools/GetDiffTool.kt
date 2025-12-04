package com.prike.gitmcpserver.tools

import com.prike.gitmcpserver.tools.handlers.GetDiffHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_diff для MCP сервера
 * Делегирует обработку запросов GetDiffHandler
 */
class GetDiffTool(
    private val handler: GetDiffHandler
) {
    private val logger = LoggerFactory.getLogger(GetDiffTool::class.java)
    
    /**
     * Регистрация инструмента на сервере
     */
    fun register(server: Server) {
        server.addTool(
            name = "get_diff",
            description = "Получить diff между двумя ветками git. Возвращает полный diff в формате unified diff. Параметры: base - базовая ветка (например, 'main' или 'origin/main'), head - целевая ветка (например, 'feature-branch' или 'HEAD').",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("base", buildJsonObject {
                        put("type", "string")
                        put("description", "Базовая ветка для сравнения (например, 'main', 'origin/main', 'develop')")
                    })
                    put("head", buildJsonObject {
                        put("type", "string")
                        put("description", "Целевая ветка для сравнения (например, 'feature-branch', 'HEAD', 'origin/feature-branch')")
                    })
                },
                required = listOf("base", "head")
            )
        ) { request ->
            logger.debug("Вызов инструмента get_diff с аргументами: ${request.arguments}")
            
            val params = GetDiffHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

