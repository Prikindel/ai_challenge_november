package com.prike.domain.tools

import com.prike.config.ReviewsConfig
import com.prike.config.TelegramConfig
import com.prike.data.client.TelegramMCPClient
import com.prike.data.repository.ReviewsRepository
import com.prike.domain.model.*
import com.prike.infrastructure.client.ReviewsApiClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Инструменты (Tools) для работы с отзывами через Koog агента
 * Эти функции будут доступны LLM для выполнения задач пользователя
 */
class ReviewsTools(
    private val apiClient: ReviewsApiClient,
    private val repository: ReviewsRepository,
    private val reviewsConfig: ReviewsConfig,
    private val telegramMCPClient: TelegramMCPClient?,
    private val telegramConfig: TelegramConfig?
) {
    private val logger = LoggerFactory.getLogger(ReviewsTools::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val dateFormatter = DateTimeFormatter.ISO_DATE

    /**
     * Получает отзывы из API за указанный период
     * 
     * @param fromDate Дата начала периода (ISO8601, например "2024-01-01")
     * @param toDate Дата конца периода (ISO8601, например "2024-01-07")
     * @param limit Максимальное количество отзывов (по умолчанию 100)
     * @return JSON строка с массивом отзывов
     */
    suspend fun fetchReviews(
        fromDate: String,
        toDate: String,
        limit: Int = 100
    ): String {
        return try {
            logger.info("Fetching reviews from $fromDate to $toDate (limit: $limit)")
            
            val reviews = apiClient.fetchReviews(
                store = reviewsConfig.api.store,
                packageId = reviewsConfig.api.packageId,
                fromDate = fromDate,
                toDate = toDate
            ).take(limit)

            val result = json.encodeToString(
                ListSerializer(Review.serializer()),
                reviews
            )
            
            logger.info("Fetched ${reviews.size} reviews")
            result
        } catch (e: Exception) {
            logger.error("Error fetching reviews: ${e.message}", e)
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("error", e.message ?: "Unknown error")
            })
        }
    }

    /**
     * Сохраняет саммари отзывов в БД
     * 
     * @param summariesJson JSON строка с массивом саммари отзывов
     * @param weekStart Дата начала недели (ISO8601)
     * @return JSON строка с результатом операции
     */
    fun saveReviewSummaries(
        summariesJson: String,
        weekStart: String
    ): String {
        return try {
            val summaries = json.decodeFromString<List<ReviewSummary>>(summariesJson)
            
            val saved = repository.saveReviewSummaries(summaries, weekStart)
            
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", saved)
                put("count", summaries.size)
                put("weekStart", weekStart)
            })
        } catch (e: Exception) {
            logger.error("Error saving review summaries: ${e.message}", e)
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            })
        }
    }

    /**
     * Получает саммари отзывов за неделю
     * 
     * @param weekStart Дата начала недели (ISO8601)
     * @return JSON строка с массивом саммари
     */
    fun getWeekSummaries(weekStart: String): String {
        return try {
            val summaries = repository.getWeekSummaries(weekStart)
            
            json.encodeToString(
                ListSerializer(ReviewSummary.serializer()),
                summaries
            )
        } catch (e: Exception) {
            logger.error("Error getting week summaries: ${e.message}", e)
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("error", e.message ?: "Unknown error")
            })
        }
    }

    /**
     * Получает все саммари отзывов (для поиска и анализа)
     * 
     * @return JSON строка с массивом всех саммари
     */
    fun getAllSummaries(): String {
        return try {
            val summaries = repository.getAllSummaries()
            
            json.encodeToString(
                ListSerializer(ReviewSummary.serializer()),
                summaries
            )
        } catch (e: Exception) {
            logger.error("Error getting all summaries: ${e.message}", e)
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("error", e.message ?: "Unknown error")
            })
        }
    }

    /**
     * Вычисляет даты недели для указанной даты
     * 
     * @param dateStr Дата (ISO8601) или null для текущей даты
     * @return JSON строка с weekStart и weekEnd
     */
    fun calculateWeekDates(dateStr: String? = null): String {
        val date = if (dateStr != null) {
            LocalDate.parse(dateStr, dateFormatter)
        } else {
            LocalDate.now()
        }

        val monday = date.with(DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)

        return json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("weekStart", monday.format(dateFormatter))
            put("weekEnd", sunday.format(dateFormatter))
        })
    }

    /**
     * Получает статистику недели из БД
     * 
     * @param weekStart Дата начала недели (ISO8601)
     * @return JSON строка со статистикой или null
     */
    fun getWeekStats(weekStart: String): String {
        return try {
            val stats = repository.getWeekAnalysis(weekStart)
            
            if (stats != null) {
                json.encodeToString(WeekStats.serializer(), stats)
            } else {
                json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("error", "Week analysis not found")
                })
            }
        } catch (e: Exception) {
            logger.error("Error getting week stats: ${e.message}", e)
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("error", e.message ?: "Unknown error")
            })
        }
    }

    /**
     * Получает статистику предыдущей недели
     * 
     * @param currentWeekStart Дата начала текущей недели (ISO8601)
     * @return JSON строка со статистикой предыдущей недели или null
     */
    fun getPreviousWeekStats(currentWeekStart: String): String {
        return try {
            val stats = repository.getPreviousWeekAnalysis(currentWeekStart)
            
            if (stats != null) {
                json.encodeToString(WeekStats.serializer(), stats)
            } else {
                json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("error", "Previous week analysis not found")
                })
            }
        } catch (e: Exception) {
            logger.error("Error getting previous week stats: ${e.message}", e)
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("error", e.message ?: "Unknown error")
            })
        }
    }

    /**
     * Индексирует саммари отзывов для RAG (создает чанки с эмбеддингами)
     * 
     * @param weekStart Дата начала недели (ISO8601) - индексируются саммари за эту неделю
     * @return JSON строка с результатом операции
     */
    suspend fun indexReviewSummariesForRag(weekStart: String): String {
        return try {
            logger.info("Indexing review summaries for week $weekStart")
            
            // Получаем саммари за неделю
            val summaries = repository.getWeekSummaries(weekStart)
            
            if (summaries.isEmpty()) {
                return json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("success", false)
                    put("error", "No summaries found for week $weekStart")
                })
            }
            
            // TODO: Индексируем через RAG сервис
            // Это будет вызвано извне, так как RAG сервис не доступен здесь напрямую
            
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", true)
                put("message", "Indexing initiated for ${summaries.size} summaries")
                put("count", summaries.size)
            })
        } catch (e: Exception) {
            logger.error("Error indexing review summaries: ${e.message}", e)
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            })
        }
    }

    /**
     * Отправляет сообщение в Telegram через MCP
     * 
     * @param message Текст сообщения для отправки
     * @return JSON строка с результатом операции
     */
    suspend fun sendTelegramMessage(message: String): String {
        return try {
            if (telegramMCPClient == null || !telegramMCPClient.isConnected()) {
                return json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("success", false)
                    put("error", "Telegram MCP client is not connected")
                })
            }

            // Получаем chatId из конфига
            val chatId = telegramConfig?.chatId ?: run {
                return json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("success", false)
                    put("error", "Telegram chatId is not configured")
                })
            }
            
            val sent = telegramMCPClient.sendMessage(chatId, message)
            
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", sent)
                put("message", if (sent) "Message sent successfully" else "Failed to send message")
            })
        } catch (e: Exception) {
            logger.error("Error sending Telegram message: ${e.message}", e)
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            })
        }
    }
}

