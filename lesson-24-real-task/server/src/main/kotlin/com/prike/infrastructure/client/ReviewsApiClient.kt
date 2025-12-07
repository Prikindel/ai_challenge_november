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
        pageSize: Int = 100
    ): List<Review> {
        val allReviews = mutableListOf<Review>()
        var nextPageToken: String? = null

        do {
            val url = "$baseUrl/api/v1/stores/$store/apps/$packageId/reviews"
            
            logger.debug("Fetching reviews from $url, pageToken: $nextPageToken")

            val requestBody = buildJsonObject {
                putJsonObject("filter") {
                    put("fromDate", fromDate)
                    put("toDate", toDate)
                }
                put("pageSize", pageSize)
                nextPageToken?.let { put("nextPageToken", it) }
            }

            try {
                val response: String = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                    // Добавляем OAuth токен в заголовок Authorization, если он указан
                    oauthToken?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }.body()

                val jsonResponse = json.parseToJsonElement(response).jsonObject
                
                val type = jsonResponse["type"]?.jsonPrimitive?.content
                if (type != "success") {
                    logger.error("API returned error: $response")
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
            } catch (e: Exception) {
                logger.error("Error fetching reviews: ${e.message}", e)
                break
            }
        } while (nextPageToken != null)

        logger.info("Total reviews fetched: ${allReviews.size}")
        return allReviews
    }
}
