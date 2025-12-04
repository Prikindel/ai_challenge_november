package com.prike.gitmcpserver.tools

import com.prike.gitmcpserver.tools.handlers.ReadFileHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Регистрация инструмента read_file для MCP сервера
 * Делегирует обработку запросов ReadFileHandler
 */
class ReadFileTool(
    private val handler: ReadFileHandler
) {
    private val logger = LoggerFactory.getLogger(ReadFileTool::class.java)
    
    /**
     * Регистрация инструмента на сервере
     */
    fun register(server: Server) {
        server.addTool(
            name = "read_file",
            description = "Читает содержимое файла проекта. Путь указывается относительно корня проекта. Максимальный размер файла: 100KB.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("path", buildJsonObject {
                        put("type", "string")
                        put("description", "Путь к файлу относительно корня проекта (например, 'project/docs/api.md')")
                    })
                },
                required = listOf("path")
            )
        ) { request ->
            logger.debug("Вызов инструмента read_file с аргументами: ${request.arguments}")
            
            val params = ReadFileHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

