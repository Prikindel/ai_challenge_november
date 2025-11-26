package com.prike.domain.service

import com.prike.domain.model.RAGRequest
import com.prike.domain.model.RAGResponse
import com.prike.domain.model.RetrievedChunk
import org.slf4j.LoggerFactory

/**
 * Сервис для RAG-запросов
 * Объединяет поиск по базе знаний с генерацией ответа через LLM
 */
class RAGService(
    private val searchService: KnowledgeBaseSearchService,
    private val llmService: LLMService,
    private val promptBuilder: PromptBuilder
) {
    private val logger = LoggerFactory.getLogger(RAGService::class.java)
    
    /**
     * Выполняет RAG-запрос: поиск чанков + генерация ответа с контекстом
     * 
     * @param request запрос для RAG
     * @return ответ с контекстом и использованными чанками
     */
    suspend fun query(request: RAGRequest): RAGResponse {
        if (request.question.isBlank()) {
            throw IllegalArgumentException("Question cannot be blank")
        }
        
        logger.info("RAG query: ${request.question} (topK=${request.topK}, minSimilarity=${request.minSimilarity})")
        
        // 1. Поиск релевантных чанков в базе знаний
        val searchResults = searchService.searchWithThreshold(
            query = request.question,
            limit = request.topK,
            minSimilarity = request.minSimilarity
        )
        
        if (searchResults.isEmpty()) {
            logger.warn("No relevant chunks found for question: ${request.question}")
            // Возвращаем ответ без контекста, если чанки не найдены
            return generateResponseWithoutContext(request.question)
        }
        
        logger.debug("Found ${searchResults.size} relevant chunks")
        
        // 2. Преобразуем SearchResult в RetrievedChunk
        val retrievedChunks = searchResults.map { result ->
            RetrievedChunk(
                chunkId = result.chunkId,
                documentId = result.documentId,
                documentPath = result.documentFilePath,
                documentTitle = result.documentTitle,
                content = result.content,
                similarity = result.similarity,
                chunkIndex = result.chunkIndex
            )
        }
        
        // 3. Формируем промпт с контекстом
        val promptResult = promptBuilder.buildPromptWithContext(
            question = request.question,
            chunks = retrievedChunks
        )
        
        logger.debug("Built prompt with ${retrievedChunks.size} chunks (systemPrompt length: ${promptResult.systemPrompt.length}, userMessage length: ${promptResult.userMessage.length})")
        
        // 4. Генерируем ответ через LLM с контекстом
        val llmResponse = llmService.generateAnswer(
            question = promptResult.userMessage,
            systemPrompt = promptResult.systemPrompt
        )
        
        logger.info("RAG query completed: answer length=${llmResponse.answer.length}, tokens=${llmResponse.tokensUsed}")
        
        return RAGResponse(
            question = request.question,
            answer = llmResponse.answer,
            contextChunks = retrievedChunks,
            tokensUsed = llmResponse.tokensUsed
        )
    }
    
    /**
     * Генерирует ответ без контекста (если чанки не найдены)
     */
    private suspend fun generateResponseWithoutContext(question: String): RAGResponse {
        logger.debug("Generating response without context")
        
        val promptResult = promptBuilder.buildPromptWithoutContext(question)
        val llmResponse = llmService.generateAnswer(
            question = promptResult.userMessage,
            systemPrompt = promptResult.systemPrompt
        )
        
        return RAGResponse(
            question = question,
            answer = llmResponse.answer,
            contextChunks = emptyList(),
            tokensUsed = llmResponse.tokensUsed
        )
    }
}

