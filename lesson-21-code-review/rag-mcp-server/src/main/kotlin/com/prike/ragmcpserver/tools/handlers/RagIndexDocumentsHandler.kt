package com.prike.ragmcpserver.tools.handlers

import com.prike.ragmcpserver.config.RagMCPConfig
import com.prike.ragmcpserver.domain.service.DocumentIndexer
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Параметры для индексации документов
 */
data class RagIndexDocumentsParams(
    val documentsPath: String? = null  // Если null, используется из конфигурации
)

/**
 * Результат индексации
 */
data class RagIndexDocumentsResult(
    val indexedCount: Int,
    val totalChunks: Int,
    val message: String
)

/**
 * Обработчик для инструмента rag_index_documents
 */
class RagIndexDocumentsHandler(
    private val documentIndexer: DocumentIndexer,
    private val lessonRoot: File,
    private val config: RagMCPConfig
) : ToolHandler<RagIndexDocumentsParams, RagIndexDocumentsResult>() {
    
    override val logger = LoggerFactory.getLogger(RagIndexDocumentsHandler::class.java)
    
    override suspend fun execute(params: RagIndexDocumentsParams): RagIndexDocumentsResult {
        val documentsPath = params.documentsPath ?: config.indexing.documentsPath
        val fullPath = File(lessonRoot, documentsPath)
        
        logger.info("RAG index documents: path='$fullPath'")
        
        return try {
            val results = documentIndexer.indexDirectory(fullPath.absolutePath)
            val successCount = results.count { it.success }
            val totalChunks = results.sumOf { it.chunksCount }
            
            RagIndexDocumentsResult(
                indexedCount = successCount,
                totalChunks = totalChunks,
                message = "Успешно проиндексировано $successCount документов, создано $totalChunks чанков"
            )
        } catch (e: Exception) {
            logger.error("Failed to index documents: ${e.message}", e)
            RagIndexDocumentsResult(
                indexedCount = 0,
                totalChunks = 0,
                message = "Ошибка индексации: ${e.message}"
            )
        }
    }
    
    override fun prepareResult(request: RagIndexDocumentsParams, result: RagIndexDocumentsResult): TextContent {
        return TextContent(text = result.message)
    }
    
    companion object {
        fun parseParams(arguments: JsonObject): RagIndexDocumentsParams {
            val documentsPath = arguments["documentsPath"]?.jsonPrimitive?.content
            return RagIndexDocumentsParams(documentsPath = documentsPath)
        }
    }
}

