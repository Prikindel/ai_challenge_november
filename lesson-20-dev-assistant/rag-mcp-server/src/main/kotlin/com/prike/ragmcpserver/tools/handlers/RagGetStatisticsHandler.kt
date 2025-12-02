package com.prike.ragmcpserver.tools.handlers

import com.prike.ragmcpserver.data.repository.KnowledgeBaseRepository
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для получения статистики (пустые, так как не требуются)
 */
object RagGetStatisticsParams

/**
 * Результат получения статистики
 */
data class RagGetStatisticsResult(
    val documentsCount: Int,
    val chunksCount: Int
)

/**
 * Обработчик для инструмента rag_get_statistics
 */
class RagGetStatisticsHandler(
    private val knowledgeBaseRepository: KnowledgeBaseRepository
) {
    private val logger = LoggerFactory.getLogger(RagGetStatisticsHandler::class.java)
    
    suspend fun handle(params: RagGetStatisticsParams): CallToolResult {
        logger.info("RAG get statistics")
        
        val statistics = knowledgeBaseRepository.getStatistics()
        
        val result = RagGetStatisticsResult(
            documentsCount = statistics.documentsCount,
            chunksCount = statistics.chunksCount
        )
        
        val json = buildJsonObject {
            put("documentsCount", result.documentsCount)
            put("chunksCount", result.chunksCount)
        }
        
        return CallToolResult(
            content = listOf(TextContent(text = json.toString()))
        )
    }
    
    companion object {
        fun parseParams(arguments: JsonObject): RagGetStatisticsParams {
            // Параметры не требуются
            return RagGetStatisticsParams
        }
    }
}

