package com.prike.gitmcpserver.tools

import com.prike.gitmcpserver.tools.handlers.GetCurrentBranchHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Регистрация инструмента get_current_branch для MCP сервера
 * Делегирует обработку запросов GetCurrentBranchHandler
 */
class GetCurrentBranchTool(
    private val handler: GetCurrentBranchHandler
) {
    private val logger = LoggerFactory.getLogger(GetCurrentBranchTool::class.java)
    
    /**
     * Регистрация инструмента на сервере
     */
    fun register(server: Server) {
        server.addTool(
            name = "get_current_branch",
            description = "Получить текущую ветку git репозитория. Возвращает имя текущей ветки или 'unknown' если git не установлен или не в git-репозитории.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    // Нет параметров для этого инструмента
                },
                required = emptyList()
            )
        ) { request ->
            logger.debug("Вызов инструмента get_current_branch с аргументами: ${request.arguments}")
            
            val params = GetCurrentBranchHandler.parseParams(request.arguments)
            handler.handle(params)
        }
    }
}

