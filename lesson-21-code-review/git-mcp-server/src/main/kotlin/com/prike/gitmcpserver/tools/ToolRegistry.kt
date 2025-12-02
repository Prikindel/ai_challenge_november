package com.prike.gitmcpserver.tools

import com.prike.gitmcpserver.tools.handlers.GetCurrentBranchHandler
import com.prike.gitmcpserver.tools.handlers.ReadFileHandler
import com.prike.gitmcpserver.tools.handlers.ListDirectoryHandler
import com.prike.mcpcommon.server.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Реестр инструментов Git MCP сервера
 * Регистрирует все доступные инструменты
 */
class ToolRegistry(
    private val projectRoot: File
) : com.prike.mcpcommon.server.ToolRegistry {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    
    private val getCurrentBranchHandler = GetCurrentBranchHandler()
    private val getCurrentBranchTool = GetCurrentBranchTool(getCurrentBranchHandler)
    
    private val readFileHandler = ReadFileHandler(projectRoot)
    private val readFileTool = ReadFileTool(readFileHandler)
    
    private val listDirectoryHandler = ListDirectoryHandler(projectRoot)
    private val listDirectoryTool = ListDirectoryTool(listDirectoryHandler)
    
    /**
     * Регистрация всех инструментов на сервере
     */
    override fun registerTools(server: Server) {
        logger.info("Регистрация инструментов MCP сервера")
        
        // Регистрация инструмента get_current_branch
        getCurrentBranchTool.register(server)
        
        // Регистрация инструмента read_file
        readFileTool.register(server)
        
        // Регистрация инструмента list_directory
        listDirectoryTool.register(server)
        
        logger.info("Все инструменты зарегистрированы")
    }
}

