package com.prike.domain.service

import com.prike.domain.agent.ReviewsAnalyzerAgent
import com.prike.domain.model.*
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Сервис для оркестрации анализа отзывов
 */
class ReviewsAnalysisService(
    private val agent: ReviewsAnalyzerAgent
) {
    private val logger = LoggerFactory.getLogger(ReviewsAnalysisService::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_DATE

    /**
     * Анализирует отзывы за указанную неделю
     * 
     * @param fromDate Дата начала недели (опционально, если не указана - текущая неделя)
     * @param toDate Дата конца недели (опционально)
     * @return WeekStats со статистикой и анализом
     */
    suspend fun analyzeWeek(
        fromDate: String? = null,
        toDate: String? = null
    ): WeekStats {
        // Определяем даты недели
        val (weekStart, weekEnd) = if (fromDate != null && toDate != null) {
            Pair(fromDate, toDate)
        } else {
            calculateWeekDates(fromDate)
        }

        logger.info("Starting analysis for week: $weekStart to $weekEnd")

        // 1. Собрать отзывы
        val reviews = agent.fetchReviews(weekStart, weekEnd)
        logger.info("Fetched ${reviews.size} reviews")

        if (reviews.isEmpty()) {
            logger.warn("No reviews found for week $weekStart")
            return createEmptyWeekStats(weekStart)
        }

        // 2. Сохранить в БД (временно закомментировано - будет переделано)
        // TODO: После анализа отзывов создать саммари и сохранить
        // val saved = agent.saveReviewSummaries(summaries, weekStart)

        // 3. Классифицировать через LLM
        val analyses = agent.classifyReviews(reviews)
        logger.info("Classified ${analyses.size} reviews")

        // 4. Создать статистику недели
        val stats = createWeekStats(weekStart, reviews, analyses)
        logger.info("Created week stats: total=${stats.totalReviews}, positive=${stats.positiveCount}, negative=${stats.negativeCount}")

        // 5. Получить предыдущую неделю из БД
        val previousWeek = agent.getPreviousWeekAnalysis(weekStart)

        // 6. Сравнить недели (если есть предыдущая)
        if (previousWeek != null) {
            logger.info("Comparing with previous week: ${previousWeek.weekStart}")
            val comparison = agent.compareWeeks(stats, previousWeek)
            logger.info("Comparison completed: ${comparison.improvements.size} improvements, ${comparison.degradations.size} degradations")

            // 7. Сгенерировать отчёт
            val report = agent.generateReport(comparison)
            logger.info("Report generated (${report.length} chars)")
        } else {
            logger.info("No previous week found for comparison")
        }

        // 8. Сохранить анализ в БД
        val analysisSaved = agent.saveWeekAnalysis(weekStart, stats)
        if (!analysisSaved) {
            logger.warn("Failed to save week analysis to database")
        }

        return stats
    }

    /**
     * Вычисляет начало и конец недели
     * Неделя начинается с понедельника
     */
    private fun calculateWeekDates(dateStr: String?): Pair<String, String> {
        val date = if (dateStr != null) {
            LocalDate.parse(dateStr, dateFormatter)
        } else {
            LocalDate.now()
        }

        // Находим понедельник текущей недели
        val monday = date.with(DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)

        return Pair(
            monday.format(dateFormatter),
            sunday.format(dateFormatter)
        )
    }

    /**
     * Создает статистику недели из отзывов и анализов
     */
    private fun createWeekStats(
        weekStart: String,
        reviews: List<Review>,
        analyses: List<ReviewAnalysis>
    ): WeekStats {
        // Создаем мапу для быстрого поиска анализа по reviewId
        val analysesMap = analyses.associateBy { it.reviewId }

        // Подсчитываем категории
        var positiveCount = 0
        var negativeCount = 0
        var neutralCount = 0

        analyses.forEach { analysis ->
            when (analysis.category) {
                ReviewCategory.POSITIVE -> positiveCount++
                ReviewCategory.NEGATIVE -> negativeCount++
                ReviewCategory.NEUTRAL -> neutralCount++
            }
        }

        // Если анализов меньше, чем отзывов, считаем по рейтингу
        if (analyses.size < reviews.size) {
            reviews.forEach { review ->
                if (analysesMap[review.id] == null) {
                    when {
                        review.rating >= 4 -> positiveCount++
                        review.rating <= 2 -> negativeCount++
                        else -> neutralCount++
                    }
                }
            }
        }

        // Вычисляем средний рейтинг
        val averageRating = if (reviews.isNotEmpty()) {
            reviews.map { it.rating }.average()
        } else {
            0.0
        }

        return WeekStats(
            weekStart = weekStart,
            totalReviews = reviews.size,
            positiveCount = positiveCount,
            negativeCount = negativeCount,
            neutralCount = neutralCount,
            averageRating = averageRating,
            analyses = analyses
        )
    }

    /**
     * Создает пустую статистику для недели без отзывов
     */
    private fun createEmptyWeekStats(weekStart: String): WeekStats {
        return WeekStats(
            weekStart = weekStart,
            totalReviews = 0,
            positiveCount = 0,
            negativeCount = 0,
            neutralCount = 0,
            averageRating = 0.0,
            analyses = emptyList()
        )
    }

    /**
     * Получает анализ недели из БД
     */
    fun getWeekAnalysis(weekStart: String): WeekStats? {
        logger.debug("Getting week analysis for week $weekStart")
        return agent.getWeekAnalysis(weekStart)
    }
}

