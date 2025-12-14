package com.prike.data.repository

import com.prike.domain.model.DataRecord
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Репозиторий для работы с данными
 * Использует БД из урока 24 (отзывы) через ReviewsDataAdapter
 */
class DataRepository(
    private val database: org.jetbrains.exposed.sql.Database
) {
    private val reviewsAdapter = ReviewsDataAdapter(database)
    private val logger = LoggerFactory.getLogger(DataRepository::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Сохранение записей отключено - используется БД из урока 24
     */
    fun saveRecords(records: List<DataRecord>): Boolean {
        logger.warn("saveRecords called but data loading is disabled. Using existing DB from lesson 24.")
        return false
    }
    
    /**
     * Получает записи данных с ограничением
     * Использует данные из БД урока 24 (отзывы)
     */
    fun getRecords(limit: Int = 1000): List<DataRecord> {
        return reviewsAdapter.getAllReviewsAsDataRecords(limit)
    }
    
    /**
     * Получает записи данных по источнику
     * Для "reviews" - использует БД урока 24
     * Для других источников - пустой список (загрузка отключена)
     */
    fun getRecordsBySource(source: String, limit: Int = 1000): List<DataRecord> {
        return when (source) {
            "reviews" -> reviewsAdapter.getAllReviewsAsDataRecords(limit)
            "positive" -> reviewsAdapter.getReviewsByCategory("POSITIVE", limit)
            "negative" -> reviewsAdapter.getReviewsByCategory("NEGATIVE", limit)
            "neutral" -> reviewsAdapter.getReviewsByCategory("NEUTRAL", limit)
            else -> {
                logger.warn("Unknown source: $source, returning empty list")
                emptyList()
            }
        }
    }
    
    /**
     * Получает количество записей по источнику
     */
    fun getRecordsCountBySource(source: String): Int {
        return when (source) {
            "reviews" -> reviewsAdapter.getReviewsCount()
            "positive" -> reviewsAdapter.getReviewsByCategory("POSITIVE", Int.MAX_VALUE).size
            "negative" -> reviewsAdapter.getReviewsByCategory("NEGATIVE", Int.MAX_VALUE).size
            "neutral" -> reviewsAdapter.getReviewsByCategory("NEUTRAL", Int.MAX_VALUE).size
            else -> 0
        }
    }
    
    /**
     * Получает общее количество записей
     */
    fun getTotalRecordsCount(): Int {
        return reviewsAdapter.getReviewsCount()
    }
}
