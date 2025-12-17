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
 * Таблица для хранения чанков саммари отзывов с эмбеддингами для RAG
 */
object ReviewSummaryChunksTable : Table("review_summary_chunks") {
    val id = text("id")
    val reviewId = text("review_id").index() // Ссылка на review_summaries.review_id
    val chunkIndex = integer("chunk_index")
    val content = text("content")
    val embedding = text("embedding") // JSON массив чисел (вектор эмбеддинга)
    val weekStart = text("week_start").index()
    val createdAt = text("created_at").default(Instant.now().toString())
    
    override val primaryKey = PrimaryKey(id)
}

/**
 * Таблица для хранения чанков базы знаний с эмбеддингами для RAG
 */
object KnowledgeBaseChunksTable : Table("knowledge_base_chunks") {
    val id = text("id")
    val documentId = text("document_id").index()
    val chunkIndex = integer("chunk_index")
    val content = text("content")
    val embedding = text("embedding") // JSON массив чисел (вектор эмбеддинга)
    val category = text("category").index() // PROJECTS, LEARNING, PERSONAL, REFERENCES
    val sourcePath = text("source_path") // Путь к файлу
    val startIndex = integer("start_index").default(0)
    val endIndex = integer("end_index").default(0)
    val indexedAt = text("indexed_at").default(Instant.now().toString())
    
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
            ChatMessagesTable,
            ReviewSummaryChunksTable,
            InteractionHistoryTable,
            KnowledgeBaseChunksTable
        )
    }
}
