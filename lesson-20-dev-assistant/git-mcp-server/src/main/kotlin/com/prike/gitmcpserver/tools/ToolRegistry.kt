package com.prike.gitmcpserver.tools

import com.prike.gitmcpserver.tools.handlers.GetCurrentBranchHandler
import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.slf4j.LoggerFactory

/**
 * Реестр инструментов MCP сервера
 * Регистрирует все доступные инструменты
 */
class ToolRegistry {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    
    private val getCurrentBranchHandler = GetCurrentBranchHandler()
    private val getCurrentBranchTool = GetCurrentBranchTool(getCurrentBranchHandler)
    
    /**
     * Регистрация всех инструментов на сервере
     */
    fun registerTools(server: Server) {
        logger.info("Регистрация инструментов MCP сервера")
        
        // Регистрация инструмента get_current_branch
        getCurrentBranchTool.register(server)
        
        logger.info("Все инструменты зарегистрированы")
    }
}

