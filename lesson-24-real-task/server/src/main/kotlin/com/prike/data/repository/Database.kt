package com.prike.data.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Таблица для хранения саммари отзывов (вместо полных отзывов)
 */
object ReviewSummariesTable : Table("review_summaries") {
    val reviewId = text("review_id")
    val rating = integer("rating")
    val date = text("date")
    val summary = text("summary") // Краткое саммари отзыва
    val category = text("category") // POSITIVE, NEGATIVE, NEUTRAL
    val topics = text("topics") // JSON массив категорий
    val criticality = text("criticality") // HIGH, MEDIUM, LOW
    val weekStart = text("week_start").index()
    val createdAt = text("created_at").default(Instant.now().toString())
    
    override val primaryKey = PrimaryKey(reviewId)
}

/**
 * Таблица для хранения анализов недель
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
    val createdAt = text("created_at").default(Instant.now().toString())
    
    override val primaryKey = PrimaryKey(id)
}

/**
 * Инициализация схемы БД
 */
fun initDatabase(connection: Database) {
    transaction(connection) {
        SchemaUtils.createMissingTablesAndColumns(
            ReviewSummariesTable,
            WeekAnalysesTable,
            ChatSessionsTable,
            ChatMessagesTable
        )
    }
}
