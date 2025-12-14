package com.prike.data.parser

import com.opencsv.CSVReader
import com.prike.domain.model.DataRecord
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Парсер CSV файлов
 */
class CsvParser {
    private val logger = LoggerFactory.getLogger(CsvParser::class.java)
    
    /**
     * Парсит CSV файл и возвращает список DataRecord
     * 
     * @param file содержимое файла в байтах
     * @param filename имя файла
     * @return список DataRecord
     */
    fun parse(file: ByteArray, filename: String): List<DataRecord> {
        return try {
            val reader = CSVReader(InputStreamReader(ByteArrayInputStream(file), StandardCharsets.UTF_8))
            val records = mutableListOf<DataRecord>()
            
            // Читаем заголовки (первая строка)
            val headers = reader.readNext()
            if (headers == null || headers.isEmpty()) {
                logger.warn("CSV file is empty or has no headers: $filename")
                return emptyList()
            }
            
            logger.info("CSV headers: ${headers.joinToString(", ")}")
            
            // Читаем данные
            var rowIndex = 0
            var row: Array<String>?
            while (reader.readNext().also { row = it } != null && rowIndex < 10000) { // Ограничение 10000 записей
                val rowData = row!!
                
                // Создаем map из заголовков и значений
                val dataMap = headers.mapIndexedNotNull { index, header ->
                    if (index < rowData.size) {
                        header.trim() to rowData[index].trim()
                    } else {
                        null
                    }
                }.toMap()
                
                // Создаем DataRecord
                val record = DataRecord(
                    id = UUID.randomUUID().toString(),
                    source = "csv",
                    sourceFile = filename,
                    data = dataMap,
                    timestamp = System.currentTimeMillis()
                )
                
                records.add(record)
                rowIndex++
            }
            
            reader.close()
            logger.info("Parsed $rowIndex records from CSV file: $filename")
            records
        } catch (e: Exception) {
            logger.error("Error parsing CSV file $filename: ${e.message}", e)
            throw CsvParseException("Ошибка при парсинге CSV файла: ${e.message}", e)
        }
    }
}

/**
 * Исключение при парсинге CSV
 */
class CsvParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
