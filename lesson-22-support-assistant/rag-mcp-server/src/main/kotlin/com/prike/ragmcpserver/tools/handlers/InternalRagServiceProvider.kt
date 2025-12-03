package com.prike.ragmcpserver.tools.handlers

import com.prike.ragmcpserver.domain.service.KnowledgeBaseSearchService
import org.slf4j.LoggerFactory

/**
 * Внутренняя реализация RagServiceProvider
 * Использует компоненты RAG напрямую (без HTTP API)
 */
class InternalRagServiceProvider(
    private val searchService: KnowledgeBaseSearchService
) : RagServiceProvider {
    private val logger = LoggerFactory.getLogger(InternalRagServiceProvider::class.java)
    
    override suspend fun search(
        query: String,
        limit: Int,
        minSimilarity: Float
    ): List<SearchResult> {
        logger.debug("Internal RAG search: query='$query', limit=$limit, minSimilarity=$minSimilarity")
        
        val results = searchService.searchWithThreshold(
            query = query,
            limit = limit,
            minSimilarity = minSimilarity
        )
        
        return results.map { result ->
            SearchResult(
                chunkId = result.chunkId,
                documentId = result.documentId,
                documentFilePath = result.documentFilePath,
                documentTitle = result.documentTitle,
                content = result.content,
                similarity = result.similarity
            )
        }
    }
    
    override suspend fun searchProjectDocs(
        query: String,
        limit: Int,
        minSimilarity: Float
    ): List<SearchResult> {
        logger.debug("Internal RAG search project docs: query='$query', limit=$limit, minSimilarity=$minSimilarity")
        
        val results = searchService.searchProjectDocs(
            query = query,
            limit = limit,
            minSimilarity = minSimilarity
        )
        
        return results.map { result ->
            SearchResult(
                chunkId = result.chunkId,
                documentId = result.documentId,
                documentFilePath = result.documentFilePath,
                documentTitle = result.documentTitle,
                content = result.content,
                similarity = result.similarity
            )
        }
    }
}

