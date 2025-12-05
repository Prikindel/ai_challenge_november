package com.prike.data.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Таблица для хранения отзывов
 */
object ReviewsTable : Table("reviews") {
    val id = text("id")
    val text = text("text")
    val rating = integer("rating")
    val date = text("date")
    val weekStart = text("week_start").index()
    val createdAt = text("created_at").default(Instant.now().toString())
    
    override val primaryKey = PrimaryKey(id)
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
            ReviewsTable,
            WeekAnalysesTable
        )
    }
}
