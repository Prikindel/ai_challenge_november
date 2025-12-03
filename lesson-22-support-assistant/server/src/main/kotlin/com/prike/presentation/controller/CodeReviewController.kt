package com.prike.presentation.controller

import com.prike.domain.model.CodeReview
import com.prike.domain.service.CodeReviewService
import com.prike.presentation.dto.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Контроллер для API ревью кода
 */
class CodeReviewController(
    private val codeReviewService: CodeReviewService
) {
    private val logger = LoggerFactory.getLogger(CodeReviewController::class.java)
    
    // In-memory хранилище ревью (в продакшене можно использовать БД)
    private val reviewsStorage = ConcurrentHashMap<String, CodeReview>()
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Запустить ревью PR
            post("/api/review/pr") {
                try {
                    val request = call.receive<ReviewPRRequest>()
                    
                    logger.info("Starting code review for PR: ${request.base}..${request.head}")
                    
                    // Запускаем ревью асинхронно
                    val reviewId = java.util.UUID.randomUUID().toString()
                    
                    // Создаём временный ревью со статусом "pending"
                    val pendingReview = CodeReview(
                        reviewId = reviewId,
                        baseBranch = request.base,
                        headBranch = request.head,
                        changedFiles = emptyList(),
                        issues = emptyList(),
                        suggestions = emptyList(),
                        summary = "Ревью выполняется...",
                        overallScore = null
                    )
                    reviewsStorage[reviewId] = pendingReview
                    
                    // Запускаем ревью в фоне
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val review = codeReviewService.reviewPR(
                                base = request.base,
                                head = request.head
                            )
                            reviewsStorage[reviewId] = review
                            logger.info("Code review completed: $reviewId")
                        } catch (e: Exception) {
                            logger.error("Failed to complete code review: $reviewId", e)
                            // Обновляем ревью с ошибкой
                            val errorReview = pendingReview.copy(
                                summary = "Ошибка при выполнении ревью: ${e.message}"
                            )
                            reviewsStorage[reviewId] = errorReview
                        }
                    }
                    
                    call.respond(
                        HttpStatusCode.Accepted,
                        ReviewPRResponse(
                            reviewId = reviewId,
                            baseBranch = request.base,
                            headBranch = request.head,
                            status = "pending",
                            message = "Ревью запущено, используйте GET /api/review/pr/$reviewId для получения результата"
                        )
                    )
                } catch (e: IllegalArgumentException) {
                    logger.warn("Invalid request: ${e.message}")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid request: ${e.message}")
                    )
                } catch (e: Exception) {
                    logger.error("Failed to start code review", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to start code review: ${e.message}")
                    )
                }
            }
            
            // Получить результат ревью по ID
            get("/api/review/pr/{reviewId}") {
                try {
                    val reviewId = call.parameters["reviewId"]
                        ?: throw IllegalArgumentException("reviewId is required")
                    
                    val review = reviewsStorage[reviewId]
                    if (review == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Review not found: $reviewId")
                        )
                        return@get
                    }
                    
                    // Определяем статус ревью
                    val status = when {
                        review.summary.contains("Ошибка") -> "error"
                        review.issues.isEmpty() && review.suggestions.isEmpty() && 
                        review.summary.contains("Ревью выполняется") -> "pending"
                        else -> "completed"
                    }
                    
                    call.respond(CodeReviewDto.fromDomain(review))
                } catch (e: Exception) {
                    logger.error("Failed to get review", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get review: ${e.message}")
                    )
                }
            }
            
            // Ревью по diff (для UI)
            post("/api/review/diff") {
                try {
                    val request = call.receive<ReviewDiffRequest>()
                    
                    logger.info("Starting code review for diff (${request.changedFiles.size} files)")
                    
                    if (request.diff.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Diff cannot be empty")
                        )
                        return@post
                    }
                    
                    // Запускаем ревью асинхронно
                    val reviewId = java.util.UUID.randomUUID().toString()
                    
                    // Создаём временный ревью со статусом "pending"
                    val pendingReview = CodeReview(
                        reviewId = reviewId,
                        baseBranch = request.baseBranch ?: "unknown",
                        headBranch = request.headBranch ?: "unknown",
                        changedFiles = request.changedFiles.ifEmpty { 
                            // Пытаемся извлечь файлы из diff
                            extractFilesFromDiff(request.diff)
                        },
                        issues = emptyList(),
                        suggestions = emptyList(),
                        summary = "Ревью выполняется...",
                        overallScore = null
                    )
                    reviewsStorage[reviewId] = pendingReview
                    
                    // Запускаем ревью в фоне
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val review = codeReviewService.reviewDiff(
                                diff = request.diff,
                                changedFiles = pendingReview.changedFiles,
                                baseBranch = request.baseBranch ?: "unknown",
                                headBranch = request.headBranch ?: "unknown"
                            )
                            
                            reviewsStorage[reviewId] = review
                            logger.info("Diff review completed: $reviewId")
                        } catch (e: Exception) {
                            logger.error("Failed to complete diff review: $reviewId", e)
                            // Обновляем ревью с ошибкой
                            val errorReview = pendingReview.copy(
                                summary = "Ошибка при выполнении ревью: ${e.message}"
                            )
                            reviewsStorage[reviewId] = errorReview
                        }
                    }
                    
                    call.respond(
                        HttpStatusCode.Accepted,
                        ReviewPRResponse(
                            reviewId = reviewId,
                            baseBranch = request.baseBranch ?: "unknown",
                            headBranch = request.headBranch ?: "unknown",
                            status = "pending",
                            message = "Ревью запущено, используйте GET /api/review/pr/$reviewId для получения результата"
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to review diff", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to review diff: ${e.message}")
                    )
                }
            }
            
            // Получить список всех ревью (опционально, для отладки)
            get("/api/review/pr") {
                try {
                    val reviews = reviewsStorage.values.map { CodeReviewDto.fromDomain(it) }
                    call.respond(reviews)
                } catch (e: Exception) {
                    logger.error("Failed to get reviews list", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get reviews list: ${e.message}")
                    )
                }
            }
        }
    }
    
    /**
     * Базовый анализ diff (упрощённая версия)
     */
    private fun analyzeDiff(diff: String, changedFiles: List<String>): Map<String, Any> {
        val addedLines = diff.lines().count { it.startsWith("+") && !it.startsWith("+++") }
        val removedLines = diff.lines().count { it.startsWith("-") && !it.startsWith("---") }
        
        val fileTypes = changedFiles.map { file ->
            when {
                file.endsWith(".kt") -> "kotlin"
                file.endsWith(".java") -> "java"
                file.endsWith(".js") -> "javascript"
                file.endsWith(".ts") -> "typescript"
                file.endsWith(".py") -> "python"
                file.endsWith(".md") -> "markdown"
                file.endsWith(".yaml") || file.endsWith(".yml") -> "yaml"
                else -> "other"
            }
        }.groupingBy { it }.eachCount()
        
        return mapOf(
            "addedLines" to addedLines,
            "removedLines" to removedLines,
            "fileTypes" to fileTypes,
            "totalFiles" to changedFiles.size
        )
    }
    
    /**
     * Извлекает список файлов из diff
     */
    private fun extractFilesFromDiff(diff: String): List<String> {
        val files = mutableSetOf<String>()
        diff.lines().forEach { line ->
            if (line.startsWith("+++") || line.startsWith("---")) {
                val filePath = line.substring(3).trim()
                if (filePath.isNotBlank() && !filePath.startsWith("/dev/null")) {
                    // Убираем префикс "a/" или "b/" если есть
                    val cleanPath = filePath.removePrefix("a/").removePrefix("b/")
                    files.add(cleanPath)
                }
            }
        }
        return files.toList()
    }
    
}

