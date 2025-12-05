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
 * Репозиторий для работы с отзывами и анализами в БД
 */
class ReviewsRepository(
    private val database: Database
) {
    private val logger = LoggerFactory.getLogger(ReviewsRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Сохраняет отзывы в БД
     */
    fun saveReviews(reviews: List<Review>, weekStart: String): Boolean {
        return try {
            transaction(database) {
                reviews.forEach { review ->
                    ReviewsTable.insertIgnore {
                        it[id] = review.id
                        it[text] = review.text
                        it[rating] = review.rating
                        it[date] = review.date
                        it[ReviewsTable.weekStart] = weekStart
                        it[createdAt] = Instant.now().toString()
                    }
                }
            }
            logger.info("Saved ${reviews.size} reviews for week $weekStart")
            true
        } catch (e: Exception) {
            logger.error("Error saving reviews: ${e.message}", e)
            false
        }
    }

    /**
     * Получает отзывы за неделю из БД
     */
    fun getWeekReviews(weekStart: String): List<Review> {
        return try {
            transaction(database) {
                ReviewsTable
                    .select { ReviewsTable.weekStart eq weekStart }
                    .map { row ->
                        Review(
                            id = row[ReviewsTable.id],
                            text = row[ReviewsTable.text],
                            rating = row[ReviewsTable.rating],
                            date = row[ReviewsTable.date]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting week reviews: ${e.message}", e)
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
