package com.prike.analyticsmcpserver.tools

import com.opencsv.CSVReader
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader

/**
 * Анализатор CSV файлов
 */
class CSVAnalyzer(private val file: File) {
    private val logger = LoggerFactory.getLogger(CSVAnalyzer::class.java)
    
    /**
     * Анализирует CSV файл на основе запроса
     */
    fun analyze(query: String): String {
        val data = readCSV()
        
        if (data.isEmpty()) {
            return "CSV file is empty"
        }
        
        val queryLower = query.lowercase()
        
        // Простая логика анализа на основе ключевых слов
        val result = StringBuilder()
        
        result.appendLine("Анализ CSV файла: ${file.name}")
        result.appendLine("=".repeat(50))
        
        // Базовая информация
        result.appendLine("\n📊 Структура данных:")
        result.appendLine("- Колонки: ${data.first().joinToString(", ")}")
        result.appendLine("- Количество строк: ${data.size - 1}") // -1 для заголовка
        result.appendLine("- Количество колонок: ${data.first().size}")
        
        // Анализ на основе запроса
        when {
            queryLower.contains("колонк") || queryLower.contains("столбец") -> {
                result.appendLine("\n📋 Список колонок:")
                data.first().forEachIndexed { index, column ->
                    result.appendLine("  ${index + 1}. $column")
                }
            }
            queryLower.contains("строк") || queryLower.contains("запис") -> {
                result.appendLine("\n📈 Количество записей: ${data.size - 1}")
            }
            queryLower.contains("пример") || queryLower.contains("образец") -> {
                result.appendLine("\n📄 Примеры данных (первые 3 строки):")
                data.take(4).forEachIndexed { index, row ->
                    result.appendLine("  Строка ${index + 1}: ${row.joinToString(" | ")}")
                }
            }
            queryLower.contains("статистик") || queryLower.contains("средн") -> {
                result.appendLine("\n📊 Статистика:")
                result.appendLine("  - Всего записей: ${data.size - 1}")
                result.appendLine("  - Колонок: ${data.first().size}")
                
                // Попытка найти числовые колонки
                if (data.size > 1) {
                    val numericColumns = findNumericColumns(data)
                    if (numericColumns.isNotEmpty()) {
                        result.appendLine("\n  Числовые колонки:")
                        numericColumns.forEach { (colIndex, colName) ->
                            val values = data.drop(1).mapNotNull { row ->
                                row.getOrNull(colIndex)?.toDoubleOrNull()
                            }
                            if (values.isNotEmpty()) {
                                val avg = values.average()
                                val min = values.minOrNull() ?: 0.0
                                val max = values.maxOrNull() ?: 0.0
                                result.appendLine("    - $colName: среднее=$avg, min=$min, max=$max")
                            }
                        }
                    }
                }
            }
            else -> {
                result.appendLine("\n💡 Общая информация:")
                result.appendLine("  Файл содержит ${data.size - 1} записей с ${data.first().size} колонками")
                result.appendLine("  Колонки: ${data.first().joinToString(", ")}")
            }
        }
        
        return result.toString()
    }
    
    /**
     * Получить сводку по CSV файлу
     */
    fun getSummary(): String {
        val data = readCSV()
        
        return buildString {
            appendLine("📊 Сводка по CSV файлу: ${file.name}")
            appendLine("=".repeat(50))
            appendLine("Размер файла: ${file.length()} байт")
            appendLine("Количество строк: ${data.size}")
            appendLine("Количество колонок: ${if (data.isNotEmpty()) data.first().size else 0}")
            if (data.isNotEmpty()) {
                appendLine("Колонки: ${data.first().joinToString(", ")}")
            }
        }
    }
    
    /**
     * Читает CSV файл
     */
    private fun readCSV(): List<List<String>> {
        return FileReader(file).use { reader ->
            CSVReader(reader).use { csvReader ->
                csvReader.readAll().map { it.toList() }
            }
        }
    }
    
    /**
     * Находит числовые колонки
     */
    private fun findNumericColumns(data: List<List<String>>): List<Pair<Int, String>> {
        if (data.isEmpty()) return emptyList()
        
        val header = data.first()
        val numericColumns = mutableListOf<Pair<Int, String>>()
        
        header.forEachIndexed { index, columnName ->
            // Проверяем первые несколько строк на числовые значения
            val sampleSize = minOf(10, data.size - 1)
            val numericCount = data.drop(1).take(sampleSize).count { row ->
                row.getOrNull(index)?.toDoubleOrNull() != null
            }
            
            // Если больше половины значений числовые, считаем колонку числовой
            if (numericCount > sampleSize / 2) {
                numericColumns.add(index to columnName)
            }
        }
        
        return numericColumns
    }
}

