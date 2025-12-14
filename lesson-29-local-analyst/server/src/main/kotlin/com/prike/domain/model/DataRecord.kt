package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Модель для хранения загруженных данных
 */
@Serializable
data class DataRecord(
    val id: String,
    val source: String,  // "csv", "json", "logs"
    val sourceFile: String,  // имя файла
    val data: Map<String, String>,  // ключ-значение из данных (все значения как строки для универсальности)
    val timestamp: Long = System.currentTimeMillis()
)
