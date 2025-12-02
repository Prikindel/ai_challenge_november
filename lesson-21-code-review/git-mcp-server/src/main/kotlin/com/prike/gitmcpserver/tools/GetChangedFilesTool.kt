package com.prike.gitmcpserver.tools

import com.prike.gitmcpserver.tools.handlers.GetChangedFilesHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_changed_files для MCP сервера
 * Делегирует обработку запросов GetChangedFilesHandler
 */
class GetChangedFilesTool(
    private val handler: GetChangedFilesHandler
) {
    private val logger = LoggerFactory.getLogger(GetChangedFilesTool::class.java)
    
    /**
     * Регистрация инструмента на сервере
     */
    fun register(server: Server) {
        server.addTool(
            name = "get_changed_files",
            description = "Получить список изменённых файлов между двумя ветками git. Возвращает список файлов с их статусами (A - добавлен, M - изменён, D - удалён, R - переименован, C - скопирован). Параметры: base - базовая ветка, head - целевая ветка.",
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
            logger.debug("Вызов инструмента get_changed_files с аргументами: ${request.arguments}")
            
            val params = GetChangedFilesHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

