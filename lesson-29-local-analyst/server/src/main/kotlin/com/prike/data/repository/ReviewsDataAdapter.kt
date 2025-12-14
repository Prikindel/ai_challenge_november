package com.prike.data.repository

import com.prike.domain.model.DataRecord
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Адаптер для преобразования данных из БД урока 24 (отзывы) в DataRecord
 */
class ReviewsDataAdapter(
    private val database: org.jetbrains.exposed.sql.Database
) {
    private val logger = LoggerFactory.getLogger(ReviewsDataAdapter::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Получает все отзывы из БД урока 24 и преобразует в DataRecord
     */
    fun getAllReviewsAsDataRecords(limit: Int = 1000): List<DataRecord> {
        return try {
            transaction(database) {
                ReviewSummariesTable
                    .selectAll()
                    .orderBy(ReviewSummariesTable.date to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        // Преобразуем данные отзыва в Map для DataRecord
                        val dataMap = mutableMapOf<String, String>()
                        dataMap["reviewId"] = row[ReviewSummariesTable.reviewId]
                        dataMap["rating"] = row[ReviewSummariesTable.rating].toString()
                        dataMap["date"] = row[ReviewSummariesTable.date]
                        dataMap["summary"] = row[ReviewSummariesTable.summary]
                        dataMap["category"] = row[ReviewSummariesTable.category]
                        dataMap["topics"] = row[ReviewSummariesTable.topics]
                        dataMap["criticality"] = row[ReviewSummariesTable.criticality]
                        dataMap["weekStart"] = row[ReviewSummariesTable.weekStart]
                        
                        DataRecord(
                            id = row[ReviewSummariesTable.reviewId],
                            source = "reviews",  // Источник - отзывы из урока 24
                            sourceFile = "lesson-24-db",
                            data = dataMap,
                            timestamp = System.currentTimeMillis() // Используем текущее время
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting reviews as data records: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Получает отзывы по категории
     */
    fun getReviewsByCategory(category: String, limit: Int = 1000): List<DataRecord> {
        return try {
            transaction(database) {
                ReviewSummariesTable
                    .select { ReviewSummariesTable.category eq category }
                    .orderBy(ReviewSummariesTable.date to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        val dataMap = mutableMapOf<String, String>()
                        dataMap["reviewId"] = row[ReviewSummariesTable.reviewId]
                        dataMap["rating"] = row[ReviewSummariesTable.rating].toString()
                        dataMap["date"] = row[ReviewSummariesTable.date]
                        dataMap["summary"] = row[ReviewSummariesTable.summary]
                        dataMap["category"] = row[ReviewSummariesTable.category]
                        dataMap["topics"] = row[ReviewSummariesTable.topics]
                        dataMap["criticality"] = row[ReviewSummariesTable.criticality]
                        dataMap["weekStart"] = row[ReviewSummariesTable.weekStart]
                        
                        DataRecord(
                            id = row[ReviewSummariesTable.reviewId],
                            source = "reviews",
                            sourceFile = "lesson-24-db",
                            data = dataMap,
                            timestamp = System.currentTimeMillis()
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting reviews by category: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Получает количество отзывов
     */
    fun getReviewsCount(): Int {
        return try {
            transaction(database) {
                ReviewSummariesTable
                    .selectAll()
                    .count()
                    .toInt()
            }
        } catch (e: Exception) {
            logger.error("Error getting reviews count: ${e.message}", e)
            0
        }
    }
    
    /**
     * Получает статистику по категориям
     */
    fun getCategoryStats(): Map<String, Int> {
        return try {
            transaction(database) {
                ReviewSummariesTable
                    .selectAll()
                    .map { it[ReviewSummariesTable.category] }
                    .groupingBy { it }
                    .eachCount()
            }
        } catch (e: Exception) {
            logger.error("Error getting category stats: ${e.message}", e)
            emptyMap()
        }
    }
}
