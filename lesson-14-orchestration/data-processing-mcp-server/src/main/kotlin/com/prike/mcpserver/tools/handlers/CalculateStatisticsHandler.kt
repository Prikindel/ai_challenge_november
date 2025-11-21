package com.prike.mcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Обработчик для инструмента calculate_statistics
 * Рассчитывает статистику по массиву сообщений
 */
class CalculateStatisticsHandler : ToolHandler<CalculateStatisticsHandler.Params, CalculateStatisticsHandler.StatisticsResult>() {

    override val logger = LoggerFactory.getLogger(CalculateStatisticsHandler::class.java)

    override fun execute(params: Params): StatisticsResult {
        logger.info("Расчёт статистики по данным (JSON строка, длина: ${params.data.length} символов)")
        
        // Парсим JSON массив сообщений
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        
        val messages = try {
            val jsonArray = json.parseToJsonElement(params.data)
            if (jsonArray is JsonArray) {
                jsonArray.mapNotNull { element ->
                    if (element is JsonObject) {
                        val content = element["content"]?.jsonPrimitive?.content ?: ""
                        val timestamp = element["timestamp"]?.jsonPrimitive?.content?.toLongOrNull()
                        val role = element["role"]?.jsonPrimitive?.content
                        val author = element["author"]?.jsonPrimitive?.content
                        
                        MessageInfo(
                            content = content,
                            timestamp = timestamp,
                            role = role,
                            author = author
                        )
                    } else {
                        null
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("Ошибка парсинга JSON: ${e.message}", e)
            emptyList()
        }
        
        if (messages.isEmpty()) {
            logger.warn("Не удалось распарсить сообщения или массив пуст")
            return StatisticsResult(
                totalMessages = 0,
                uniqueUsers = 0,
                averageLength = 0.0,
                timeRange = TimeRange(null, null)
            )
        }
        
        // Рассчитываем статистику
        val totalMessages = messages.size
        val uniqueUsers = messages.mapNotNull { it.author ?: it.role }.distinct().size
        val averageLength = messages.map { it.content.length }.average()
        
        val timestamps = messages.mapNotNull { it.timestamp }
        val timeRange = if (timestamps.isNotEmpty()) {
            TimeRange(
                start = timestamps.minOrNull(),
                end = timestamps.maxOrNull()
            )
        } else {
            TimeRange(null, null)
        }
        
        logger.info("Статистика: сообщений=$totalMessages, уникальных пользователей=$uniqueUsers, средняя длина=${averageLength.toInt()}")
        
        return StatisticsResult(
            totalMessages = totalMessages,
            uniqueUsers = uniqueUsers,
            averageLength = averageLength,
            timeRange = timeRange
        )
    }

    override fun prepareResult(request: Params, result: StatisticsResult): TextContent {
        val resultJson = buildJsonObject {
            put("totalMessages", result.totalMessages)
            put("uniqueUsers", result.uniqueUsers)
            put("averageLength", result.averageLength)
            putJsonObject("timeRange") {
                result.timeRange.start?.let { put("start", it) }
                result.timeRange.end?.let { put("end", it) }
            }
        }
        
        return TextContent(text = resultJson.toString())
    }

    data class Params(
        val data: String  // JSON массив сообщений
    )
    
    data class MessageInfo(
        val content: String,
        val timestamp: Long?,
        val role: String?,
        val author: String?
    )
    
    data class StatisticsResult(
        val totalMessages: Int,
        val uniqueUsers: Int,
        val averageLength: Double,
        val timeRange: TimeRange
    )
    
    data class TimeRange(
        val start: Long?,
        val end: Long?
    )
}

