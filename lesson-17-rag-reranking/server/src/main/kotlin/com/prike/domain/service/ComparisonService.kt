package com.prike.domain.service

import com.prike.domain.model.ComparisonResult
import com.prike.domain.model.ComparisonMetrics
import com.prike.domain.model.RAGRequest
import com.prike.domain.model.RAGResponse
import com.prike.domain.model.StandardResponse
import org.slf4j.LoggerFactory

/**
 * Сервис для сравнения ответов с RAG и без RAG
 * Поддерживает сравнение разных режимов: STANDARD, RAG_NO_FILTER, RAG_WITH_FILTER
 */
class ComparisonService(
    private val ragService: RAGService,
    private val llmService: LLMService
) {
    private val logger = LoggerFactory.getLogger(ComparisonService::class.java)
    
    /**
     * Сравнивает ответы с RAG и без RAG (старый метод для обратной совместимости)
     * 
     * @param request запрос для RAG
     * @return результат сравнения обоих режимов
     */
    suspend fun compare(request: RAGRequest): ComparisonResult {
        logger.info("Comparing RAG vs Standard for question: ${request.question}")
        
        // 1. Генерируем RAG-ответ (с контекстом, с фильтром если настроен)
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
            filtered = ragResponse,
            standardResponse = standard
        )
    }
    
    /**
     * Сравнивает три режима: STANDARD, RAG_NO_FILTER, RAG_WITH_FILTER
     * 
     * @param request запрос для RAG
     * @return результат сравнения всех режимов с метриками
     */
    suspend fun compareWithFilter(request: RAGRequest): ComparisonResult {
        logger.info("Comparing all modes for question: ${request.question}")
        
        // 1. RAG без фильтра
        logger.debug("Generating RAG response without filter...")
        val ragNoFilter = ragService.query(request, applyFilter = false)
        
        // 2. RAG с фильтром (если настроен)
        logger.debug("Generating RAG response with filter...")
        val ragWithFilter = ragService.query(request, applyFilter = true)
        
        // 3. Обычный режим (без RAG)
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
        
        // Вычисляем метрики
        val metrics = ComparisonMetrics(
            baselineChunks = ragNoFilter.contextChunks.size,
            filteredChunks = ragWithFilter.contextChunks.size,
            avgSimilarityBefore = ragNoFilter.contextChunks.map { it.similarity }.average().toFloat().takeIf { ragNoFilter.contextChunks.isNotEmpty() },
            avgSimilarityAfter = ragWithFilter.contextChunks.map { it.similarity }.average().toFloat().takeIf { ragWithFilter.contextChunks.isNotEmpty() },
            tokensSaved = (ragNoFilter.tokensUsed ?: 0) - (ragWithFilter.tokensUsed ?: 0),
            filterApplied = ragWithFilter.filterStats != null || ragWithFilter.rerankInsights != null,
            strategy = null  // Можно добавить из конфигурации
        )
        
        logger.info("Comparison completed: NoFilter chunks=${metrics.baselineChunks}, WithFilter chunks=${metrics.filteredChunks}, Tokens saved=${metrics.tokensSaved}")
        
        return ComparisonResult(
            question = request.question,
            baseline = ragNoFilter,
            filtered = ragWithFilter,
            standardResponse = standard,
            metrics = metrics
        )
    }
    
    /**
     * Сравнивает два режима: baseline (без фильтра) и filtered (с фильтром)
     * 
     * @param request запрос для RAG
     * @param strategy стратегия фильтрации (если null, используется из конфигурации)
     * @return результат сравнения с метриками
     */
    suspend fun compareBaselineVsFiltered(request: RAGRequest, strategy: String? = null): ComparisonResult {
        logger.info("Comparing baseline vs filtered for question: ${request.question}, strategy: $strategy")
        
        // 1. Baseline: RAG без фильтра
        val baseline = ragService.query(request, applyFilter = false, strategy = "none")
        
        // 2. Filtered: RAG с фильтром (используем стратегию из запроса или конфигурации)
        val filtered = ragService.query(request, applyFilter = true, strategy = strategy)
        
        // Вычисляем метрики
        val metrics = ComparisonMetrics(
            baselineChunks = baseline.contextChunks.size,
            filteredChunks = filtered.contextChunks.size,
            avgSimilarityBefore = baseline.contextChunks.map { it.similarity }.average().toFloat().takeIf { baseline.contextChunks.isNotEmpty() },
            avgSimilarityAfter = filtered.contextChunks.map { it.similarity }.average().toFloat().takeIf { filtered.contextChunks.isNotEmpty() },
            tokensSaved = (baseline.tokensUsed ?: 0) - (filtered.tokensUsed ?: 0),
            filterApplied = filtered.filterStats != null || filtered.rerankInsights != null,
            strategy = strategy
        )
        
        return ComparisonResult(
            question = request.question,
            baseline = baseline,
            filtered = filtered,
            metrics = metrics
        )
    }
}

