package com.prike.domain.service

import com.prike.config.RAGFilterConfig
import com.prike.config.ThresholdFilterConfig as ConfigThresholdFilterConfig
import com.prike.config.RerankerConfig as ConfigRerankerConfig
import com.prike.config.AIConfig
import com.prike.domain.model.RAGRequest
import com.prike.domain.model.RAGResponse
import com.prike.domain.model.RetrievedChunk
import com.prike.domain.model.Citation
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
    private val citationParser = CitationParser()
    
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
     * @param strategy стратегия фильтрации (none, threshold, reranker, hybrid). Если null, используется из конфигурации
     * @param skipGeneration если true, не генерирует ответ, только возвращает чанки (для использования в ChatService)
     * @return ответ с контекстом и использованными чанками
     */
    suspend fun query(
        request: RAGRequest, 
        applyFilter: Boolean? = null, 
        strategy: String? = null,
        skipGeneration: Boolean = false
    ): RAGResponse {
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
            // Возвращаем пустой ответ - ChatService сам решит, как обработать
            // (с историей или без, с цитатами или без)
            return RAGResponse(
                question = request.question,
                answer = "",  // Пустой ответ - будет сгенерирован в ChatService
                contextChunks = emptyList(),
                tokensUsed = null,
                citations = emptyList()
            )
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
        
        // 3. Применяем стратегию фильтрации/реранкинга
        // Определяем, какую стратегию использовать: из запроса или из конфигурации
        val strategyToUse = strategy ?: filterConfig?.type ?: "none"
        
        // Если applyFilter явно false - не применяем фильтр
        // Если applyFilter null - используем конфигурацию или стратегию из запроса
        // Если applyFilter true - применяем фильтр
        val shouldApplyFilter = when {
            applyFilter == false -> false  // Явно отключено
            applyFilter == true -> true     // Явно включено
            strategyToUse != "none" -> true // Если стратегия указана и не "none"
            else -> filterConfig != null && filterConfig.enabled && filterConfig.type != "none"  // Используем конфигурацию
        }
        
        
        val (filteredChunks, filterStats, rerankInsights) = if (shouldApplyFilter) {
            applyFilteringStrategy(
                chunks = retrievedChunks,
                question = request.question,
                strategy = strategyToUse
            )
        } else {
            logger.debug("Filter disabled, using all ${retrievedChunks.size} chunks")
            Triple(retrievedChunks, null, null)
        }
        
        // Если после фильтрации не осталось чанков, возвращаем пустой ответ
        if (filteredChunks.isEmpty()) {
            logger.warn("No chunks left after filtering")
            return RAGResponse(
                question = request.question,
                answer = "",  // Пустой ответ - будет сгенерирован в ChatService
                contextChunks = emptyList(),
                tokensUsed = null,
                filterStats = filterStats,
                rerankInsights = rerankInsights,
                citations = emptyList()
            )
        }
        
        // Если skipGeneration = true, возвращаем только чанки без генерации ответа
        if (skipGeneration) {
            logger.debug("Skipping answer generation, returning chunks only")
            return RAGResponse(
                question = request.question,
                answer = "",  // Пустой ответ - будет сгенерирован в ChatService
                contextChunks = filteredChunks,
                tokensUsed = null,
                filterStats = filterStats,
                rerankInsights = rerankInsights,
                citations = emptyList()
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
        
        // 6. Парсим цитаты из ответа и валидируем их
        val availableDocumentsMap = filteredChunks
            .mapNotNull { chunk ->
                chunk.documentPath?.let { path ->
                    path to (chunk.documentTitle ?: path)
                }
            }
            .distinctBy { it.first }
            .toMap()
        
        val availableDocumentsPaths = availableDocumentsMap.keys.toSet()
        
        val answerWithCitations = citationParser.parseCitations(
            rawAnswer = llmResponse.answer,
            availableDocuments = availableDocumentsMap
        )
        
        // Валидируем цитаты - проверяем, что документы были в контексте
        val validatedCitations = answerWithCitations.citations.filter { citation ->
            val isValid = citationParser.validateCitation(citation, availableDocumentsPaths)
            if (!isValid) {
                logger.warn("Invalid citation detected: ${citation.documentPath} (not in context)")
            }
            isValid
        }
        
        logger.debug("Parsed ${answerWithCitations.citations.size} citations, ${validatedCitations.size} are valid")
        
        return RAGResponse(
            question = request.question,
            answer = answerWithCitations.answer,
            contextChunks = filteredChunks,
            tokensUsed = llmResponse.tokensUsed,
            filterStats = filterStats,
            rerankInsights = rerankInsights,
            citations = validatedCitations
        )
    }
    
    /**
     * Применяет стратегию фильтрации/реранкинга
     * 
     * @param chunks список чанков для обработки
     * @param question вопрос пользователя
     * @param strategy стратегия фильтрации (none, threshold, reranker, hybrid)
     * @return тройка: (отфильтрованные чанки, статистика фильтрации, решения реранкера)
     */
    private suspend fun applyFilteringStrategy(
        chunks: List<RetrievedChunk>,
        question: String,
        strategy: String
    ): Triple<List<RetrievedChunk>, FilterStats?, List<RerankDecision>?> {
        if (strategy == "none") {
            return Triple(chunks, null, null)
        }
        
        // Если нет конфигурации, но стратегия указана, используем значения по умолчанию
        val thresholdConfig = filterConfig?.threshold ?: ConfigThresholdFilterConfig(minSimilarity = 0.6f, keepTop = null)
        
        return when (strategy) {
            "threshold" -> {
                // Только пороговый фильтр
                // Преобразуем конфигурацию из config в domain
                val domainFilterConfig = com.prike.domain.service.ThresholdFilterConfig(
                    minSimilarity = thresholdConfig.minSimilarity,
                    keepTop = thresholdConfig.keepTop
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
                
                try {
                    val rerankResult = rerankerService.rerank(question, chunks)
                    // Фильтрация по shouldUse уже выполнена в RerankerService
                    Triple(rerankResult.rerankedChunks, null, rerankResult.decisions)
                } catch (e: Exception) {
                    // Если реранкер упал (таймаут, ошибка API), используем все чанки без фильтрации
                    logger.error("Reranker failed, using all chunks without filtering: ${e.message}", e)
                    Triple(chunks, null, null)
                }
            }
            
            "hybrid" -> {
                // Сначала порог, потом реранкер
                // Преобразуем конфигурацию из config в domain
                val domainFilterConfig = com.prike.domain.service.ThresholdFilterConfig(
                    minSimilarity = thresholdConfig.minSimilarity,
                    keepTop = thresholdConfig.keepTop
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
                
                try {
                    val rerankResult = rerankerService.rerank(question, filterResult.filteredChunks)
                    
                    // Фильтруем по shouldUse
                    val finalChunks = rerankResult.rerankedChunks.filter { chunk ->
                        rerankResult.decisions.find { it.chunkId == chunk.chunkId }?.shouldUse == true
                    }
                    
                    logger.info("Hybrid strategy: threshold ${filterResult.stats.retrieved} -> ${filterResult.stats.kept}, reranker -> ${finalChunks.size} chunks")
                    Triple(finalChunks, filterResult.stats, rerankResult.decisions)
                } catch (e: Exception) {
                    // Если реранкер упал, используем результат порогового фильтра
                    logger.error("Reranker failed in hybrid mode, using threshold filter result: ${e.message}", e)
                    Triple(filterResult.filteredChunks, filterResult.stats, null)
                }
            }
            
            else -> {
                logger.warn("Unknown filter strategy: $strategy, skipping filtering")
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
        
        // Парсим цитаты даже для ответа без контекста (может быть пусто)
        val answerWithCitations = citationParser.parseCitations(
            rawAnswer = llmResponse.answer,
            availableDocuments = emptyMap()
        )
        
        return RAGResponse(
            question = question,
            answer = answerWithCitations.answer,
            contextChunks = emptyList(),
            tokensUsed = llmResponse.tokensUsed,
            citations = emptyList()  // Нет контекста, значит цитаты невалидны
        )
    }
    
    /**
     * Выполняет RAG-запрос только в документации проекта (project/docs/ и project/README.md)
     * Используется для команды /help
     * 
     * @param request запрос для RAG
     * @param applyFilter применять ли фильтр/реранкер
     * @param strategy стратегия фильтрации
     * @param skipGeneration если true, не генерирует ответ, только возвращает чанки
     * @return ответ с контекстом только из документации проекта
     */
    suspend fun queryProjectDocs(
        request: RAGRequest,
        applyFilter: Boolean? = null,
        strategy: String? = null,
        skipGeneration: Boolean = false
    ): RAGResponse {
        if (request.question.isBlank()) {
            throw IllegalArgumentException("Question cannot be blank")
        }
        
        logger.info("RAG query (project docs only): ${request.question} (topK=${request.topK}, minSimilarity=${request.minSimilarity})")
        
        // 1. Поиск релевантных чанков только в документации проекта
        val searchResults = searchService.searchProjectDocs(
            query = request.question,
            limit = request.topK,
            minSimilarity = request.minSimilarity
        )
        
        if (searchResults.isEmpty()) {
            logger.warn("No relevant chunks found in project documentation for question: ${request.question}")
            return RAGResponse(
                question = request.question,
                answer = "",
                contextChunks = emptyList(),
                tokensUsed = null,
                citations = emptyList()
            )
        }
        
        logger.debug("Found ${searchResults.size} relevant chunks in project documentation")
        
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
        
        // 3. Применяем стратегию фильтрации/реранкинга (аналогично query)
        val strategyToUse = strategy ?: filterConfig?.type ?: "none"
        val shouldApplyFilter = applyFilter ?: filterConfig?.enabled ?: false
        
        val (filteredChunks, filterStats, rerankInsights) = if (shouldApplyFilter && strategyToUse != "none") {
            applyFilteringStrategy(retrievedChunks, question = request.question, strategy = strategyToUse)
        } else {
            Triple(retrievedChunks, null, null)
        }
        
        if (skipGeneration) {
            logger.debug("Skipping answer generation, returning chunks only")
            return RAGResponse(
                question = request.question,
                answer = "",
                contextChunks = filteredChunks,
                tokensUsed = null,
                filterStats = filterStats,
                rerankInsights = rerankInsights,
                citations = emptyList()
            )
        }
        
        // 4. Формируем промпт с контекстом
        val promptResult = promptBuilder.buildPromptWithContext(
            question = request.question,
            chunks = filteredChunks
        )
        
        // 5. Генерируем ответ через LLM с контекстом
        val llmResponse = llmService.generateAnswer(
            question = promptResult.userMessage,
            systemPrompt = promptResult.systemPrompt
        )
        
        logger.info("RAG query (project docs) completed: answer length=${llmResponse.answer.length}, tokens=${llmResponse.tokensUsed}")
        
        // 6. Парсим цитаты из ответа
        val availableDocumentsMap = filteredChunks
            .mapNotNull { chunk ->
                chunk.documentPath?.let { path ->
                    path to (chunk.documentTitle ?: path)
                }
            }
            .distinctBy { it.first }
            .toMap()
        
        val answerWithCitations = citationParser.parseCitations(
            rawAnswer = llmResponse.answer,
            availableDocuments = availableDocumentsMap
        )
        
        val validatedCitations = answerWithCitations.citations.filter { citation ->
            val isValid = citationParser.validateCitation(citation, availableDocumentsMap.keys.toSet())
            if (!isValid) {
                logger.warn("Invalid citation detected: ${citation.documentPath} (not in context)")
            }
            isValid
        }
        
        return RAGResponse(
            question = request.question,
            answer = answerWithCitations.answer,
            contextChunks = filteredChunks,
            tokensUsed = llmResponse.tokensUsed,
            filterStats = filterStats,
            rerankInsights = rerankInsights,
            citations = validatedCitations
        )
    }
    
    /**
     * Закрывает ресурсы (реранкер)
     */
    fun close() {
        rerankerService?.close()
    }
}

