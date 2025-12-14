package com.prike.domain.service

import com.prike.domain.model.DataRecord
import org.slf4j.LoggerFactory

/**
 * Статистика данных
 */
data class DataStatistics(
    val totalRecords: Int,
    val bySource: Map<String, Int>,
    val fieldStats: Map<String, FieldStatistics>,
    val uniqueValues: Map<String, Set<String>>,
    val sampleRecords: List<Map<String, String>>
)

/**
 * Статистика по полю
 */
data class FieldStatistics(
    val fieldName: String,
    val totalCount: Int,
    val uniqueCount: Int,
    val nullCount: Int,
    val topValues: Map<String, Int>  // значение -> количество
)

/**
 * Сервис для расчета статистики данных
 */
class DataStatisticsService {
    private val logger = LoggerFactory.getLogger(DataStatisticsService::class.java)
    
    /**
     * Вычисляет статистику для набора записей
     */
    fun calculateStatistics(records: List<DataRecord>): DataStatistics {
        if (records.isEmpty()) {
            return DataStatistics(
                totalRecords = 0,
                bySource = emptyMap(),
                fieldStats = emptyMap(),
                uniqueValues = emptyMap(),
                sampleRecords = emptyList()
            )
        }
        
        // Статистика по источникам
        val bySource = records.groupingBy { it.source }.eachCount()
        
        // Собираем все уникальные поля
        val allFields = records.flatMap { it.data.keys }.toSet()
        
        // Статистика по полям
        val fieldStats = allFields.associateWith { fieldName ->
            calculateFieldStatistics(records, fieldName)
        }
        
        // Уникальные значения для каждого поля (ограничено для производительности)
        val uniqueValues = allFields.associateWith { fieldName ->
            records.mapNotNull { it.data[fieldName] }
                .take(1000)  // Ограничение для производительности
                .toSet()
        }
        
        // Примеры записей (первые 10)
        val sampleRecords = records.take(10).map { it.data }
        
        return DataStatistics(
            totalRecords = records.size,
            bySource = bySource,
            fieldStats = fieldStats,
            uniqueValues = uniqueValues,
            sampleRecords = sampleRecords
        )
    }
    
    /**
     * Вычисляет статистику для конкретного поля
     */
    private fun calculateFieldStatistics(records: List<DataRecord>, fieldName: String): FieldStatistics {
        val values = records.mapNotNull { it.data[fieldName] }
        val totalCount = values.size
        val uniqueCount = values.toSet().size
        val nullCount = records.size - totalCount
        
        // Топ значений (топ 10)
        val topValues = values.groupingBy { it }.eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .toMap()
        
        return FieldStatistics(
            fieldName = fieldName,
            totalCount = totalCount,
            uniqueCount = uniqueCount,
            nullCount = nullCount,
            topValues = topValues
        )
    }
}
