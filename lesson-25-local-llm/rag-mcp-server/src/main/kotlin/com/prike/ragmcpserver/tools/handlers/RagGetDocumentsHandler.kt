package com.prike.ragmcpserver.tools.handlers

import com.prike.ragmcpserver.data.repository.KnowledgeBaseRepository
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для получения списка документов (пустые, так как не требуются)
 */
object RagGetDocumentsParams

/**
 * Результат получения списка документов
 */
data class RagGetDocumentsResult(
    val documents: List<DocumentInfo>
)

data class DocumentInfo(
    val id: String,
    val filePath: String,
    val title: String?,
    val indexedAt: Long,
    val chunkCount: Int
)

/**
 * Обработчик для инструмента rag_get_documents
 */
class RagGetDocumentsHandler(
    private val knowledgeBaseRepository: KnowledgeBaseRepository
) {
    private val logger = LoggerFactory.getLogger(RagGetDocumentsHandler::class.java)
    
    suspend fun handle(params: RagGetDocumentsParams): CallToolResult {
        logger.info("RAG get documents list")
        
        val documents = knowledgeBaseRepository.getAllDocuments()
        
        val documentInfos = documents.map { doc ->
            DocumentInfo(
                id = doc.id,
                filePath = doc.filePath,
                title = doc.title,
                indexedAt = doc.indexedAt,
                chunkCount = doc.chunkCount
            )
        }
        
        val result = RagGetDocumentsResult(documents = documentInfos)
        
        val json = buildJsonObject {
            putJsonArray("documents") {
                result.documents.forEach { doc ->
                    addJsonObject {
                        put("id", doc.id)
                        put("filePath", doc.filePath)
                        put("title", doc.title ?: "")
                        put("indexedAt", doc.indexedAt)
                        put("chunkCount", doc.chunkCount)
                    }
                }
            }
        }
        
        return CallToolResult(
            content = listOf(TextContent(text = json.toString()))
        )
    }
    
    companion object {
        fun parseParams(arguments: JsonObject): RagGetDocumentsParams {
            // Параметры не требуются
            return RagGetDocumentsParams
        }
    }
}

