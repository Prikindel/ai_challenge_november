package com.prike.domain.service

import com.prike.config.RAGFilterConfig
import com.prike.config.ThresholdFilterConfig as ConfigThresholdFilterConfig
import com.prike.config.RerankerConfig as ConfigRerankerConfig
import com.prike.config.AIConfig
import com.prike.domain.model.RAGRequest
import com.prike.domain.model.RAGResponse
import com.prike.domain.model.RetrievedChunk
import org.slf4j.LoggerFactory

/**
 * Сервис для RAG-запросов
 * Объединяет поиск по базе знаний с генерацией ответа через LLM
 * Поддерживает различные стратегии фильтрации и реранкинга
 */
class RAGService(
    private val searchService: KnowledgeBaseSearchService,
    private val llmService: LLMService,
    private val promptBuilder: PromptBuilder,
    private val filterConfig: RAGFilterConfig? = null,  // Конфигурация фильтрации
    private val aiConfig: AIConfig? = null  // Конфигурация AI для реранкера
) {
    private val logger = LoggerFactory.getLogger(RAGService::class.java)
    
    private val rerankerService: RerankerService? = if (filterConfig != null && aiConfig != null) {
        // Преобразуем конфигурацию из config в domain
        val domainRerankerConfig = com.prike.domain.service.RerankerConfig(
            model = filterConfig.reranker.model,
            maxChunks = filterConfig.reranker.maxChunks,
            systemPrompt = filterConfig.reranker.systemPrompt
        )
        RerankerService(aiConfig, domainRerankerConfig)
    } else {
        null
    }
    
    /**
     * Выполняет RAG-запрос: поиск чанков + генерация ответа с контекстом
     * 
     * @param request запрос для RAG
     * @param applyFilter применять ли фильтр/реранкер (по умолчанию используется конфигурация)
     * @return ответ с контекстом и использованными чанками
     */
    suspend fun query(request: RAGRequest, applyFilter: Boolean? = null): RAGResponse {
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
        
        // 3. Применяем стратегию фильтрации/реранкинга (если не отключено явно)
        val shouldApplyFilter = applyFilter ?: (filterConfig != null && filterConfig.enabled)
        val (filteredChunks, filterStats, rerankInsights) = if (shouldApplyFilter) {
            applyFilteringStrategy(
                chunks = retrievedChunks,
                question = request.question
            )
        } else {
            Triple(retrievedChunks, null, null)
        }
        
        // Если после фильтрации не осталось чанков, возвращаем ответ без контекста
        if (filteredChunks.isEmpty()) {
            logger.warn("No chunks left after filtering")
            return generateResponseWithoutContext(request.question).copy(
                filterStats = filterStats,
                rerankInsights = rerankInsights
            )
        }
        
        // 4. Формируем промпт с контекстом
        val promptResult = promptBuilder.buildPromptWithContext(
            question = request.question,
            chunks = filteredChunks
        )
        
        logger.debug("Built prompt with ${filteredChunks.size} chunks (systemPrompt length: ${promptResult.systemPrompt.length}, userMessage length: ${promptResult.userMessage.length})")
        
        // 5. Генерируем ответ через LLM с контекстом
        val llmResponse = llmService.generateAnswer(
            question = promptResult.userMessage,
            systemPrompt = promptResult.systemPrompt
        )
        
        logger.info("RAG query completed: answer length=${llmResponse.answer.length}, tokens=${llmResponse.tokensUsed}")
        
        return RAGResponse(
            question = request.question,
            answer = llmResponse.answer,
            contextChunks = filteredChunks,
            tokensUsed = llmResponse.tokensUsed,
            filterStats = filterStats,
            rerankInsights = rerankInsights
        )
    }
    
    /**
     * Применяет стратегию фильтрации/реранкинга
     * 
     * @param chunks список чанков для обработки
     * @param question вопрос пользователя
     * @return тройка: (отфильтрованные чанки, статистика фильтрации, решения реранкера)
     */
    private suspend fun applyFilteringStrategy(
        chunks: List<RetrievedChunk>,
        question: String
    ): Triple<List<RetrievedChunk>, FilterStats?, List<RerankDecision>?> {
        if (filterConfig == null || !filterConfig.enabled || filterConfig.type == "none") {
            return Triple(chunks, null, null)
        }
        
        return when (filterConfig.type) {
            "threshold" -> {
                // Только пороговый фильтр
                val domainFilterConfig = ThresholdFilterConfig(
                    minSimilarity = filterConfig.threshold.minSimilarity,
                    keepTop = filterConfig.threshold.keepTop
                )
                val filter = RelevanceFilter(domainFilterConfig)
                val filterResult = filter.filter(chunks)
                logger.info("Threshold filter applied: ${filterResult.stats.retrieved} -> ${filterResult.stats.kept} chunks")
                Triple(filterResult.filteredChunks, filterResult.stats, null)
            }
            
            "reranker" -> {
                // Только реранкер
                if (rerankerService == null) {
                    logger.warn("Reranker service not available, skipping reranking")
                    return Triple(chunks, null, null)
                }
                
                val rerankResult = rerankerService.rerank(question, chunks)
                
                // Фильтруем по shouldUse
                val filteredChunks = rerankResult.rerankedChunks.filter { chunk ->
                    rerankResult.decisions.find { it.chunkId == chunk.chunkId }?.shouldUse == true
                }
                
                logger.info("Reranker applied: ${chunks.size} -> ${filteredChunks.size} chunks")
                Triple(filteredChunks, null, rerankResult.decisions)
            }
            
            "hybrid" -> {
                // Сначала порог, потом реранкер
                val domainFilterConfig = ThresholdFilterConfig(
                    minSimilarity = filterConfig.threshold.minSimilarity,
                    keepTop = filterConfig.threshold.keepTop
                )
                val filter = RelevanceFilter(domainFilterConfig)
                val filterResult = filter.filter(chunks)
                
                if (filterResult.filteredChunks.isEmpty()) {
                    return Triple(emptyList(), filterResult.stats, null)
                }
                
                // Применяем реранкер к отфильтрованным чанкам
                if (rerankerService == null) {
                    logger.warn("Reranker service not available, using threshold filter only")
                    return Triple(filterResult.filteredChunks, filterResult.stats, null)
                }
                
                val rerankResult = rerankerService.rerank(question, filterResult.filteredChunks)
                
                // Фильтруем по shouldUse
                val finalChunks = rerankResult.rerankedChunks.filter { chunk ->
                    rerankResult.decisions.find { it.chunkId == chunk.chunkId }?.shouldUse == true
                }
                
                logger.info("Hybrid strategy: threshold ${filterResult.stats.retrieved} -> ${filterResult.stats.kept}, reranker -> ${finalChunks.size} chunks")
                Triple(finalChunks, filterResult.stats, rerankResult.decisions)
            }
            
            else -> {
                logger.warn("Unknown filter strategy: ${filterConfig.type}, skipping filtering")
                Triple(chunks, null, null)
            }
        }
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
    
    /**
     * Закрывает ресурсы (реранкер)
     */
    fun close() {
        rerankerService?.close()
    }
}

