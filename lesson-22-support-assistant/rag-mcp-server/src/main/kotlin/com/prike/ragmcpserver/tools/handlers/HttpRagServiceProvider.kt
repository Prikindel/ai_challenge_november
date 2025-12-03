package com.prike.ragmcpserver.tools.handlers

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Реализация RagServiceProvider через HTTP API
 */
class HttpRagServiceProvider(
    private val apiBaseUrl: String = "http://localhost:8080"
) : RagServiceProvider {
    private val logger = LoggerFactory.getLogger(HttpRagServiceProvider::class.java)
    
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
    }
    
    override suspend fun search(
        query: String,
        limit: Int,
        minSimilarity: Float
    ): List<SearchResult> {
        return try {
            val response = httpClient.post("$apiBaseUrl/api/rag-mcp/search") {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(query = query, topK = limit, minSimilarity = minSimilarity))
            }.body<SearchResponse>()
            
            response.chunks.map { chunk ->
                SearchResult(
                    chunkId = chunk.chunkId,
                    documentId = chunk.documentId,
                    documentFilePath = chunk.documentPath,
                    documentTitle = chunk.documentTitle,
                    content = chunk.content,
                    similarity = chunk.similarity
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to search via HTTP API: ${e.message}", e)
            emptyList()
        }
    }
    
    override suspend fun searchProjectDocs(
        query: String,
        limit: Int,
        minSimilarity: Float
    ): List<SearchResult> {
        return try {
            val response = httpClient.post("$apiBaseUrl/api/rag-mcp/search-project-docs") {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(query = query, topK = limit, minSimilarity = minSimilarity))
            }.body<SearchResponse>()
            
            response.chunks.map { chunk ->
                SearchResult(
                    chunkId = chunk.chunkId,
                    documentId = chunk.documentId,
                    documentFilePath = chunk.documentPath,
                    documentTitle = chunk.documentTitle,
                    content = chunk.content,
                    similarity = chunk.similarity
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to search project docs via HTTP API: ${e.message}", e)
            emptyList()
        }
    }
    
    @Serializable
    private data class SearchRequest(
        val query: String,
        val topK: Int,
        val minSimilarity: Float
    )
    
    @Serializable
    private data class SearchResponse(
        val chunks: List<ChunkResponse>,
        val totalFound: Int
    )
    
    @Serializable
    private data class ChunkResponse(
        val chunkId: String,
        val documentId: String,
        val documentPath: String?,
        val documentTitle: String?,
        val content: String,
        val similarity: Float
    )
}
