package com.prike.domain.agent

import ai.koog.agents.core.agent.AIAgent
import com.prike.config.ReviewsConfig
import com.prike.data.repository.ReviewsRepository
import com.prike.domain.model.*
import com.prike.infrastructure.client.ReviewsApiClient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Koog агент для анализа отзывов
 */
class ReviewsAnalyzerAgent(
    private val koogAgent: AIAgent<String, String>,
    private val reviewsConfig: ReviewsConfig,
    private val apiClient: ReviewsApiClient,
    private val repository: ReviewsRepository
) {
    private val logger = LoggerFactory.getLogger(ReviewsAnalyzerAgent::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    /**
     * Сбор отзывов за период с сервера Company Mobile Stores
     */
    suspend fun fetchReviews(
        fromDate: String,
        toDate: String
    ): List<Review> {
        logger.info("Fetching reviews from $fromDate to $toDate")
        return apiClient.fetchReviews(
            store = reviewsConfig.api.store,
            packageId = reviewsConfig.api.packageId,
            fromDate = fromDate,
            toDate = toDate
        )
    }

    /**
     * Классификация отзывов через LLM
     */
    suspend fun classifyReviews(
        reviews: List<Review>
    ): List<ReviewAnalysis> {
        logger.info("Classifying ${reviews.size} reviews via LLM")
        
        if (reviews.isEmpty()) return emptyList()

        val reviewsJson = json.encodeToString(
            ListSerializer(Review.serializer()),
            reviews
        )

        val prompt = """
            Проанализируй следующие отзывы и классифицируй каждый из них.
            
            Для каждого отзыва определи:
            1. category: POSITIVE, NEGATIVE или NEUTRAL
            2. topics: список тем/проблем (например, ["UI", "Performance", "Bugs"])
            3. criticality: HIGH, MEDIUM или LOW (для негативных отзывов)
            
            Верни результат в формате JSON массива:
            [
              {
                "reviewId": "id1",
                "category": "NEGATIVE",
                "topics": ["UI", "Bugs"],
                "criticality": "HIGH"
              },
              ...
            ]
            
            Отзывы:
            $reviewsJson
        """.trimIndent()

        return try {
            val result = kotlinx.coroutines.runBlocking {
                koogAgent.run(prompt)
            }
            logger.debug("LLM classification result: $result")
            
            // Парсим JSON ответ от LLM
            val jsonElement = json.parseToJsonElement(result)
            val analysesArray = jsonElement.jsonArray
            
            analysesArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    ReviewAnalysis(
                        reviewId = obj["reviewId"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        category = ReviewCategory.valueOf(
                            obj["category"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        ),
                        topics = obj["topics"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        criticality = Criticality.valueOf(
                            obj["criticality"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        )
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to parse review analysis: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Error classifying reviews via LLM: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Сравнение недель через LLM
     */
    suspend fun compareWeeks(
        currentWeek: WeekStats,
        previousWeek: WeekStats
    ): WeekComparison {
        logger.info("Comparing weeks: ${currentWeek.weekStart} vs ${previousWeek.weekStart}")

        val currentWeekJson = json.encodeToString(WeekStats.serializer(), currentWeek)
        val previousWeekJson = json.encodeToString(WeekStats.serializer(), previousWeek)

        val prompt = """
            Сравни статистику двух недель и определи:
            1. Улучшения (improvements) - метрики, которые улучшились
            2. Ухудшения (degradations) - метрики, которые ухудшились
            3. Новые проблемы (newIssues) - темы, которые появились в текущей неделе
            4. Решенные проблемы (resolvedIssues) - темы, которые были в предыдущей неделе, но исчезли в текущей
            
            Верни результат в формате JSON:
            {
              "improvements": [
                {
                  "metric": "averageRating",
                  "change": "+0.5",
                  "reason": "Улучшение качества приложения"
                }
              ],
              "degradations": [
                {
                  "metric": "negativeCount",
                  "change": "+10",
                  "reason": "Увеличение негативных отзывов"
                }
              ],
              "newIssues": ["UI проблемы", "Производительность"],
              "resolvedIssues": ["Баг с авторизацией"]
            }
            
            Текущая неделя:
            $currentWeekJson
            
            Предыдущая неделя:
            $previousWeekJson
        """.trimIndent()

        return try {
            val result = kotlinx.coroutines.runBlocking {
                koogAgent.run(prompt)
            }
            logger.debug("LLM comparison result: $result")
            
            val jsonElement = json.parseToJsonElement(result)
            val obj = jsonElement.jsonObject
            
            WeekComparison(
                currentWeek = currentWeek,
                previousWeek = previousWeek,
                improvements = obj["improvements"]?.jsonArray?.mapNotNull { element ->
                    try {
                        val impObj = element.jsonObject
                        Improvement(
                            metric = impObj["metric"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            change = impObj["change"]?.jsonPrimitive?.content ?: "",
                            reason = impObj["reason"]?.jsonPrimitive?.content ?: ""
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to parse improvement: ${e.message}")
                        null
                    }
                } ?: emptyList(),
                degradations = obj["degradations"]?.jsonArray?.mapNotNull { element ->
                    try {
                        val degObj = element.jsonObject
                        Degradation(
                            metric = degObj["metric"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            change = degObj["change"]?.jsonPrimitive?.content ?: "",
                            reason = degObj["reason"]?.jsonPrimitive?.content ?: ""
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to parse degradation: ${e.message}")
                        null
                    }
                } ?: emptyList(),
                newIssues = obj["newIssues"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                resolvedIssues = obj["resolvedIssues"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            )
        } catch (e: Exception) {
            logger.error("Error comparing weeks via LLM: ${e.message}", e)
            // Возвращаем базовое сравнение при ошибке
            WeekComparison(
                currentWeek = currentWeek,
                previousWeek = previousWeek,
                improvements = emptyList(),
                degradations = emptyList(),
                newIssues = emptyList(),
                resolvedIssues = emptyList()
            )
        }
    }

    /**
     * Генерация текстового отчета о сравнении недель
     */
    suspend fun generateReport(
        comparison: WeekComparison
    ): String {
        logger.info("Generating report for week comparison")

        val comparisonJson = json.encodeToString(WeekComparison.serializer(), comparison)

        val prompt = """
            На основе сравнения двух недель сгенерируй структурированный текстовый отчет на русском языке.
            
            Отчет должен включать:
            1. Краткое резюме изменений
            2. Основные улучшения
            3. Основные проблемы и ухудшения
            4. Новые проблемы, требующие внимания
            5. Решенные проблемы
            6. Рекомендации на следующую неделю
            
            Будь конкретным и используй данные из сравнения.
            
            Данные сравнения:
            $comparisonJson
        """.trimIndent()

        return try {
            val result = kotlinx.coroutines.runBlocking {
                koogAgent.run(prompt)
            }
            logger.debug("Generated report: $result")
            result
        } catch (e: Exception) {
            logger.error("Error generating report via LLM: ${e.message}", e)
            "Ошибка при генерации отчета: ${e.message}"
        }
    }

    /**
     * Сохранение саммари отзывов в локальную БД
     */
    fun saveReviewSummaries(
        summaries: List<ReviewSummary>,
        weekStart: String
    ): Boolean {
        logger.info("Saving ${summaries.size} review summaries for week $weekStart")
        return repository.saveReviewSummaries(summaries, weekStart)
    }

    /**
     * Получение саммари отзывов за неделю из БД
     */
    fun getWeekSummaries(
        weekStart: String
    ): List<ReviewSummary> {
        logger.debug("Getting review summaries for week $weekStart")
        return repository.getWeekSummaries(weekStart)
    }

    /**
     * Сохранение анализа недели в БД
     */
    fun saveWeekAnalysis(
        weekStart: String,
        stats: WeekStats
    ): Boolean {
        logger.info("Saving week analysis for week $weekStart")
        return repository.saveWeekAnalysis(weekStart, stats)
    }

    /**
     * Получение анализа предыдущей недели из БД
     */
    fun getPreviousWeekAnalysis(
        currentWeekStart: String
    ): WeekStats? {
        logger.debug("Getting previous week analysis for current week $currentWeekStart")
        return repository.getPreviousWeekAnalysis(currentWeekStart)
    }

    /**
     * Получение анализа недели из БД
     */
    fun getWeekAnalysis(
        weekStart: String
    ): WeekStats? {
        logger.debug("Getting week analysis for week $weekStart")
        return repository.getWeekAnalysis(weekStart)
    }
}
