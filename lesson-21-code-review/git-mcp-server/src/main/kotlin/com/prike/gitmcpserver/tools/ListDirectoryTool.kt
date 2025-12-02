package com.prike.gitmcpserver.tools

import com.prike.gitmcpserver.tools.handlers.ListDirectoryHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Регистрация инструмента list_directory для MCP сервера
 * Делегирует обработку запросов ListDirectoryHandler
 */
class ListDirectoryTool(
    private val handler: ListDirectoryHandler
) {
    private val logger = LoggerFactory.getLogger(ListDirectoryTool::class.java)
    
    /**
     * Регистрация инструмента на сервере
     */
    fun register(server: Server) {
        server.addTool(
            name = "list_directory",
            description = "Возвращает список файлов и директорий в указанной директории проекта. Путь указывается относительно корня проекта.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("path", buildJsonObject {
                        put("type", "string")
                        put("description", "Путь к директории относительно корня проекта (по умолчанию: '.')")
                        put("default", ".")
                    })
                },
                required = emptyList()
            )
        ) { request ->
            logger.debug("Вызов инструмента list_directory с аргументами: ${request.arguments}")
            
            val params = ListDirectoryHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

