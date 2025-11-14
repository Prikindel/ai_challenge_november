package com.prike.config

/**
 * Конфигурация внешней памяти
 */
data class MemoryConfig(
    val storageType: StorageType,
    val sqlite: SqliteConfig? = null,
    val json: JsonConfig? = null,
    val limits: MemoryLimits? = null
) {
    enum class StorageType {
        SQLITE,
        JSON
    }
    
    data class SqliteConfig(
        val databasePath: String
    )
    
    data class JsonConfig(
        val filePath: String,
        val prettyPrint: Boolean = true
    )
    
    data class MemoryLimits(
        val maxEntries: Int? = null,
        val maxHistoryDays: Int? = null,
        val autoCleanup: Boolean = false
    )
}

