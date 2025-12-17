package com.prike.infrastructure.client

import com.prike.domain.model.Review
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * HTTP клиент для работы с API Company Mobile Stores
 */
class ReviewsApiClient(
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val oauthToken: String? = null
) {
    private val logger = LoggerFactory.getLogger(ReviewsApiClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Получает отзывы за указанный период с обработкой пагинации
     */
    suspend fun fetchReviews(
        store: String,
        packageId: String,
        fromDate: String,
        toDate: String,
        pageSize: Int = 100,
        maxResults: Int? = null // Максимальное количество отзывов для получения
    ): List<Review> {
        val allReviews = mutableListOf<Review>()
        var nextPageToken: String? = null

        do {
            // Если указан maxResults и уже получено достаточно, прекращаем пагинацию
            if (maxResults != null && allReviews.size >= maxResults) {
                break
            }
            val url = "$baseUrl/api/v1/stores/$store/apps/$packageId/reviews"

            val requestBody = buildJsonObject {
                putJsonObject("filter") {
                    put("fromDate", fromDate)
                    put("toDate", toDate)
                }
                put("pageSize", pageSize)
                nextPageToken?.let { put("nextPageToken", it) }
            }

            try {
                if (oauthToken == null || (oauthToken.startsWith("${") && oauthToken.contains("}"))) {
                    logger.error("❌ OAuth token not set! Check .env file")
                    break
                }
                
                val httpResponse = httpClient.get(url) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(requestBody.toString())
                    oauthToken?.let { token ->
                        header(HttpHeaders.Authorization, "OAuth $token")
                    }
                }
                
                val statusCode = httpResponse.status.value
                val response: String = httpResponse.body()
                
                if (response.isBlank() || statusCode !in 200..299) {
                    logger.error("❌ API error: status=$statusCode")
                    if (statusCode == 404) {
                        logger.error("404 Not Found - check URL, OAuth token, store/packageId")
                    }
                    break
                }

                val jsonResponse = json.parseToJsonElement(response).jsonObject
                val type = jsonResponse["type"]?.jsonPrimitive?.content
                
                if (type != "success") {
                    logger.error("❌ API error type: $type")
                    break
                }

                val data = jsonResponse["data"]?.jsonObject ?: break
                val reviews = data["reviews"]?.jsonArray ?: break

                // Извлекаем только нужные поля: id, text, rating, date
                reviews.forEach { reviewElement ->
                    val reviewObj = reviewElement.jsonObject
                    val id = reviewObj["id"]?.jsonPrimitive?.content ?: return@forEach
                    val text = reviewObj["text"]?.jsonPrimitive?.content ?: ""
                    val rating = reviewObj["rating"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
                    val date = reviewObj["date"]?.jsonPrimitive?.content ?: ""

                    allReviews.add(
                        Review(
                            id = id,
                            text = text,
                            rating = rating,
                            date = date
                        )
                    )
                }

                // Получаем токен следующей страницы
                val pagination = data["pagination"]?.jsonObject
                nextPageToken = pagination?.get("nextPageToken")?.jsonPrimitive?.content

                logger.debug("Fetched ${reviews.size} reviews, total: ${allReviews.size}")
                
                // Если указан maxResults и уже получено достаточно, прекращаем пагинацию
                if (maxResults != null && allReviews.size >= maxResults) {
                    break
                }
            } catch (e: Exception) {
                logger.error("Error fetching reviews: ${e.message}", e)
                break
            }
        } while (nextPageToken != null)

        // Обрезаем до maxResults, если указан
        val result = if (maxResults != null && allReviews.size > maxResults) {
            allReviews.take(maxResults)
        } else {
            allReviews
        }
        
        logger.info("✅ Fetched ${result.size} reviews from API${if (maxResults != null && allReviews.size > maxResults) " (requested: $maxResults, total available: ${allReviews.size})" else ""}")
        return result
    }
}
