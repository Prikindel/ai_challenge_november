package com.prike.domain.service

import com.prike.domain.model.ComparisonResult
import com.prike.domain.model.RAGRequest
import com.prike.domain.model.RAGResponse
import com.prike.domain.model.StandardResponse
import org.slf4j.LoggerFactory

/**
 * Сервис для сравнения ответов с RAG и без RAG
 */
class ComparisonService(
    private val ragService: RAGService,
    private val llmService: LLMService
) {
    private val logger = LoggerFactory.getLogger(ComparisonService::class.java)
    
    /**
     * Сравнивает ответы с RAG и без RAG
     * 
     * @param request запрос для RAG
     * @return результат сравнения обоих режимов
     */
    suspend fun compare(request: RAGRequest): ComparisonResult {
        logger.info("Comparing RAG vs Standard for question: ${request.question}")
        
        // 1. Генерируем RAG-ответ (с контекстом)
        logger.debug("Generating RAG response...")
        val ragResponse = ragService.query(request)
        
        // 2. Генерируем обычный ответ (без контекста)
        logger.debug("Generating standard response...")
        val standardResponse = llmService.generateAnswer(
            question = request.question,
            systemPrompt = "Ты — помощник, который отвечает на вопросы."
        )
        
        val standard = StandardResponse(
            question = request.question,
            answer = standardResponse.answer,
            tokensUsed = standardResponse.tokensUsed
        )
        
        logger.info("Comparison completed: RAG tokens=${ragResponse.tokensUsed}, Standard tokens=${standard.tokensUsed}")
        
        return ComparisonResult(
            question = request.question,
            ragResponse = ragResponse,
            standardResponse = standard
        )
    }
}

