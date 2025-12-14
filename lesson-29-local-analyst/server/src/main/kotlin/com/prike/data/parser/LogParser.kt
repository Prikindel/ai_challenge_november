package com.prike.data.parser

import com.prike.domain.model.DataRecord
import com.prike.domain.model.LogRecord
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.regex.Pattern

/**
 * Парсер логов проекта
 * Поддерживает:
 * - JSON логи: {"timestamp": "...", "level": "ERROR", "message": "..."}
 * - Plain text логи: различные форматы (log4j, winston, и т.д.)
 */
class LogParser {
    private val logger = LoggerFactory.getLogger(LogParser::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Паттерны для парсинга plain text логов
    private val log4jPattern = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) \\[(.*?)\\] (ERROR|WARN|INFO|DEBUG|TRACE) (.*?) - (.*)$",
        Pattern.MULTILINE
    )
    
    private val simplePattern = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?) (ERROR|WARN|INFO|DEBUG|TRACE):? (.*)$",
        Pattern.MULTILINE
    )
    
    /**
     * Парсит файл логов и возвращает список DataRecord
     * 
     * @param file содержимое файла в байтах
     * @param filename имя файла
     * @return список DataRecord
     */
    fun parse(file: ByteArray, filename: String): List<DataRecord> {
        return try {
            val content = String(file, Charsets.UTF_8)
            val records = mutableListOf<DataRecord>()
            
            // Пытаемся определить формат логов
            if (isJsonLogs(content)) {
                logger.info("Detected JSON log format")
                records.addAll(parseJsonLogs(content, filename))
            } else {
                logger.info("Detected plain text log format")
                records.addAll(parsePlainTextLogs(content, filename))
            }
            
            logger.info("Parsed ${records.size} log records from file: $filename")
            records
        } catch (e: Exception) {
            logger.error("Error parsing log file $filename: ${e.message}", e)
            throw LogParseException("Ошибка при парсинге файла логов: ${e.message}", e)
        }
    }
    
    /**
     * Проверяет, является ли содержимое JSON логами
     */
    private fun isJsonLogs(content: String): Boolean {
        val trimmed = content.trim()
        return trimmed.startsWith("{") || trimmed.startsWith("[{")
    }
    
    /**
     * Парсит JSON логи
     */
    private fun parseJsonLogs(content: String, filename: String): List<DataRecord> {
        val records = mutableListOf<DataRecord>()
        
        try {
            val jsonElement = json.parseToJsonElement(content)
            
            when {
                // Массив JSON объектов
                jsonElement is JsonArray -> {
                    jsonElement.forEachIndexed { index, element ->
                        if (index >= 10000) return@forEachIndexed
                        if (element is JsonObject) {
                            val logRecord = parseJsonLogObject(element)
                            val dataMap = logRecordToMap(logRecord)
                            records.add(createDataRecord(dataMap, filename))
                        }
                    }
                }
                // Один JSON объект
                jsonElement is JsonObject -> {
                    val logRecord = parseJsonLogObject(jsonElement)
                    val dataMap = logRecordToMap(logRecord)
                    records.add(createDataRecord(dataMap, filename))
                }
            }
        } catch (e: Exception) {
            // Если не удалось распарсить как JSON, пробуем как plain text
            logger.warn("Failed to parse as JSON, trying plain text: ${e.message}")
            return parsePlainTextLogs(content, filename)
        }
        
        return records
    }
    
    /**
     * Парсит JSON объект лога
     */
    private fun parseJsonLogObject(obj: JsonObject): LogRecord {
        return LogRecord(
            timestamp = obj["timestamp"]?.jsonPrimitive?.content ?: obj["time"]?.jsonPrimitive?.content ?: obj["date"]?.jsonPrimitive?.content ?: "",
            level = obj["level"]?.jsonPrimitive?.content ?: obj["severity"]?.jsonPrimitive?.content ?: "INFO",
            message = obj["message"]?.jsonPrimitive?.content ?: obj["msg"]?.jsonPrimitive?.content ?: "",
            error = obj["error"]?.jsonPrimitive?.content,
            stackTrace = obj["stackTrace"]?.jsonPrimitive?.content ?: obj["stack"]?.jsonPrimitive?.content,
            source = obj["source"]?.jsonPrimitive?.content ?: obj["logger"]?.jsonPrimitive?.content,
            thread = obj["thread"]?.jsonPrimitive?.content
        )
    }
    
    /**
     * Парсит plain text логи
     */
    private fun parsePlainTextLogs(content: String, filename: String): List<DataRecord> {
        val records = mutableListOf<DataRecord>()
        val lines = content.lines()
        
        var lineIndex = 0
        var currentStackTrace: StringBuilder? = null
        
        for (line in lines) {
            if (lineIndex >= 10000) break // Ограничение
            
            if (line.isBlank()) continue
            
            // Пытаемся распарсить строку лога
            val logRecord = parseLogLine(line)
            
            if (logRecord != null) {
                // Если есть предыдущий stack trace, добавляем его
                currentStackTrace?.let { stack ->
                    val dataMap = logRecordToMap(logRecord.copy(stackTrace = stack.toString()))
                    records.add(createDataRecord(dataMap, filename))
                    currentStackTrace = null
                } ?: run {
                    val dataMap = logRecordToMap(logRecord)
                    records.add(createDataRecord(dataMap, filename))
                }
                lineIndex++
            } else {
                // Возможно, это продолжение stack trace
                if (line.trim().startsWith("at ") || line.trim().startsWith("Caused by:")) {
                    if (currentStackTrace == null) {
                        currentStackTrace = StringBuilder()
                    }
                    currentStackTrace.appendLine(line)
                }
            }
        }
        
        return records
    }
    
    /**
     * Парсит одну строку лога
     */
    private fun parseLogLine(line: String): LogRecord? {
        // Пробуем log4j формат
        var matcher = log4jPattern.matcher(line)
        if (matcher.find()) {
            return LogRecord(
                timestamp = matcher.group(1),
                level = matcher.group(3),
                message = matcher.group(5),
                source = matcher.group(2)
            )
        }
        
        // Пробуем простой формат
        matcher = simplePattern.matcher(line)
        if (matcher.find()) {
            return LogRecord(
                timestamp = matcher.group(1),
                level = matcher.group(2),
                message = matcher.group(3)
            )
        }
        
        // Если не удалось распарсить, создаем запись с полной строкой
        if (line.length > 50) { // Игнорируем очень короткие строки
            return LogRecord(
                timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                level = "INFO",
                message = line
            )
        }
        
        return null
    }
    
    /**
     * Преобразует LogRecord в Map для DataRecord
     */
    private fun logRecordToMap(logRecord: LogRecord): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["timestamp"] = logRecord.timestamp
        map["level"] = logRecord.level
        map["message"] = logRecord.message
        logRecord.error?.let { map["error"] = it }
        logRecord.stackTrace?.let { map["stackTrace"] = it }
        logRecord.source?.let { map["source"] = it }
        logRecord.thread?.let { map["thread"] = it }
        return map
    }
    
    /**
     * Создает DataRecord из map
     */
    private fun createDataRecord(dataMap: Map<String, String>, filename: String): DataRecord {
        return DataRecord(
            id = UUID.randomUUID().toString(),
            source = "logs",
            sourceFile = filename,
            data = dataMap,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Исключение при парсинге логов
 */
class LogParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
