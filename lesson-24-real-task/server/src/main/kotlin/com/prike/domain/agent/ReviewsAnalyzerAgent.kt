package com.prike.domain.agent

import com.prike.config.KoogConfig
import com.prike.config.ReviewsConfig
import com.prike.infrastructure.client.ReviewsApiClient
import com.prike.domain.model.Review

/**
 * Koog агент для анализа отзывов
 */
class ReviewsAnalyzerAgent(
    private val koogConfig: KoogConfig,
    private val reviewsConfig: ReviewsConfig,
    private val apiClient: ReviewsApiClient
) {
    /**
     * Koog инструмент для сбора отзывов за период с сервера Company Mobile Stores
     * 
     * TODO: Добавить @Tool аннотацию после настройки Koog в следующих коммитах
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
}
