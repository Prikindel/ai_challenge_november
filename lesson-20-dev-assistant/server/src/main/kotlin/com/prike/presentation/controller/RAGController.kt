package com.prike.presentation.controller

import com.prike.domain.model.RAGRequest
import com.prike.domain.service.ComparisonService
import com.prike.domain.service.RAGService
import com.prike.domain.service.LLMService
import com.prike.domain.service.CitationAnalyzer
import com.prike.presentation.dto.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для API RAG-запросов
 */
class RAGController(
    private val ragService: RAGService,
    private val llmService: LLMService,
    private val comparisonService: ComparisonService,
    private val citationAnalyzer: CitationAnalyzer? = null,
    private val filterConfig: com.prike.config.RAGFilterConfig? = null  // Конфигурация фильтра для чтения
) {
    private val logger = LoggerFactory.getLogger(RAGController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // RAG-запрос (с контекстом)
            post("/api/rag/query") {
                try {
                    val request = call.receive<RAGQueryRequestDto>()
                    
                    // Валидация входных данных
                    if (request.question.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("question cannot be blank")
                        )
                        return@post
                    }
                    
                    val ragRequest = RAGRequest(
                        question = request.question,
                        topK = request.topK.coerceIn(1, 10),
                        minSimilarity = request.minSimilarity.coerceIn(0f, 1f)
                    )
                    
                    // Применяем фильтр согласно запросу (или конфигурации)
                    val applyFilter = request.applyFilter
                    val strategy = request.strategy?.takeIf { it in listOf("none", "threshold", "reranker", "hybrid") }
                    logger.debug("RAG query: applyFilter=$applyFilter, strategy=$strategy")
                    val ragResponse = ragService.query(ragRequest, applyFilter = applyFilter, strategy = strategy)
                    
                    call.respond(mapRAGResponseToDto(ragResponse))
                } catch (e: Exception) {
                    logger.error("RAG query error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to process RAG query: ${e.message}")
                    )
                }
            }
            
            // Обычный запрос (без контекста)
            post("/api/rag/standard") {
                try {
                    val request = call.receive<StandardQueryRequestDto>()
                    
                    // Валидация входных данных
                    if (request.question.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("question cannot be blank")
                        )
                        return@post
                    }
                    
                    val llmResponse = llmService.generateAnswer(
                        question = request.question,
                        systemPrompt = "Ты — помощник, который отвечает на вопросы."
                    )
                    
                    call.respond(StandardResponseDto(
                        question = request.question,
                        answer = llmResponse.answer,
                        tokensUsed = llmResponse.tokensUsed
                    ))
                } catch (e: Exception) {
                    logger.error("Standard query error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to process standard query: ${e.message}")
                    )
                }
            }
            
            // Сравнение обоих режимов
            post("/api/rag/compare") {
                try {
                    val request = call.receive<RAGQueryRequestDto>()
                    
                    // Валидация входных данных
                    if (request.question.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("question cannot be blank")
                        )
                        return@post
                    }
                    
                    val ragRequest = RAGRequest(
                        question = request.question,
                        topK = request.topK.coerceIn(1, 10),
                        minSimilarity = request.minSimilarity.coerceIn(0f, 1f)
                    )
                    
                    // Получаем стратегию из запроса
                    val strategy = request.strategy?.takeIf { it in listOf("none", "threshold", "reranker", "hybrid") }
                    logger.debug("Comparison: strategy=$strategy")
                    
                    // Используем новый метод сравнения с фильтром
                    val comparisonResult = comparisonService.compareBaselineVsFiltered(ragRequest, strategy = strategy)
                    
                    call.respond(mapComparisonResultToDto(comparisonResult))
                } catch (e: Exception) {
                    logger.error("Comparison error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to compare responses: ${e.message}")
                    )
                }
            }
            
            // Получить конфигурацию фильтра
            get("/api/rag/filter/config") {
                try {
                    if (filterConfig == null) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.NotFound,
                            ErrorResponse("Filter configuration not available")
                        )
                        return@get
                    }
                    
                    val configDto = FilterConfigDto(
                        enabled = filterConfig.enabled,
                        strategy = filterConfig.type,
                        threshold = ThresholdConfigDto(
                            minSimilarity = filterConfig.threshold.minSimilarity,
                            keepTop = filterConfig.threshold.keepTop
                        )
                    )
                    
                    call.respond(configDto)
                } catch (e: Exception) {
                    logger.error("Failed to get filter config", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get filter config: ${e.message}")
                    )
                }
            }
            
            // Обновить конфигурацию фильтра (только для чтения в текущей реализации)
            // В полной реализации можно добавить динамическое обновление конфигурации
            post("/api/rag/filter/config") {
                try {
                    call.receive<UpdateFilterConfigRequestDto>()  // Принимаем запрос, но не используем
                    
                    // В текущей реализации конфигурация читается из файла
                    // Здесь можно добавить логику обновления конфигурации в памяти
                    // или сохранения в файл
                    
                    call.respond(
                        io.ktor.http.HttpStatusCode.NotImplemented,
                        ErrorResponse("Dynamic filter configuration update is not implemented. Please update config/server.yaml and restart the server.")
                    )
                } catch (e: Exception) {
                    logger.error("Failed to update filter config", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to update filter config: ${e.message}")
                    )
                }
            }
            
            // Тестирование цитат на нескольких вопросах
            post("/api/rag/test-citations") {
                try {
                    if (citationAnalyzer == null) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("Citation analyzer is not available")
                        )
                        return@post
                    }
                    
                    val request = call.receive<CitationTestRequestDto>()
                    
                    // Валидация
                    if (request.questions.isEmpty()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("questions list cannot be empty")
                        )
                        return@post
                    }
                    
                    if (request.questions.size > 20) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("Too many questions. Maximum is 20")
                        )
                        return@post
                    }
                    
                    logger.info("Starting citation test with ${request.questions.size} questions")
                    
                    // Выполняем тест
                    val report = citationAnalyzer.testCitations(
                        questions = request.questions,
                        topK = request.topK.coerceIn(1, 10),
                        minSimilarity = request.minSimilarity.coerceIn(0f, 1f),
                        applyFilter = request.applyFilter,
                        strategy = request.strategy.takeIf { it in listOf("none", "threshold", "reranker", "hybrid") } ?: "hybrid"
                    )
                    
                    // Преобразуем в DTO
                    val reportDto = CitationTestReportDto(
                        results = report.results.map { result ->
                            CitationTestResultDto(
                                question = result.question,
                                hasCitations = result.hasCitations,
                                citationsCount = result.citationsCount,
                                validCitationsCount = result.validCitationsCount,
                                answer = result.answer,
                                citations = result.citations.map { citation ->
                                    CitationDto(
                                        text = citation.text,
                                        documentPath = citation.documentPath,
                                        documentTitle = citation.documentTitle,
                                        chunkId = citation.chunkId
                                    )
                                }
                            )
                        },
                        metrics = CitationMetricsDto(
                            totalQuestions = report.metrics.totalQuestions,
                            questionsWithCitations = report.metrics.questionsWithCitations,
                            averageCitationsPerAnswer = report.metrics.averageCitationsPerAnswer,
                            validCitationsPercentage = report.metrics.validCitationsPercentage,
                            answersWithoutHallucinations = report.metrics.answersWithoutHallucinations
                        )
                    )
                    
                    call.respond(reportDto)
                } catch (e: Exception) {
                    logger.error("Citation test error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to test citations: ${e.message}")
                    )
                }
            }
        }
    }
    
    /**
     * Преобразует ComparisonResult в DTO
     */
    private fun mapComparisonResultToDto(result: com.prike.domain.model.ComparisonResult): ComparisonResponseDto {
        return ComparisonResponseDto(
            question = result.question,
            baseline = result.baseline?.let { mapRAGResponseToDto(it) },
            filtered = result.filtered?.let { mapRAGResponseToDto(it) },
            ragResponse = result.filtered?.let { mapRAGResponseToDto(it) },  // Для обратной совместимости
            standardResponse = result.standardResponse?.let {
                StandardResponseDto(
                    question = it.question,
                    answer = it.answer,
                    tokensUsed = it.tokensUsed
                )
            },
            metrics = result.metrics?.let {
                ComparisonMetricsDto(
                    baselineChunks = it.baselineChunks,
                    filteredChunks = it.filteredChunks,
                    avgSimilarityBefore = it.avgSimilarityBefore,
                    avgSimilarityAfter = it.avgSimilarityAfter,
                    tokensSaved = it.tokensSaved,
                    filterApplied = it.filterApplied,
                    strategy = it.strategy
                )
            }
        )
    }
    
    /**
     * Преобразует RAGResponse в DTO
     */
    private fun mapRAGResponseToDto(ragResponse: com.prike.domain.model.RAGResponse): RAGQueryResponseDto {
        return RAGQueryResponseDto(
            question = ragResponse.question,
            answer = ragResponse.answer,
            contextChunks = ragResponse.contextChunks.map { chunk ->
                RetrievedChunkDto(
                    chunkId = chunk.chunkId,
                    documentPath = chunk.documentPath,
                    documentTitle = chunk.documentTitle,
                    content = chunk.content,
                    similarity = chunk.similarity,
                    chunkIndex = chunk.chunkIndex
                )
            },
            tokensUsed = ragResponse.tokensUsed,
            filterStats = ragResponse.filterStats?.let { stats ->
                FilterStatsDto(
                    retrieved = stats.retrieved,
                    kept = stats.kept,
                    dropped = stats.dropped.map { dropped ->
                        DroppedChunkDto(
                            chunkId = dropped.chunkId,
                            documentPath = dropped.documentPath,
                            similarity = dropped.similarity,
                            reason = dropped.reason
                        )
                    },
                    avgSimilarityBefore = stats.avgSimilarityBefore,
                    avgSimilarityAfter = stats.avgSimilarityAfter
                )
            },
            rerankInsights = ragResponse.rerankInsights?.map { decision ->
                RerankDecisionDto(
                    chunkId = decision.chunkId,
                    rerankScore = decision.rerankScore,
                    reason = decision.reason,
                    shouldUse = decision.shouldUse
                )
            },
            citations = ragResponse.citations.map { citation ->
                CitationDto(
                    text = citation.text,
                    documentPath = citation.documentPath,
                    documentTitle = citation.documentTitle,
                    chunkId = citation.chunkId
                )
            }
        )
    }
}

/**
 * DTO для обычного запроса
 */
@kotlinx.serialization.Serializable
data class StandardQueryRequestDto(
    val question: String
)

