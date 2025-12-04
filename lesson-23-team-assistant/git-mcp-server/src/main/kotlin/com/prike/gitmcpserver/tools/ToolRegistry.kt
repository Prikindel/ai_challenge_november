package com.prike.gitmcpserver.tools

import com.prike.gitmcpserver.tools.handlers.GetCurrentBranchHandler
import com.prike.gitmcpserver.tools.handlers.ReadFileHandler
import com.prike.gitmcpserver.tools.handlers.ListDirectoryHandler
import com.prike.gitmcpserver.tools.handlers.GetDiffHandler
import com.prike.gitmcpserver.tools.handlers.GetChangedFilesHandler
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
    private val getFileContentTool = GetFileContentTool(readFileHandler)
    
    private val listDirectoryHandler = ListDirectoryHandler(projectRoot)
    private val listDirectoryTool = ListDirectoryTool(listDirectoryHandler)
    
    private val getDiffHandler = GetDiffHandler()
    private val getDiffTool = GetDiffTool(getDiffHandler)
    
    private val getChangedFilesHandler = GetChangedFilesHandler()
    private val getChangedFilesTool = GetChangedFilesTool(getChangedFilesHandler)
    
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
        
        // Регистрация инструмента get_diff (для ревью кода)
        getDiffTool.register(server)
        
        // Регистрация инструмента get_changed_files (для ревью кода)
        getChangedFilesTool.register(server)
        
        // Регистрация инструмента get_file_content (для ревью кода)
        getFileContentTool.register(server)
        
        logger.info("Все инструменты зарегистрированы")
    }
}

