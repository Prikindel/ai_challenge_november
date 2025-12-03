package com.prike.ragmcpserver.tools.handlers

import com.prike.ragmcpserver.config.RagMCPConfig
import com.prike.ragmcpserver.domain.service.DocumentIndexer
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Параметры для индексации документации поддержки
 */
data class RagIndexSupportDocsParams(
    val supportDocsPath: String? = null  // Если null, используется из конфигурации
)

/**
 * Результат индексации документации поддержки
 */
data class RagIndexSupportDocsResult(
    val indexedCount: Int,
    val totalChunks: Int,
    val message: String
)

/**
 * Обработчик для инструмента rag_index_support_docs
 */
class RagIndexSupportDocsHandler(
    private val documentIndexer: DocumentIndexer,
    private val lessonRoot: File,
    private val config: RagMCPConfig
) : ToolHandler<RagIndexSupportDocsParams, RagIndexSupportDocsResult>() {
    
    override val logger = LoggerFactory.getLogger(RagIndexSupportDocsHandler::class.java)
    
    override suspend fun execute(params: RagIndexSupportDocsParams): RagIndexSupportDocsResult {
        val supportDocsPath = params.supportDocsPath ?: config.indexing.supportDocsPath
        
        logger.info("RAG index support docs: supportDocsPath='$supportDocsPath'")
        
        var totalIndexed = 0
        var totalChunks = 0
        val messages = mutableListOf<String>()
        
        return try {
            // Индексируем папку project/docs/support
            if (supportDocsPath != null) {
                val supportDir = File(lessonRoot, supportDocsPath)
                if (supportDir.exists() && supportDir.isDirectory) {
                    val results = documentIndexer.indexDirectory(supportDir.absolutePath)
                    val successCount = results.count { it.success }
                    val chunksCount = results.sumOf { it.chunksCount }
                    totalIndexed += successCount
                    totalChunks += chunksCount
                    messages.add("Папка $supportDocsPath: $successCount документов, $chunksCount чанков")
                } else {
                    messages.add("Папка $supportDocsPath не найдена")
                }
            } else {
                messages.add("Путь к документации поддержки не указан в конфигурации")
            }
            
            RagIndexSupportDocsResult(
                indexedCount = totalIndexed,
                totalChunks = totalChunks,
                message = messages.joinToString("\n")
            )
        } catch (e: Exception) {
            logger.error("Failed to index support docs: ${e.message}", e)
            RagIndexSupportDocsResult(
                indexedCount = 0,
                totalChunks = 0,
                message = "Ошибка индексации: ${e.message}"
            )
        }
    }
    
    override fun prepareResult(request: RagIndexSupportDocsParams, result: RagIndexSupportDocsResult): TextContent {
        return TextContent(text = result.message)
    }
    
    companion object {
        fun parseParams(arguments: JsonObject): RagIndexSupportDocsParams {
            val supportDocsPath = arguments["supportDocsPath"]?.jsonPrimitive?.content
            return RagIndexSupportDocsParams(
                supportDocsPath = supportDocsPath
            )
        }
    }
}

