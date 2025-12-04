package com.prike.gitmcpserver.tools

import com.prike.gitmcpserver.tools.handlers.ReadFileHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Регистрация инструмента get_file_content для MCP сервера
 * Алиас для read_file, но с более явным названием для ревью кода
 * Делегирует обработку запросов ReadFileHandler
 */
class GetFileContentTool(
    private val handler: ReadFileHandler
) {
    private val logger = LoggerFactory.getLogger(GetFileContentTool::class.java)
    
    /**
     * Регистрация инструмента на сервере
     */
    fun register(server: Server) {
        server.addTool(
            name = "get_file_content",
            description = "Получить содержимое файла проекта. Используется для получения полного содержимого файла при ревью кода. Путь указывается относительно корня проекта. Максимальный размер файла: 100KB.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("filePath", buildJsonObject {
                        put("type", "string")
                        put("description", "Путь к файлу относительно корня проекта (например, 'server/src/main/kotlin/com/prike/Main.kt')")
                    })
                },
                required = listOf("filePath")
            )
        ) { request ->
            logger.debug("Вызов инструмента get_file_content с аргументами: ${request.arguments}")
            
            // Преобразуем filePath в path для ReadFileHandler
            val filePath = request.arguments["filePath"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Parameter 'filePath' is required")
            
            val params = com.prike.gitmcpserver.tools.handlers.ReadFileParams(path = filePath)
            handler.handle(params)
        }
    }
}

