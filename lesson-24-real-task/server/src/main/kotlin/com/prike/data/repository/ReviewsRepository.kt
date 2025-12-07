package com.prike.data.repository

import com.prike.domain.model.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Репозиторий для работы с саммари отзывов и анализами в БД
 */
class ReviewsRepository(
    private val database: Database
) {
    private val logger = LoggerFactory.getLogger(ReviewsRepository::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Сохраняет саммари отзывов в БД
     */
    fun saveReviewSummaries(summaries: List<ReviewSummary>, weekStart: String): Boolean {
        return try {
            transaction(database) {
                summaries.forEach { summary ->
                    ReviewSummariesTable.insertIgnore {
                        it[reviewId] = summary.reviewId
                        it[rating] = summary.rating
                        it[date] = summary.date
                        it[ReviewSummariesTable.summary] = summary.summary
                        it[category] = summary.category.name
                        it[topics] = json.encodeToString(summary.topics)
                        it[criticality] = summary.criticality.name
                        it[ReviewSummariesTable.weekStart] = weekStart
                        it[createdAt] = Instant.now().toString()
                    }
                }
            }
            logger.info("Saved ${summaries.size} review summaries for week $weekStart")
            true
        } catch (e: Exception) {
            logger.error("Error saving review summaries: ${e.message}", e)
            false
        }
    }

    /**
     * Получает саммари отзывов за неделю из БД
     */
    fun getWeekSummaries(weekStart: String): List<ReviewSummary> {
        return try {
            transaction(database) {
                ReviewSummariesTable
                    .select { ReviewSummariesTable.weekStart eq weekStart }
                    .map { row ->
                        ReviewSummary(
                            reviewId = row[ReviewSummariesTable.reviewId],
                            rating = row[ReviewSummariesTable.rating],
                            date = row[ReviewSummariesTable.date],
                            summary = row[ReviewSummariesTable.summary],
                            category = ReviewCategory.valueOf(row[ReviewSummariesTable.category]),
                            topics = json.decodeFromString<List<String>>(row[ReviewSummariesTable.topics]),
                            criticality = Criticality.valueOf(row[ReviewSummariesTable.criticality]),
                            weekStart = row[ReviewSummariesTable.weekStart]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting week summaries: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Получает все саммари отзывов (для поиска)
     */
    fun getAllSummaries(): List<ReviewSummary> {
        return try {
            transaction(database) {
                ReviewSummariesTable
                    .selectAll()
                    .orderBy(ReviewSummariesTable.date to SortOrder.DESC)
                    .map { row ->
                        ReviewSummary(
                            reviewId = row[ReviewSummariesTable.reviewId],
                            rating = row[ReviewSummariesTable.rating],
                            date = row[ReviewSummariesTable.date],
                            summary = row[ReviewSummariesTable.summary],
                            category = ReviewCategory.valueOf(row[ReviewSummariesTable.category]),
                            topics = json.decodeFromString<List<String>>(row[ReviewSummariesTable.topics]),
                            criticality = Criticality.valueOf(row[ReviewSummariesTable.criticality]),
                            weekStart = row[ReviewSummariesTable.weekStart]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting all summaries: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Сохраняет анализ недели в БД
     */
    fun saveWeekAnalysis(weekStart: String, stats: WeekStats): Boolean {
        return try {
            transaction(database) {
                WeekAnalysesTable.insertIgnore {
                    it[WeekAnalysesTable.weekStart] = weekStart
                    it[totalReviews] = stats.totalReviews
                    it[positiveCount] = stats.positiveCount
                    it[negativeCount] = stats.negativeCount
                    it[neutralCount] = stats.neutralCount
                    it[averageRating] = stats.averageRating
                    it[analysisJson] = json.encodeToString(WeekStats.serializer(), stats)
                    it[createdAt] = Instant.now().toString()
                }
            }
            logger.info("Saved week analysis for week $weekStart")
            true
        } catch (e: Exception) {
            logger.error("Error saving week analysis: ${e.message}", e)
            false
        }
    }

    /**
     * Получает анализ предыдущей недели из БД
     */
    fun getPreviousWeekAnalysis(currentWeekStart: String): WeekStats? {
        return try {
            val currentDate = LocalDate.parse(currentWeekStart, DateTimeFormatter.ISO_DATE)
            val previousWeekStart = currentDate.minusWeeks(1).format(DateTimeFormatter.ISO_DATE)

            transaction(database) {
                WeekAnalysesTable
                    .select { WeekAnalysesTable.weekStart eq previousWeekStart }
                    .firstOrNull()
                    ?.let { row ->
                        val analysisJson = row[WeekAnalysesTable.analysisJson]
                        json.decodeFromString<WeekStats>(analysisJson)
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting previous week analysis: ${e.message}", e)
            null
        }
    }

    /**
     * Получает анализ недели по weekStart
     */
    fun getWeekAnalysis(weekStart: String): WeekStats? {
        return try {
            transaction(database) {
                WeekAnalysesTable
                    .select { WeekAnalysesTable.weekStart eq weekStart }
                    .firstOrNull()
                    ?.let { row ->
                        val analysisJson = row[WeekAnalysesTable.analysisJson]
                        json.decodeFromString<WeekStats>(analysisJson)
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting week analysis: ${e.message}", e)
            null
        }
    }

    /**
     * Получает все анализы недель
     */
    fun getAllWeekAnalyses(): List<WeekStats> {
        return try {
            transaction(database) {
                WeekAnalysesTable
                    .selectAll()
                    .orderBy(WeekAnalysesTable.weekStart to SortOrder.DESC)
                    .map { row ->
                        val analysisJson = row[WeekAnalysesTable.analysisJson]
                        json.decodeFromString<WeekStats>(analysisJson)
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting all week analyses: ${e.message}", e)
            emptyList()
        }
    }
}
