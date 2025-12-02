package com.prike.ragmcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Параметры для поиска по документации проекта
 */
data class RagSearchProjectDocsParams(
    val query: String,
    val topK: Int = 10,
    val minSimilarity: Float = 0.0f
)

/**
 * Обработчик для инструмента rag_search_project_docs
 */
class RagSearchProjectDocsHandler(
    private val ragServiceProvider: RagServiceProvider
) : ToolHandler<RagSearchProjectDocsParams, RagSearchResult>() {
    
    override val logger = LoggerFactory.getLogger(RagSearchProjectDocsHandler::class.java)
    
    override suspend fun execute(params: RagSearchProjectDocsParams): RagSearchResult {
        logger.info("RAG search (project docs): query='${params.query}', topK=${params.topK}, minSimilarity=${params.minSimilarity}")
        
        val searchResults = ragServiceProvider.searchProjectDocs(
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
    
    override fun prepareResult(request: RagSearchProjectDocsParams, result: RagSearchResult): TextContent {
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
        fun parseParams(arguments: JsonObject): RagSearchProjectDocsParams {
            val query = arguments["query"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Parameter 'query' is required")
            
            val topK = arguments["topK"]?.jsonPrimitive?.intOrNull ?: 10
            val minSimilarity = arguments["minSimilarity"]?.jsonPrimitive?.floatOrNull ?: 0.0f
            
            return RagSearchProjectDocsParams(
                query = query,
                topK = topK,
                minSimilarity = minSimilarity
            )
        }
    }
}

