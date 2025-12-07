package com.prike.domain.tools

import ai.koog.prompt.executor.clients.LLMClientException
import com.prike.config.ReviewsConfig
import com.prike.config.TelegramConfig
import com.prike.data.client.TelegramMCPClient
import com.prike.data.repository.ReviewsRepository
import com.prike.domain.model.*
import com.prike.domain.service.KoogAgentService
import com.prike.domain.service.ReviewSummaryRagService
import com.prike.infrastructure.client.ReviewsApiClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
    private val telegramConfig: TelegramConfig?,
    private val reviewSummaryRagService: ReviewSummaryRagService,
    private val koogAgentService: KoogAgentService? = null // Для анализа через LLM (опционально, чтобы избежать циклической зависимости)
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
            logger.info("Fetching reviews: $fromDate to $toDate (limit: $limit)")
            
            // Если limit большой (>= 50), используем пагинацию по 50
            // Если limit >= 500, считаем что нужно получить все отзывы (maxResults = null)
            // Если limit маленький, используем его как pageSize
            val pageSize = if (limit >= 50) 50 else limit
            val maxResults = when {
                limit >= 500 -> null // Получаем все отзывы через пагинацию
                limit > 0 -> limit // Ограничиваем указанным количеством
                else -> null // Если limit = 0 или отрицательный, получаем все
            }
            
            val reviews = apiClient.fetchReviews(
                store = reviewsConfig.api.store,
                packageId = reviewsConfig.api.packageId,
                fromDate = fromDate,
                toDate = toDate,
                pageSize = pageSize,
                maxResults = maxResults
            )

            val result = json.encodeToString(
                ListSerializer(Review.serializer()),
                reviews
            )
            
            logger.info("✅ Fetched ${reviews.size} reviews")
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
            // Очищаем JSON от возможных артефактов LLM (markdown код блоки, лишний текст)
            var cleanedJson = summariesJson.trim()
            
            // Удаляем markdown код блоки, если есть
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.removePrefix("```json").trim()
            }
            if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.removePrefix("```").trim()
            }
            if (cleanedJson.endsWith("```")) {
                cleanedJson = cleanedJson.removeSuffix("```").trim()
            }
            
            // Ищем начало JSON массива
            val arrayStart = cleanedJson.indexOf('[')
            if (arrayStart > 0) {
                cleanedJson = cleanedJson.substring(arrayStart)
            }
            
            // Ищем конец JSON массива (последняя закрывающая скобка)
            val lastBracket = cleanedJson.lastIndexOf(']')
            if (lastBracket > 0 && lastBracket < cleanedJson.length - 1) {
                cleanedJson = cleanedJson.substring(0, lastBracket + 1)
            }
            
            logger.debug("Cleaned JSON (first 200 chars): ${cleanedJson.take(200)}")
            logger.debug("Cleaned JSON (last 200 chars): ${cleanedJson.takeLast(200)}")
            
            // Проверяем, что это валидный JSON
            val jsonElement = json.parseToJsonElement(cleanedJson)
            if (jsonElement !is JsonArray) {
                throw IllegalArgumentException("Expected JSON array, got: ${jsonElement::class.simpleName}")
            }
            
            val summaries = json.decodeFromString<List<ReviewSummary>>(cleanedJson)
            
            if (summaries.isEmpty()) {
                logger.warn("Empty summaries list provided")
                return json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("success", false)
                    put("error", "Empty summaries list")
                })
            }
            
            val saved = repository.saveReviewSummaries(summaries, weekStart)
            
            logger.info("✅ Saved ${summaries.size} review summaries for week $weekStart")
            
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", saved)
                put("count", summaries.size)
                put("weekStart", weekStart)
            })
        } catch (e: Exception) {
            @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
            val errorMsg = when (e) {
                is kotlinx.serialization.MissingFieldException -> {
                    "Invalid ReviewSummary format. Required fields: reviewId, rating, date, summary, category, topics (array of ReviewTopic enum values), criticality. Error: ${e.message}"
                }
                is kotlinx.serialization.SerializationException -> {
                    "Invalid JSON format. Make sure the JSON is complete and valid. Error: ${e.message}. JSON preview: ${summariesJson.take(200)}..."
                }
                else -> e.message ?: "Unknown error"
            }
            logger.error("Error saving review summaries: $errorMsg", e)
            logger.error("Problematic JSON (first 500 chars): ${summariesJson.take(500)}")
            logger.error("Problematic JSON (last 500 chars): ${summariesJson.takeLast(500)}")
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", false)
                put("error", errorMsg)
                put("hint", "ReviewSummary must be a valid JSON array. Each item must have: reviewId (string), rating (1-5), date (ISO8601), summary (string), category (POSITIVE/NEGATIVE/NEUTRAL), topics (array of ReviewTopic enum: AUTO_UPLOAD, ALBUMS, etc.), criticality (HIGH/MEDIUM/LOW)")
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
            logger.info("Getting week summaries for weekStart: $weekStart")
            val summaries = repository.getWeekSummaries(weekStart)
            logger.info("Found ${summaries.size} summaries for week $weekStart")
            
            if (summaries.isEmpty()) {
                // Если не найдено, попробуем получить все отзывы для информации
                val allSummaries = repository.getAllSummaries()
                logger.info("Total summaries in DB: ${allSummaries.size}")
                if (allSummaries.isNotEmpty()) {
                    // Показываем примеры weekStart из БД для отладки
                    val weekStartsInDb = allSummaries.mapNotNull { it.weekStart }.distinct().take(5)
                    logger.info("Example weekStart values in DB: $weekStartsInDb")
                }
            }
            
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
     * @param dateStr Дата (ISO8601, с временем или без) или null для текущей даты
     * @return JSON строка с weekStart и weekEnd
     */
    fun calculateWeekDates(dateStr: String? = null): String {
        val date = if (dateStr != null) {
            try {
                // Пробуем парсить как дату без времени (ISO_DATE)
                LocalDate.parse(dateStr, dateFormatter)
            } catch (e: Exception) {
                try {
                    // Если не получилось, пробуем парсить как дату с временем (ISO_DATE_TIME или ISO_INSTANT)
                    if (dateStr.contains("T")) {
                        // ISO8601 с временем: "2025-12-07T00:00:00Z" или "2025-12-07T00:00:00+00:00"
                        val instant = java.time.Instant.parse(dateStr)
                        LocalDate.ofInstant(instant, ZoneOffset.UTC)
                    } else {
                        // Пробуем ISO_DATE еще раз
                        LocalDate.parse(dateStr, dateFormatter)
                    }
                } catch (e2: Exception) {
                    logger.warn("Failed to parse date: $dateStr, using current date")
                    LocalDate.now()
                }
            }
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
        logger.info("Getting previous week stats for currentWeekStart: $currentWeekStart")
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
     * Получает текущую дату и время в формате ISO8601
     *
     * @return JSON строка с текущей датой и временем
     */
    fun getCurrentDate(): String {
        val now = java.time.Instant.now().atOffset(ZoneOffset.UTC)
        val dateStr = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val localDateStr = LocalDate.now().format(dateFormatter)
        
        return json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("currentDateTime", dateStr) // ISO8601 с временем
            put("currentDate", localDateStr) // Только дата
            put("timestamp", now.toEpochSecond())
        })
    }

    /**
     * Индексирует все саммари из БД в RAG
     *
     * @return JSON строка с результатом операции
     */
    suspend fun indexAllSummaries(): String {
        return try {
            if (reviewSummaryRagService == null) {
                return json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("success", false)
                    put("error", "RAG service is not available")
                })
            }

            val allSummaries = repository.getAllSummaries()
            if (allSummaries.isEmpty()) {
                return json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("success", false)
                    put("error", "No summaries found in database")
                })
            }

            logger.info("Starting RAG indexing for ${allSummaries.size} summaries")
            var indexed = 0
            var failed = 0

            allSummaries.forEach { summary ->
                try {
                    reviewSummaryRagService.indexSummary(summary)
                    indexed++
                    if (indexed % 50 == 0) {
                        logger.info("Indexed $indexed/${allSummaries.size} summaries...")
                    }
                    // Увеличиваем задержку между запросами, чтобы снизить нагрузку на БД
                    if (indexed < allSummaries.size) {
                        kotlinx.coroutines.delay(200) // 200ms задержка между запросами
                    }
                } catch (e: Exception) {
                    failed++
                    logger.warn("Failed to index summary ${summary.reviewId}: ${e.message}")
                    // При ошибке делаем большую задержку перед следующим запросом
                    kotlinx.coroutines.delay(2000) // 2 секунды задержка после ошибки
                }
            }

            logger.info("✅ RAG indexing completed: $indexed indexed, $failed failed")

            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", true)
                put("message", "RAG indexing completed")
                put("total", allSummaries.size)
                put("indexed", indexed)
                put("failed", failed)
            })
        } catch (e: Exception) {
            logger.error("Error indexing all summaries: ${e.message}", e)
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

    /**
     * Анализирует отзывы за период с автоматическим батчингом по дням
     * Разбивает период на дни, для каждого дня получает отзывы, анализирует через LLM и сохраняет в БД
     * 
     * @param fromDate Дата начала периода (ISO8601, например "2025-11-01")
     * @param toDate Дата конца периода (ISO8601, например "2025-12-07")
     * @return JSON строка с результатом анализа
     */
    suspend fun analyzePeriodByDays(
        fromDate: String,
        toDate: String
    ): String {
        return try {
            logger.info("Starting period analysis by days: $fromDate to $toDate")
            
            val startDate = LocalDate.parse(fromDate.substringBefore("T"), dateFormatter)
            val endDate = LocalDate.parse(toDate.substringBefore("T"), dateFormatter)
            
            var totalProcessed = 0
            var totalSaved = 0
            val processedDays = mutableListOf<String>()
            
            var currentDate = startDate
            while (!currentDate.isAfter(endDate)) {
                val dayStart = currentDate.atStartOfDay(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                val dayEnd = currentDate.plusDays(1).atStartOfDay(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                
                logger.info("Processing day: ${currentDate.format(dateFormatter)}")
                
                try {
                    // 1. Получаем отзывы за день (без ограничения, получаем все)
                    val reviewsJson = fetchReviews(dayStart, dayEnd, limit = 500)
                    val reviews = json.decodeFromString<List<Review>>(
                        ListSerializer(Review.serializer()),
                        reviewsJson
                    )
                    
                    if (reviews.isEmpty()) {
                        logger.debug("No reviews for day ${currentDate.format(dateFormatter)}")
                        currentDate = currentDate.plusDays(1)
                        continue
                    }
                    
                    logger.info("Fetched ${reviews.size} reviews for day ${currentDate.format(dateFormatter)}")
                    totalProcessed += reviews.size
                    
                    // 2. Анализируем через LLM (используем Koog агента через специальный промпт)
                    // Создаем промпт для анализа отзывов за день
                    val reviewsJsonForLLM = json.encodeToString(
                        ListSerializer(Review.serializer()),
                        reviews
                    )
                    
                    // Используем RAG для поиска похожих отзывов из прошлого (если доступен)
                    // ВАЖНО: Если RAG недоступен или произошла ошибка, продолжаем без него
                    val ragContext = if (reviewSummaryRagService != null && reviews.isNotEmpty()) {
                        try {
                            // Берем первые несколько отзывов для поиска похожих
                            val sampleReview = reviews.take(3).joinToString("\n") { "${it.rating}/5: ${it.text.take(100)}" }
                            val searchResults = runBlocking {
                                try {
                                    reviewSummaryRagService.search(
                                        query = sampleReview,
                                        limit = 3,
                                        minSimilarity = 0.5
                                    )
                                } catch (e: Exception) {
                                    logger.warn("RAG search failed for day ${currentDate.format(dateFormatter)}: ${e.message}")
                                    emptyList()
                                }
                            }
                            if (searchResults.isNotEmpty()) {
                                logger.debug("Found ${searchResults.size} similar reviews via RAG for day ${currentDate.format(dateFormatter)}")
                                "\n\nПохожие отзывы из прошлого для контекста:\n" + 
                                searchResults.joinToString("\n") { 
                                    "- ${it.reviewId}: ${it.content.take(150)} (сходство: ${String.format("%.2f", it.similarity)})" 
                                }
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            logger.warn("Error during RAG search for day ${currentDate.format(dateFormatter)}: ${e.message}. Continuing without RAG context.")
                            null
                        }
                    } else {
                        null
                    }
                    
                    val availableTopics = ReviewTopic.values().joinToString(", ") { it.name }
                    val analysisPrompt = """
                        Проанализируй следующие отзывы за ${currentDate.format(dateFormatter)} и создай саммари для каждого отзыва.
                        ${ragContext ?: ""}
                        
                        Для каждого отзыва создай ReviewSummary со следующей структурой:
                        {
                          "reviewId": "ID отзыва из Review.id",
                          "rating": рейтинг из Review.rating (1-5),
                          "date": дата из Review.date,
                          "summary": краткое саммари отзыва (1-2 предложения, БЕЗ markdown разметки),
                          "category": "POSITIVE" (для rating 4-5), "NEGATIVE" (для rating 1-2), "NEUTRAL" (для rating 3),
                          "topics": массив из ReviewTopic enum. ВАЖНО: используй ТОЛЬКО следующие значения: $availableTopics. Если тема не подходит ни под одну категорию, используй "OTHER".
                          "criticality": "HIGH" (для rating 1), "MEDIUM" (для rating 2), "LOW" (для остальных),
                          "weekStart": дата начала недели в формате ISO8601 (YYYY-MM-DD)
                        }
                        
                        КРИТИЧЕСКИ ВАЖНО: topics должен содержать ТОЛЬКО значения из списка выше. НЕ придумывай новые значения!
                        ${if (ragContext != null) "Используй контекст похожих отзывов из прошлого для более точной классификации тем." else ""}
                        
                        Верни ТОЛЬКО валидный JSON массив ReviewSummary, без дополнительного текста.
                        
                        Отзывы:
                        $reviewsJsonForLLM
                    """.trimIndent()
                    
                    // Используем Koog агента для анализа
                    if (koogAgentService == null) {
                        throw IllegalStateException("KoogAgentService not available for analysis")
                    }
                    val agent = koogAgentService.createAgent()
                    val summariesJson = try {
                        runBlocking {
                            val result = agent.run(analysisPrompt)
                            // Извлекаем JSON из ответа (может быть обернут в markdown)
                            var cleaned = result.trim()
                            if (cleaned.startsWith("```json")) {
                                cleaned = cleaned.removePrefix("```json").trim()
                            }
                            if (cleaned.startsWith("```")) {
                                cleaned = cleaned.removePrefix("```").trim()
                            }
                            if (cleaned.endsWith("```")) {
                                cleaned = cleaned.removeSuffix("```").trim()
                            }
                            // Ищем начало массива
                            val arrayStart = cleaned.indexOf('[')
                            if (arrayStart > 0) {
                                cleaned = cleaned.substring(arrayStart)
                            }
                            // Ищем конец массива
                            val arrayEnd = cleaned.lastIndexOf(']')
                            if (arrayEnd > 0 && arrayEnd < cleaned.length - 1) {
                                cleaned = cleaned.substring(0, arrayEnd + 1)
                            }
                            cleaned
                        }
                    } catch (e: LLMClientException) {
                        // Обрабатываем ошибки LLM (например, 403 - регион не поддерживается)
                        if (e.message?.contains("403") == true || e.message?.contains("unsupported_country") == true) {
                            logger.error("LLM API error (403 - region not supported) for day ${currentDate.format(dateFormatter)}: ${e.message}")
                            logger.warn("Skipping LLM analysis for day ${currentDate.format(dateFormatter)} due to region restrictions. Creating basic summaries from reviews.")
                            // Создаем базовые саммари без LLM анализа и сохраняем их
                            val summaries = createBasicSummariesFromReviews(reviews, currentDate)
                            val weekStart = currentDate.with(DayOfWeek.MONDAY).format(dateFormatter)
                            val saved = repository.saveReviewSummaries(summaries, weekStart)
                            if (saved) {
                                totalSaved += summaries.size
                                processedDays.add(currentDate.format(dateFormatter))
                                logger.info("✅ Saved ${summaries.size} basic summaries (without LLM) for day ${currentDate.format(dateFormatter)}")
                            }
                            currentDate = currentDate.plusDays(1)
                            continue
                        } else {
                            throw e
                        }
                    } catch (e: Exception) {
                        logger.error("Error calling LLM for day ${currentDate.format(dateFormatter)}: ${e.message}", e)
                        // Создаем базовые саммари без LLM анализа и сохраняем их
                        val summaries = createBasicSummariesFromReviews(reviews, currentDate)
                        val weekStart = currentDate.with(DayOfWeek.MONDAY).format(dateFormatter)
                        val saved = repository.saveReviewSummaries(summaries, weekStart)
                        if (saved) {
                            totalSaved += summaries.size
                            processedDays.add(currentDate.format(dateFormatter))
                            logger.info("✅ Saved ${summaries.size} basic summaries (without LLM) for day ${currentDate.format(dateFormatter)}")
                            
                            // Индексируем в RAG
                            if (reviewSummaryRagService != null) {
                                try {
                                    summaries.forEach { summary ->
                                        runBlocking {
                                            reviewSummaryRagService.indexSummary(summary)
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.warn("Error indexing basic summaries in RAG: ${e.message}")
                                }
                            }
                        }
                        currentDate = currentDate.plusDays(1)
                        continue
                    } finally {
                        agent.close()
                    }
                    
                    // 3. Парсим и сохраняем саммари с обработкой неизвестных значений
                    val summaries = if (summariesJson.isNotEmpty()) {
                        try {
                            json.decodeFromString<List<ReviewSummary>>(
                                ListSerializer(ReviewSummary.serializer()),
                                summariesJson
                            )
                        } catch (e: kotlinx.serialization.SerializationException) {
                            // Ошибка десериализации - логируем и пробуем создать базовые саммари
                            logger.error("Error deserializing summaries: ${e.message}")
                            logger.error("Problematic JSON (first 500 chars): ${summariesJson.take(500)}")
                            throw e
                        }
                    } else {
                        // Если summariesJson пустой, создаем базовые саммари
                        createBasicSummariesFromReviews(reviews, currentDate)
                    }
                    
                    // Вычисляем weekStart
                    val weekStart = currentDate.with(DayOfWeek.MONDAY).format(dateFormatter)
                    
                    // 4. Сохраняем в БД
                    val saved = repository.saveReviewSummaries(summaries, weekStart)
                    if (saved) {
                        totalSaved += summaries.size
                        processedDays.add(currentDate.format(dateFormatter))
                        logger.info("✅ Saved ${summaries.size} summaries for day ${currentDate.format(dateFormatter)}")
                    } else {
                        logger.warn("Failed to save summaries for day ${currentDate.format(dateFormatter)}")
                    }
                    
                } catch (e: Exception) {
                    logger.error("Error processing day ${currentDate.format(dateFormatter)}: ${e.message}", e)
                    // Продолжаем со следующим днем
                }
                
                currentDate = currentDate.plusDays(1)
            }
            
            logger.info("✅ Period analysis completed: processed $totalProcessed reviews, saved $totalSaved summaries for ${processedDays.size} days")
            
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", true)
                put("message", "Period analysis completed")
                put("totalProcessed", totalProcessed)
                put("totalSaved", totalSaved)
                put("daysProcessed", processedDays.size)
                putJsonArray("processedDays") {
                    processedDays.forEach { day ->
                        add(day)
                    }
                }
            })
        } catch (e: Exception) {
            logger.error("Error in analyzePeriodByDays: ${e.message}", e)
            json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            })
        }
    }

    /**
     * Создает базовые саммари из отзывов без использования LLM
     * Используется как fallback при ошибках LLM API
     */
    private fun createBasicSummariesFromReviews(reviews: List<Review>, date: LocalDate): List<ReviewSummary> {
        val weekStart = date.with(DayOfWeek.MONDAY).format(dateFormatter)
        return reviews.map { review ->
            ReviewSummary(
                reviewId = review.id,
                rating = review.rating,
                date = review.date,
                summary = review.text.take(200), // Первые 200 символов как саммари
                category = when {
                    review.rating >= 4 -> ReviewCategory.POSITIVE
                    review.rating <= 2 -> ReviewCategory.NEGATIVE
                    else -> ReviewCategory.NEUTRAL
                },
                topics = emptyList(), // Без тем, так как LLM недоступен
                criticality = when {
                    review.rating == 1 -> Criticality.HIGH
                    review.rating == 2 -> Criticality.MEDIUM
                    else -> Criticality.LOW
                },
                weekStart = weekStart
            )
        }
    }

}

