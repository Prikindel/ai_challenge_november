package com.prike.mcpserver.tools

import com.prike.mcpserver.tools.handlers.AnalyzeSentimentHandler
import com.prike.mcpserver.tools.handlers.ExtractKeywordsHandler
import com.prike.mcpserver.tools.handlers.CalculateStatisticsHandler
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Реестр инструментов для Data Processing MCP Server
 */
class ToolRegistry(
    private val analyzeSentimentHandler: AnalyzeSentimentHandler,
    private val extractKeywordsHandler: ExtractKeywordsHandler,
    private val calculateStatisticsHandler: CalculateStatisticsHandler
) {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    
    /**
     * Регистрация всех инструментов на сервере
     */
    fun registerTools(server: Server) {
        registerAnalyzeSentiment(server)
        registerExtractKeywords(server)
        registerCalculateStatistics(server)
        logger.info("Все инструменты зарегистрированы (3 инструмента)")
    }
    
    /**
     * Регистрация инструмента analyze_sentiment
     */
    private fun registerAnalyzeSentiment(server: Server) {
        server.addTool(
            name = "analyze_sentiment",
            description = """
                Анализирует тональность текста и определяет, является ли он позитивным, негативным или нейтральным.
                Использует простой алгоритм на основе ключевых слов для определения тональности.
                
                Используй этот инструмент, когда нужно определить эмоциональную окраску текста.
                Например, для анализа отзывов, комментариев, сообщений пользователей.
                
                Параметры:
                - text: текст для анализа тональности
                
                Возвращает JSON объект:
                {"sentiment": "positive|negative|neutral", "score": 0.0-1.0, "explanation": "..."}
                
                Где:
                - sentiment: тональность текста ("positive", "negative" или "neutral")
                - score: числовая оценка от 0.0 (негативный) до 1.0 (позитивный)
                - explanation: объяснение результата анализа
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "Текст для анализа тональности")
                    }
                },
                required = listOf("text")
            )
        ) { request ->
            logger.debug("Вызов инструмента analyze_sentiment с аргументами: ${request.arguments}")

            val text = extractStringParameter(request.arguments, "text")
                ?: throw IllegalArgumentException("text is required and must be a string")

            analyzeSentimentHandler.handle(AnalyzeSentimentHandler.Params(text))
        }
    }
    
    /**
     * Регистрация инструмента extract_keywords
     */
    private fun registerExtractKeywords(server: Server) {
        server.addTool(
            name = "extract_keywords",
            description = """
                Извлекает ключевые слова из текста на основе частотного анализа.
                Использует простой алгоритм TF (Term Frequency) для определения наиболее важных слов.
                
                Используй этот инструмент, когда нужно найти основные темы или ключевые слова в тексте.
                Например, для суммаризации, тегирования, поиска основных тем в переписке.
                
                Параметры:
                - text: текст для извлечения ключевых слов
                - count: количество ключевых слов для извлечения (по умолчанию 10)
                
                Возвращает JSON массив строк с ключевыми словами:
                ["keyword1", "keyword2", ...]
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "Текст для извлечения ключевых слов")
                    }
                    putJsonObject("count") {
                        put("type", "number")
                        put("description", "Количество ключевых слов для извлечения (по умолчанию 10)")
                    }
                },
                required = listOf("text")
            )
        ) { request ->
            logger.debug("Вызов инструмента extract_keywords с аргументами: ${request.arguments}")

            val text = extractStringParameter(request.arguments, "text")
                ?: throw IllegalArgumentException("text is required and must be a string")

            val count = extractIntParameter(request.arguments, "count") ?: 10

            extractKeywordsHandler.handle(ExtractKeywordsHandler.Params(text, count))
        }
    }
    
    /**
     * Регистрация инструмента calculate_statistics
     */
    private fun registerCalculateStatistics(server: Server) {
        server.addTool(
            name = "calculate_statistics",
            description = """
                Рассчитывает статистику по массиву сообщений.
                Анализирует данные и возвращает общую статистику: количество сообщений, уникальных пользователей,
                среднюю длину сообщений и временной диапазон.
                
                Используй этот инструмент, когда нужно получить общую статистику по переписке или набору сообщений.
                Например, для анализа активности пользователей, временных паттернов, объёма переписки.
                
                Параметры:
                - data: JSON массив сообщений (строка)
                
                Формат входных данных (JSON массив):
                [
                  {"content": "...", "timestamp": ..., "role": "...", "author": "..."},
                  ...
                ]
                
                Возвращает JSON объект:
                {
                  "totalMessages": 100,
                  "uniqueUsers": 5,
                  "averageLength": 50.5,
                  "timeRange": {"start": ..., "end": ...}
                }
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("data") {
                        put("type", "string")
                        put("description", "JSON массив сообщений для анализа")
                    }
                },
                required = listOf("data")
            )
        ) { request ->
            logger.debug("Вызов инструмента calculate_statistics с аргументами: ${request.arguments}")

            val data = extractStringParameter(request.arguments, "data")
                ?: throw IllegalArgumentException("data is required and must be a string")

            calculateStatisticsHandler.handle(CalculateStatisticsHandler.Params(data))
        }
    }
    
    /**
     * Извлечь String параметр из JsonObject
     */
    private fun extractStringParameter(arguments: JsonObject, key: String): String? {
        val value = arguments[key] ?: return null
        return when {
            value is JsonPrimitive -> value.contentOrNull
            value is String -> value
            else -> null
        }
    }
    
    /**
     * Извлечь Int параметр из JsonObject
     */
    private fun extractIntParameter(arguments: JsonObject, key: String): Int? {
        val value = arguments[key] ?: return null
        return when {
            value is JsonPrimitive -> value.intOrNull
            value is Number -> value.toInt()
            else -> null
        }
    }
}

