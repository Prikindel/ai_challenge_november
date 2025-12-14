package com.prike.data.parser

import com.prike.domain.model.DataRecord
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Парсер JSON файлов
 */
class JsonParser {
    private val logger = LoggerFactory.getLogger(JsonParser::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Парсит JSON файл и возвращает список DataRecord
     * Поддерживает:
     * - Массив объектов: [{...}, {...}]
     * - Один объект: {...}
     * 
     * @param file содержимое файла в байтах
     * @param filename имя файла
     * @return список DataRecord
     */
    fun parse(file: ByteArray, filename: String): List<DataRecord> {
        return try {
            val jsonString = String(file, Charsets.UTF_8)
            val records = mutableListOf<DataRecord>()
            
            // Пытаемся распарсить как JSON
            val jsonElement = json.parseToJsonElement(jsonString)
            
            when {
                // Массив объектов
                jsonElement is JsonArray -> {
                    logger.info("Parsing JSON array with ${jsonElement.size} elements")
                    jsonElement.forEachIndexed { index, element ->
                        if (index >= 10000) { // Ограничение 10000 записей
                            return@forEachIndexed
                        }
                        if (element is JsonObject) {
                            val dataMap = element.toMap()
                            val record = DataRecord(
                                id = UUID.randomUUID().toString(),
                                source = "json",
                                sourceFile = filename,
                                data = dataMap,
                                timestamp = System.currentTimeMillis()
                            )
                            records.add(record)
                        }
                    }
                }
                // Один объект
                jsonElement is JsonObject -> {
                    logger.info("Parsing single JSON object")
                    val dataMap = jsonElement.toMap()
                    val record = DataRecord(
                        id = UUID.randomUUID().toString(),
                        source = "json",
                        sourceFile = filename,
                        data = dataMap,
                        timestamp = System.currentTimeMillis()
                    )
                    records.add(record)
                }
                else -> {
                    logger.warn("JSON file contains unsupported structure: ${jsonElement::class.simpleName}")
                    return emptyList()
                }
            }
            
            logger.info("Parsed ${records.size} records from JSON file: $filename")
            records
        } catch (e: Exception) {
            logger.error("Error parsing JSON file $filename: ${e.message}", e)
            throw JsonParseException("Ошибка при парсинге JSON файла: ${e.message}", e)
        }
    }
    
    /**
     * Преобразует JsonObject в Map<String, String>
     */
    private fun JsonObject.toMap(): Map<String, String> {
        return entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value.toString()
                is JsonArray -> value.toString()
                is JsonNull -> ""
                else -> value.toString()
            }
        }
    }
}

/**
 * Исключение при парсинге JSON
 */
class JsonParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
