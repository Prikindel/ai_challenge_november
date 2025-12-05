package com.prike.domain.agent

import com.prike.config.KoogConfig
import com.prike.config.ReviewsConfig
import com.prike.domain.model.*
import com.prike.infrastructure.client.ReviewsApiClient
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Koog агент для анализа отзывов
 */
class ReviewsAnalyzerAgent(
    private val koogConfig: KoogConfig,
    private val reviewsConfig: ReviewsConfig,
    private val apiClient: ReviewsApiClient
) {
    private val logger = LoggerFactory.getLogger(ReviewsAnalyzerAgent::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Koog инструмент для сбора отзывов за период с сервера Company Mobile Stores
     * 
     * TODO: Добавить @Tool аннотацию после настройки Koog
     */
    suspend fun fetchReviews(
        fromDate: String,
        toDate: String
    ): List<Review> {
        return apiClient.fetchReviews(
            store = reviewsConfig.api.store,
            packageId = reviewsConfig.api.packageId,
            fromDate = fromDate,
            toDate = toDate
        )
    }

    /**
     * Классификация отзывов через LLM
     * 
     * TODO: Добавить @Tool аннотацию и реализовать через Koog AIAgent
     */
    suspend fun classifyReviews(
        reviews: List<Review>
    ): List<ReviewAnalysis> {
        logger.info("Classifying ${reviews.size} reviews via LLM")
        
        // TODO: Реализовать через Koog AIAgent
        // Структура будет:
        // 1. Создать промпт для классификации
        // 2. Вызвать koog.ask() или agent.run()
        // 3. Распарсить JSON ответ
        // 4. Вернуть List<ReviewAnalysis>
        
        // Пока возвращаем пустой список - будет реализовано в следующих коммитах
        return emptyList()
    }

    /**
     * Сравнение недель через LLM
     * 
     * TODO: Добавить @Tool аннотацию и реализовать через Koog AIAgent
     */
    suspend fun compareWeeks(
        currentWeek: WeekStats,
        previousWeek: WeekStats
    ): WeekComparison {
        logger.info("Comparing weeks: ${currentWeek.weekStart} vs ${previousWeek.weekStart}")
        
        // TODO: Реализовать через Koog AIAgent
        // Структура будет:
        // 1. Создать промпт для сравнения
        // 2. Вызвать koog.ask() или agent.run()
        // 3. Распарсить JSON ответ
        // 4. Вернуть WeekComparison с improvements/degradations
        
        // Пока возвращаем базовое сравнение - будет реализовано в следующих коммитах
        return WeekComparison(
            currentWeek = currentWeek,
            previousWeek = previousWeek,
            improvements = emptyList(),
            degradations = emptyList(),
            newIssues = emptyList(),
            resolvedIssues = emptyList()
        )
    }
}
