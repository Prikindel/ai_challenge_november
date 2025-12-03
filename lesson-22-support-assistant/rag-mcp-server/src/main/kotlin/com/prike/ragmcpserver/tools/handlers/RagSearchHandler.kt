package com.prike.ragmcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для поиска по RAG
 */
data class RagSearchParams(
    val query: String,
    val topK: Int = 5,
    val minSimilarity: Float = 0.4f
)

/**
 * Результат поиска по RAG
 */
data class RagSearchResult(
    val chunks: List<ChunkResult>,
    val totalFound: Int
)

data class ChunkResult(
    val chunkId: String,
    val documentPath: String?,
    val documentTitle: String?,
    val content: String,
    val similarity: Float
)

/**
 * Обработчик для инструмента rag_search
 * Использует RagServiceProvider для выполнения поиска
 */
class RagSearchHandler(
    private val ragServiceProvider: RagServiceProvider
) : ToolHandler<RagSearchParams, RagSearchResult>() {
    
    override val logger = LoggerFactory.getLogger(RagSearchHandler::class.java)
    
    override suspend fun execute(params: RagSearchParams): RagSearchResult {
        logger.info("RAG search: query='${params.query}', topK=${params.topK}, minSimilarity=${params.minSimilarity}")
        
        // Вызываем поиск через провайдер
        val searchResults = ragServiceProvider.search(
            query = params.query,
            limit = params.topK,
            minSimilarity = params.minSimilarity
        )
        
        val chunks = searchResults.map { result ->
            ChunkResult(
                chunkId = result.chunkId,
                documentPath = result.documentFilePath,
                documentTitle = result.documentTitle,
                content = result.content,
                similarity = result.similarity
            )
        }
        
        return RagSearchResult(
            chunks = chunks,
            totalFound = chunks.size
        )
    }
    
    override fun prepareResult(request: RagSearchParams, result: RagSearchResult): TextContent {
        // Возвращаем JSON для удобного парсинга на стороне клиента
        val json = buildJsonObject {
            put("totalFound", result.totalFound)
            putJsonArray("chunks") {
                result.chunks.forEach { chunk ->
                    addJsonObject {
                        put("chunkId", chunk.chunkId)
                        put("documentPath", chunk.documentPath ?: "")
                        put("documentTitle", chunk.documentTitle ?: "")
                        put("content", chunk.content)
                        put("similarity", chunk.similarity)
                    }
                }
            }
        }
        return TextContent(text = json.toString())
    }
    
    companion object {
        /**
         * Парсинг параметров из JSON
         */
        fun parseParams(arguments: JsonObject): RagSearchParams {
            val query = arguments["query"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Parameter 'query' is required")
            
            val topK = arguments["topK"]?.jsonPrimitive?.intOrNull ?: 5
            val minSimilarity = arguments["minSimilarity"]?.jsonPrimitive?.floatOrNull ?: 0.4f
            
            return RagSearchParams(
                query = query,
                topK = topK,
                minSimilarity = minSimilarity
            )
        }
    }
}

/**
 * Интерфейс для доступа к RAG сервисам
 * Реализуется в основном сервере
 */
interface RagServiceProvider {
    suspend fun search(
        query: String,
        limit: Int,
        minSimilarity: Float
    ): List<SearchResult>
    
    suspend fun searchProjectDocs(
        query: String,
        limit: Int,
        minSimilarity: Float
    ): List<SearchResult>
}

/**
 * Результат поиска (упрощенная версия)
 */
data class SearchResult(
    val chunkId: String,
    val documentId: String,
    val documentFilePath: String?,
    val documentTitle: String?,
    val content: String,
    val similarity: Float
)

