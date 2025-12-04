package com.prike.taskmcpserver.tools

import com.prike.taskmcpserver.storage.InMemoryTaskStorage
import com.prike.taskmcpserver.tools.handlers.*
import com.prike.mcpcommon.server.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.slf4j.LoggerFactory

/**
 * Реестр инструментов Task MCP сервера
 * Регистрирует все доступные инструменты
 */
class ToolRegistry(
    private val storage: InMemoryTaskStorage
) : com.prike.mcpcommon.server.ToolRegistry {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    
    // Handlers
    private val getTasksHandler = GetTasksHandler(storage)
    private val getTaskHandler = GetTaskHandler(storage)
    private val createTaskHandler = CreateTaskHandler(storage)
    private val updateTaskHandler = UpdateTaskHandler(storage)
    private val getProjectStatusHandler = GetProjectStatusHandler(storage)
    private val getTasksByPriorityHandler = GetTasksByPriorityHandler(storage)
    
    // Tools
    private val getTasksTool = GetTasksTool(getTasksHandler)
    private val getTaskTool = GetTaskTool(getTaskHandler)
    private val createTaskTool = CreateTaskTool(createTaskHandler)
    private val updateTaskTool = UpdateTaskTool(updateTaskHandler)
    private val getProjectStatusTool = GetProjectStatusTool(getProjectStatusHandler)
    private val getTasksByPriorityTool = GetTasksByPriorityTool(getTasksByPriorityHandler)
    
    /**
     * Регистрация всех инструментов на сервере
     */
    override fun registerTools(server: Server) {
        logger.info("Регистрация инструментов Task MCP сервера")
        
        // Регистрация инструмента get_tasks
        getTasksTool.register(server)
        
        // Регистрация инструмента get_task
        getTaskTool.register(server)
        
        // Регистрация инструмента create_task
        createTaskTool.register(server)
        
        // Регистрация инструмента update_task
        updateTaskTool.register(server)
        
        // Регистрация инструмента get_project_status
        getProjectStatusTool.register(server)
        
        // Регистрация инструмента get_tasks_by_priority
        getTasksByPriorityTool.register(server)
        
        logger.info("Все инструменты зарегистрированы")
    }
}

