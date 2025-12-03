package com.prike.presentation.dto

import com.prike.domain.model.CodeReview
import com.prike.domain.model.ReviewIssue
import com.prike.domain.model.ReviewSuggestion
import kotlinx.serialization.Serializable

/**
 * DTO для запроса на ревью PR
 */
@Serializable
data class ReviewPRRequest(
    val base: String,  // Базовая ветка (например, "main")
    val head: String  // Целевая ветка (например, "feature-branch")
)

/**
 * DTO для ответа на запрос ревью PR
 */
@Serializable
data class ReviewPRResponse(
    val reviewId: String,
    val baseBranch: String,
    val headBranch: String,
    val status: String,  // "pending", "completed", "error"
    val message: String? = null
)

/**
 * DTO для запроса ревью по diff
 */
@Serializable
data class ReviewDiffRequest(
    val diff: String,  // Diff в формате unified diff
    val baseBranch: String? = null,  // Опционально: базовая ветка
    val headBranch: String? = null,  // Опционально: целевая ветка
    val changedFiles: List<String> = emptyList()  // Опционально: список изменённых файлов
)

/**
 * DTO для результата ревью кода (полный ответ)
 */
@Serializable
data class CodeReviewDto(
    val reviewId: String,
    val baseBranch: String,
    val headBranch: String,
    val changedFiles: List<String>,
    val issues: List<ReviewIssueDto>,
    val suggestions: List<ReviewSuggestionDto>,
    val summary: String,
    val overallScore: String?,
    val timestamp: Long
) {
    companion object {
        fun fromDomain(review: CodeReview): CodeReviewDto {
            return CodeReviewDto(
                reviewId = review.reviewId,
                baseBranch = review.baseBranch,
                headBranch = review.headBranch,
                changedFiles = review.changedFiles,
                issues = review.issues.map { ReviewIssueDto.fromDomain(it) },
                suggestions = review.suggestions.map { ReviewSuggestionDto.fromDomain(it) },
                summary = review.summary,
                overallScore = review.overallScore,
                timestamp = review.timestamp
            )
        }
    }
}

/**
 * DTO для проблемы в ревью
 */
@Serializable
data class ReviewIssueDto(
    val type: String,  // "BUG", "SECURITY", "PERFORMANCE", "STYLE", "LOGIC", "DOCUMENTATION"
    val severity: String,  // "critical", "high", "medium", "low"
    val file: String,
    val line: Int?,
    val message: String,
    val suggestion: String?
) {
    companion object {
        fun fromDomain(issue: ReviewIssue): ReviewIssueDto {
            return ReviewIssueDto(
                type = issue.type.name,
                severity = issue.severity,
                file = issue.file,
                line = issue.line,
                message = issue.message,
                suggestion = issue.suggestion
            )
        }
    }
}

/**
 * DTO для предложения в ревью
 */
@Serializable
data class ReviewSuggestionDto(
    val file: String,
    val line: Int?,
    val message: String,
    val priority: String  // "high", "medium", "low"
) {
    companion object {
        fun fromDomain(suggestion: ReviewSuggestion): ReviewSuggestionDto {
            return ReviewSuggestionDto(
                file = suggestion.file,
                line = suggestion.line,
                message = suggestion.message,
                priority = suggestion.priority
            )
        }
    }
}

