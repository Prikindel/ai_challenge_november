package com.prike.crmmcpserver.tools

import com.prike.crmmcpserver.storage.InMemoryCRMStorage
import com.prike.crmmcpserver.tools.handlers.*
import com.prike.mcpcommon.server.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.slf4j.LoggerFactory

/**
 * Реестр инструментов CRM MCP сервера
 * Регистрирует все доступные инструменты
 */
class ToolRegistry(
    private val storage: InMemoryCRMStorage
) : com.prike.mcpcommon.server.ToolRegistry {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    
    // Handlers
    private val getUserHandler = GetUserHandler(storage)
    private val getTicketHandler = GetTicketHandler(storage)
    private val getUserTicketsHandler = GetUserTicketsHandler(storage)
    private val createTicketHandler = CreateTicketHandler(storage)
    private val addTicketMessageHandler = AddTicketMessageHandler(storage)
    
    // Tools
    private val getUserTool = GetUserTool(getUserHandler)
    private val getTicketTool = GetTicketTool(getTicketHandler)
    private val getUserTicketsTool = GetUserTicketsTool(getUserTicketsHandler)
    private val createTicketTool = CreateTicketTool(createTicketHandler)
    private val addTicketMessageTool = AddTicketMessageTool(addTicketMessageHandler)
    
    /**
     * Регистрация всех инструментов на сервере
     */
    override fun registerTools(server: Server) {
        logger.info("Регистрация инструментов CRM MCP сервера")
        
        // Регистрация инструмента get_user
        getUserTool.register(server)
        
        // Регистрация инструмента get_ticket
        getTicketTool.register(server)
        
        // Регистрация инструмента get_user_tickets
        getUserTicketsTool.register(server)
        
        // Регистрация инструмента create_ticket
        createTicketTool.register(server)
        
        // Регистрация инструмента add_ticket_message
        addTicketMessageTool.register(server)
        
        logger.info("Все инструменты зарегистрированы")
    }
}

