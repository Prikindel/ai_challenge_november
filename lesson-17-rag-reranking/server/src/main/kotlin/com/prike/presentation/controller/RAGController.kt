package com.prike.presentation.controller

import com.prike.domain.model.RAGRequest
import com.prike.domain.service.ComparisonService
import com.prike.domain.service.RAGService
import com.prike.domain.service.LLMService
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
    private val comparisonService: ComparisonService
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
                    
                    val ragResponse = ragService.query(ragRequest)
                    
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
                    
                    val comparisonResult = comparisonService.compare(ragRequest)
                    
                    call.respond(ComparisonResponseDto(
                        question = comparisonResult.question,
                        ragResponse = mapRAGResponseToDto(comparisonResult.ragResponse),
                        standardResponse = StandardResponseDto(
                            question = comparisonResult.standardResponse.question,
                            answer = comparisonResult.standardResponse.answer,
                            tokensUsed = comparisonResult.standardResponse.tokensUsed
                        )
                    ))
                } catch (e: Exception) {
                    logger.error("Comparison error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to compare responses: ${e.message}")
                    )
                }
            }
        }
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

