package com.prike.analyticsmcpserver.tools

import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Анализатор JSON файлов
 */
class JSONAnalyzer(
    private val file: File,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(JSONAnalyzer::class.java)
    
    /**
     * Анализирует JSON файл на основе запроса
     */
    fun analyze(query: String): String {
        val jsonData = readJSON()
        
        val queryLower = query.lowercase()
        val result = StringBuilder()
        
        result.appendLine("Анализ JSON файла: ${file.name}")
        result.appendLine("=".repeat(50))
        
        // Базовая информация
        result.appendLine("\n📊 Структура данных:")
        result.appendLine("- Тип: ${getJsonType(jsonData)}")
        result.appendLine("- Размер файла: ${file.length()} байт")
        
        // Анализ на основе запроса
        when {
            queryLower.contains("структур") || queryLower.contains("ключ") -> {
                result.appendLine("\n📋 Структура данных:")
                result.appendLine(getStructure(jsonData))
            }
            queryLower.contains("количеств") || queryLower.contains("элемент") -> {
                result.appendLine("\n📈 Количество элементов:")
                result.appendLine(getCount(jsonData))
            }
            queryLower.contains("пример") || queryLower.contains("образец") -> {
                result.appendLine("\n📄 Пример данных:")
                result.appendLine(json.encodeToString(JsonElement.serializer(), jsonData).take(500))
                if (json.encodeToString(JsonElement.serializer(), jsonData).length > 500) {
                    result.appendLine("\n... (обрезано)")
                }
            }
            else -> {
                result.appendLine("\n💡 Общая информация:")
                result.appendLine("  Тип: ${getJsonType(jsonData)}")
                result.appendLine("  Структура: ${getStructure(jsonData).take(200)}")
            }
        }
        
        return result.toString()
    }
    
    /**
     * Получить сводку по JSON файлу
     */
    fun getSummary(): String {
        val jsonData = readJSON()
        
        return buildString {
            appendLine("📊 Сводка по JSON файлу: ${file.name}")
            appendLine("=".repeat(50))
            appendLine("Размер файла: ${file.length()} байт")
            appendLine("Тип данных: ${getJsonType(jsonData)}")
            appendLine("Структура:")
            appendLine(getStructure(jsonData).take(300))
        }
    }
    
    /**
     * Читает JSON файл
     */
    private fun readJSON(): JsonElement {
        val content = file.readText()
        return json.parseToJsonElement(content)
    }
    
    /**
     * Определяет тип JSON данных
     */
    private fun getJsonType(element: JsonElement): String {
        return when (element) {
            is JsonObject -> "Object"
            is JsonArray -> "Array (${element.size} элементов)"
            is JsonPrimitive -> {
                when {
                    element.isString -> "String"
                    element.booleanOrNull != null -> "Boolean"
                    element.longOrNull != null -> "Number"
                    else -> "Primitive"
                }
            }
            JsonNull -> "Null"
        }
    }
    
    /**
     * Получает структуру JSON
     */
    private fun getStructure(element: JsonElement, indent: Int = 0): String {
        val indentStr = "  ".repeat(indent)
        return when (element) {
            is JsonObject -> {
                if (element.isEmpty()) {
                    "{}"
                } else {
                    buildString {
                        appendLine("{")
                        element.entries.take(10).forEach { (key, value) ->
                            append("$indentStr  \"$key\": ")
                            when (value) {
                                is JsonObject -> appendLine("{...}")
                                is JsonArray -> appendLine("[${value.size} элементов]")
                                else -> appendLine(value.toString().take(50))
                            }
                        }
                        if (element.size > 10) {
                            appendLine("$indentStr  ... (еще ${element.size - 10} ключей)")
                        }
                        append("$indentStr}")
                    }
                }
            }
            is JsonArray -> {
                if (element.isEmpty()) {
                    "[]"
                } else {
                    val firstType = getJsonType(element.firstOrNull() ?: JsonNull)
                    "[${element.size} элементов типа $firstType]"
                }
            }
            else -> element.toString().take(100)
        }
    }
    
    /**
     * Получает количество элементов
     */
    private fun getCount(element: JsonElement): String {
        return when (element) {
            is JsonArray -> "Массив содержит ${element.size} элементов"
            is JsonObject -> "Объект содержит ${element.size} ключей"
            else -> "Примитивное значение"
        }
    }
}

