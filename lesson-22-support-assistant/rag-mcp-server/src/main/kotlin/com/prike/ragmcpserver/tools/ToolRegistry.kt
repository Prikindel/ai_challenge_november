package com.prike.ragmcpserver.tools

import com.prike.ragmcpserver.config.RagMCPConfig
import com.prike.ragmcpserver.data.repository.KnowledgeBaseRepository
import com.prike.ragmcpserver.domain.service.DocumentIndexer
import com.prike.ragmcpserver.tools.handlers.RagServiceProvider
import com.prike.ragmcpserver.tools.handlers.RagSearchHandler
import com.prike.ragmcpserver.tools.handlers.RagSearchProjectDocsHandler
import com.prike.ragmcpserver.tools.handlers.RagIndexDocumentsHandler
import com.prike.ragmcpserver.tools.handlers.RagIndexProjectDocsHandler
import com.prike.ragmcpserver.tools.handlers.RagIndexSupportDocsHandler
import com.prike.ragmcpserver.tools.handlers.RagGetStatisticsHandler
import com.prike.ragmcpserver.tools.handlers.RagGetDocumentsHandler
import com.prike.mcpcommon.server.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Реестр инструментов RAG MCP сервера
 */
class ToolRegistry(
    private val ragServiceProvider: RagServiceProvider,
    private val documentIndexer: DocumentIndexer,
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val lessonRoot: File,
    private val config: RagMCPConfig
) : com.prike.mcpcommon.server.ToolRegistry {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    
    // Инструменты поиска
    private val ragSearchHandler = RagSearchHandler(ragServiceProvider)
    private val ragSearchTool = RagSearchTool(ragSearchHandler)
    
    private val ragSearchProjectDocsHandler = RagSearchProjectDocsHandler(ragServiceProvider)
    private val ragSearchProjectDocsTool = RagSearchProjectDocsTool(ragSearchProjectDocsHandler)
    
    // Инструменты индексации
    private val ragIndexDocumentsHandler = RagIndexDocumentsHandler(documentIndexer, lessonRoot, config)
    private val ragIndexDocumentsTool = RagIndexDocumentsTool(ragIndexDocumentsHandler)
    
    private val ragIndexProjectDocsHandler = RagIndexProjectDocsHandler(documentIndexer, lessonRoot, config)
    private val ragIndexProjectDocsTool = RagIndexProjectDocsTool(ragIndexProjectDocsHandler)
    
    private val ragIndexSupportDocsHandler = RagIndexSupportDocsHandler(documentIndexer, lessonRoot, config)
    private val ragIndexSupportDocsTool = RagIndexSupportDocsTool(ragIndexSupportDocsHandler)
    
    // Инструменты для получения информации
    private val ragGetStatisticsHandler = RagGetStatisticsHandler(knowledgeBaseRepository)
    private val ragGetStatisticsTool = RagGetStatisticsTool(ragGetStatisticsHandler)
    
    private val ragGetDocumentsHandler = RagGetDocumentsHandler(knowledgeBaseRepository)
    private val ragGetDocumentsTool = RagGetDocumentsTool(ragGetDocumentsHandler)
    
    override fun registerTools(server: Server) {
        logger.info("Регистрация инструментов RAG MCP сервера")
        
        // Инструменты поиска
        ragSearchTool.register(server)
        ragSearchProjectDocsTool.register(server)
        
        // Инструменты индексации
        ragIndexDocumentsTool.register(server)
        ragIndexProjectDocsTool.register(server)
        ragIndexSupportDocsTool.register(server)
        
        // Инструменты для получения информации
        ragGetStatisticsTool.register(server)
        ragGetDocumentsTool.register(server)
        
        logger.info("Все инструменты RAG MCP сервера зарегистрированы (7 инструментов)")
    }
}
