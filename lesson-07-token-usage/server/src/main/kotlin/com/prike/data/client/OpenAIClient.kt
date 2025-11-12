package com.prike.data.client

import com.prike.data.dto.MessageDto
import com.prike.data.dto.OpenAIErrorResponse
import com.prike.data.dto.OpenAIResponse
import com.prike.domain.exception.AIServiceException
import com.prike.domain.repository.AIResponseFormat
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

class OpenAIClient(
    private val apiKey: String,
    private val defaultApiUrl: String,
    private val defaultModel: String?,
    private val defaultTemperature: Double?,
    private val defaultMaxTokens: Int?,
    requestTimeoutSeconds: Int,
    private val defaultSystemPrompt: String? = null,
    private val useJsonFormat: Boolean = false
) {
    private val requestTimeoutMs = requestTimeoutSeconds * 1000L
    private val logger = LoggerFactory.getLogger(OpenAIClient::class.java)
    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeoutMs
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = requestTimeoutMs
        }
    }

    data class CompletionResult(
        val response: OpenAIResponse,
        val requestJson: String,
        val responseJson: String
    )

    suspend fun getCompletion(
        userMessage: String,
        apiUrlOverride: String? = null,
        modelOverride: String? = null,
        temperatureOverride: Double? = null,
        maxTokensOverride: Int? = null,
        responseFormatOverride: AIResponseFormat = AIResponseFormat.TEXT,
        systemPromptOverride: String? = null,
        additionalParams: Map<String, Any?> = emptyMap()
    ): CompletionResult {
        val apiUrl = apiUrlOverride?.takeIf { it.isNotBlank() } ?: defaultApiUrl
        val model = modelOverride ?: defaultModel
            ?: throw IllegalStateException("Не указан идентификатор модели для запроса")
        val systemPrompt = systemPromptOverride ?: defaultSystemPrompt

        val messages = buildMessages(systemPrompt, userMessage)
        val responseFormat = resolveResponseFormat(responseFormatOverride)

        val requestBody = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", messages)
            temperatureOverride
                ?: defaultTemperature
                ?.let { put("temperature", JsonPrimitive(it)) }
            maxTokensOverride
                ?: defaultMaxTokens
                ?.let { put("max_tokens", JsonPrimitive(it)) }
            responseFormat?.let { put("response_format", it) }

            additionalParams.forEach { (key, value) ->
                put(key, value.toJsonElement())
            }
        }

        val requestJson = runCatching {
            jsonSerializer.encodeToString(JsonObject.serializer(), requestBody)
        }.getOrElse { "{}" }

        runCatching {
            logger.info("--> POST $apiUrl")
            logger.info("Content-Type: application/json")
            logger.info("")
            logger.info(requestJson)
            logger.info("--> END POST")
        }

        return runCatching {
            val response = client.post(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(requestBody)
            }

            val statusCode = response.status.value

            if (!response.status.isSuccess()) {
                val errorBody = runCatching {
                    Json.decodeFromString<OpenAIErrorResponse>(response.bodyAsText())
                }.getOrNull()

                val errorJson = runCatching {
                    if (errorBody != null) jsonSerializer.encodeToString(errorBody) else ""
                }.getOrElse { "" }

                runCatching {
                    logger.info("<-- $statusCode $apiUrl")
                    if (errorJson.isNotEmpty()) {
                        logger.info(errorJson)
                    }
                    logger.info("<-- END HTTP")
                }

                throw AIServiceException(
                    "Ошибка API: ${errorBody?.error?.message ?: "HTTP $statusCode"}",
                    Exception("API вернул статус $statusCode")
                )
            }

            val responseBody = response.body<OpenAIResponse>()

            val responseJson = runCatching {
                jsonSerializer.encodeToString(responseBody)
            }.getOrElse { "{}" }

            runCatching {
                logger.info("<-- $statusCode $apiUrl")
                logger.info("")
                logger.info(responseJson)
                logger.info("<-- END HTTP")
            }

            CompletionResult(responseBody, requestJson, responseJson)
        }.getOrElse { throwable ->
            if (throwable is AIServiceException) throw throwable
            throw AIServiceException("Ошибка при обращении к AI API: ${throwable.message}", throwable)
        }
    }

    fun close() {
        client.close()
    }

    private fun buildMessages(systemPrompt: String?, userMessage: String): JsonArray =
        buildJsonArray {
            systemPrompt?.let {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(it))
                    }
                )
            }
            add(
                buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(userMessage))
                }
            )
        }

    private fun resolveResponseFormat(format: AIResponseFormat): JsonObject? = when {
        format == AIResponseFormat.JSON_OBJECT -> buildJsonObject {
            put("type", JsonPrimitive("json_object"))
        }
        useJsonFormat -> buildJsonObject {
            put("type", JsonPrimitive("json_object"))
        }
        else -> null
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Map<*, *> -> {
            val map = this
            buildJsonObject {
                map.forEach { (key, value) ->
                    if (key is String) {
                        put(key, value.toJsonElement())
                    }
                }
            }
        }
        is Iterable<*> -> {
            val iterable = this
            buildJsonArray {
                iterable.forEach { value ->
                    add(value.toJsonElement())
                }
            }
        }
        else -> JsonPrimitive(this.toString())
    }
}
