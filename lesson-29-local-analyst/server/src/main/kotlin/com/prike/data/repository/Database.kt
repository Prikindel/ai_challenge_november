package com.prike.data.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Таблица для хранения саммари отзывов из урока 24
 */
object ReviewSummariesTable : Table("review_summaries") {
    val reviewId = text("review_id")
    val rating = integer("rating")
    val date = text("date")
    val summary = text("summary")
    val category = text("category")
    val topics = text("topics")
    val criticality = text("criticality")
    val weekStart = text("week_start").index()
    val createdAt = text("created_at")
    
    override val primaryKey = PrimaryKey(reviewId)
}

/**
 * Таблица для хранения анализов недель из урока 24
 */
object WeekAnalysesTable : Table("week_analyses") {
    val id = integer("id").autoIncrement()
    val weekStart = text("week_start").uniqueIndex()
    val totalReviews = integer("total_reviews")
    val positiveCount = integer("positive_count")
    val negativeCount = integer("negative_count")
    val neutralCount = integer("neutral_count")
    val averageRating = double("average_rating")
    val analysisJson = text("analysis_json")
    val createdAt = text("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

/**
 * Инициализация схемы БД (проверка существования таблиц)
 */
fun initDatabase(connection: Database) {
    transaction(connection) {
        // Проверяем, что таблицы существуют (не создаем новые, используем существующие из урока 24)
        SchemaUtils.createMissingTablesAndColumns(
            ReviewSummariesTable,
            WeekAnalysesTable
        )
    }
}
