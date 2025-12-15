package com.prike.data.repository

import com.prike.domain.model.Feedback
import com.prike.domain.model.InteractionHistory
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import org.jetbrains.exposed.sql.Database

/**
 * Репозиторий для работы с историей взаимодействий пользователя
 */
interface InteractionHistoryRepository {
    suspend fun saveInteraction(history: InteractionHistory)
    suspend fun getRecentInteractions(userId: String, limit: Int = 10): List<InteractionHistory>
    suspend fun getFeedback(userId: String): List<Feedback>
}

/**
 * Реализация репозитория истории взаимодействий на основе SQLite
 */
class SQLiteInteractionHistoryRepository(
    private val database: Database
) : InteractionHistoryRepository {
    private val logger = LoggerFactory.getLogger(SQLiteInteractionHistoryRepository::class.java)
    
    init {
        // Создаем таблицу при инициализации
        transaction(database) {
            SchemaUtils.create(InteractionHistoryTable)
        }
        logger.info("InteractionHistory table initialized")
    }
    
    override suspend fun saveInteraction(history: InteractionHistory) {
        transaction(database) {
            InteractionHistoryTable.insert {
                it[userId] = history.userId
                it[timestamp] = history.timestamp
                it[question] = history.question
                it[answer] = history.answer
                it[rating] = history.feedback?.rating
                it[feedbackComment] = history.feedback?.comment
            }
        }
        logger.debug("Interaction saved for user: ${history.userId}")
    }
    
    override suspend fun getRecentInteractions(userId: String, limit: Int): List<InteractionHistory> {
        return transaction(database) {
            InteractionHistoryTable
                .select { InteractionHistoryTable.userId eq userId }
                .orderBy(InteractionHistoryTable.timestamp to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    InteractionHistory(
                        userId = row[InteractionHistoryTable.userId],
                        timestamp = row[InteractionHistoryTable.timestamp],
                        question = row[InteractionHistoryTable.question],
                        answer = row[InteractionHistoryTable.answer],
                        feedback = row[InteractionHistoryTable.rating]?.let { rating ->
                            Feedback(
                                rating = rating,
                                comment = row[InteractionHistoryTable.feedbackComment]
                            )
                        }
                    )
                }
        }
    }
    
    override suspend fun getFeedback(userId: String): List<Feedback> {
        return transaction(database) {
            InteractionHistoryTable
                .select { (InteractionHistoryTable.userId eq userId) and (InteractionHistoryTable.rating.isNotNull()) }
                .orderBy(InteractionHistoryTable.timestamp to SortOrder.DESC)
                .mapNotNull { row ->
                    row[InteractionHistoryTable.rating]?.let { rating ->
                        Feedback(
                            rating = rating,
                            comment = row[InteractionHistoryTable.feedbackComment]
                        )
                    }
                }
        }
    }
}

/**
 * Таблица для хранения истории взаимодействий
 */
object InteractionHistoryTable : IntIdTable("interaction_history") {
    val userId = varchar("user_id", 255).index()
    val timestamp = long("timestamp")
    val question = text("question")
    val answer = text("answer")
    val rating = integer("rating").nullable()
    val feedbackComment = text("feedback_comment").nullable()
}

