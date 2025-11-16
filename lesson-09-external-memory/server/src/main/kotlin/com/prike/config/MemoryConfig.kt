package com.prike.config

/**
 * Конфигурация внешней памяти
 */
data class MemoryConfig(
    val storageType: StorageType,
    val sqlite: SqliteConfig? = null,
    val json: JsonConfig? = null,
    val limits: MemoryLimits? = null,
    val summarization: SummarizationConfig? = null
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

    /**
     * Конфигурация суммаризации
     */
    data class SummarizationConfig(
        val enabled: Boolean = true,
        val userMessagesPerSummary: Int = 10,  // каждые N пользовательских сообщений
        val userMessagesPerSegment: Int = 100, // размер сегмента
        val model: String? = null,
        val temperature: Double? = 0.2,
        val maxTokens: Int? = 900
    )
}

