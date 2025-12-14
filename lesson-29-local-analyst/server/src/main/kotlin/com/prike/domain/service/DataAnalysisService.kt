package com.prike.domain.service

import com.prike.data.repository.DataRepository
import org.slf4j.LoggerFactory

/**
 * Сервис для анализа данных через локальную LLM
 */
class DataAnalysisService(
    private val dataRepository: DataRepository,
    private val llmService: LLMService,
    private val statisticsService: DataStatisticsService = DataStatisticsService()
) {
    private val logger = LoggerFactory.getLogger(DataAnalysisService::class.java)
    
    /**
     * Анализирует вопрос на основе загруженных данных
     * 
     * @param question аналитический вопрос
     * @param source источник данных (csv, json, logs) или null для всех
     * @param limit максимальное количество записей для анализа
     * @return ответ от LLM
     */
    suspend fun analyzeQuestion(
        question: String,
        source: String? = null,
        limit: Int = 1000
    ): String {
        logger.info("Analyzing question: $question (source: $source, limit: $limit)")
        
        // 1. Получить данные из БД
        val records = if (source != null) {
            dataRepository.getRecordsBySource(source, limit)
        } else {
            dataRepository.getRecords(limit)
        }
        
        if (records.isEmpty()) {
            return "Нет данных для анализа. Пожалуйста, загрузите данные (CSV, JSON или логи) перед задаванием вопросов."
        }
        
        logger.info("Retrieved ${records.size} records for analysis")
        
        // 2. Подготовить данные для LLM
        val dataSummary = prepareDataSummary(records)
        
        // Логируем размер данных, отправляемых в LLM
        val summarySize = dataSummary.length
        val estimatedTokens = summarySize / 3  // Приблизительная оценка: 1 токен ≈ 3 символа
        logger.info("Prepared data summary for LLM: ${records.size} records analyzed, summary size: $summarySize chars (~$estimatedTokens tokens)")
        logger.info("Full records sent to LLM: 3 examples (out of ${records.size} total records)")
        
        // 3. Запросить анализ у LLM
        return try {
            val response = llmService.generateResponse(
                userMessage = question,
                context = dataSummary,
                templateId = "analyst"
            )
            response.answer
        } catch (e: Exception) {
            logger.error("Error analyzing question: ${e.message}", e)
            "Ошибка при анализе данных: ${e.message}"
        }
    }
    
    /**
     * Подготавливает сводку данных для LLM
     */
    private fun prepareDataSummary(records: List<com.prike.domain.model.DataRecord>): String {
        val stats = statisticsService.calculateStatistics(records)
        
        val summary = StringBuilder()
        summary.appendLine("=== СТАТИСТИКА ДАННЫХ ===")
        summary.appendLine("Всего записей: ${stats.totalRecords}")
        summary.appendLine()
        
        // Статистика по источникам
        summary.appendLine("По источникам:")
        stats.bySource.forEach { (source, count) ->
            summary.appendLine("  - $source: $count записей")
        }
        summary.appendLine()
        
        // Статистика по полям
        summary.appendLine("=== СТАТИСТИКА ПО ПОЛЯМ ===")
        stats.fieldStats.forEach { (fieldName, fieldStats) ->
            summary.appendLine("Поле: $fieldName")
            summary.appendLine("  Всего значений: ${fieldStats.totalCount}")
            summary.appendLine("  Уникальных: ${fieldStats.uniqueCount}")
            if (fieldStats.nullCount > 0) {
                summary.appendLine("  Пустых: ${fieldStats.nullCount}")
            }
            if (fieldStats.topValues.isNotEmpty()) {
                summary.appendLine("  Топ значения:")
                fieldStats.topValues.toList().take(5).forEach { (value, count) ->
                    summary.appendLine("    - \"$value\": $count раз")
                }
            }
            summary.appendLine()
        }
        
        // Примеры данных (только первые 3 для экономии токенов)
        if (stats.sampleRecords.isNotEmpty()) {
            val examplesCount = stats.sampleRecords.size.coerceAtMost(3)
            summary.appendLine("=== ПРИМЕРЫ ДАННЫХ (первые $examplesCount из ${stats.totalRecords}) ===")
            summary.appendLine("Примечание: Для анализа используется статистика по всем ${stats.totalRecords} записям, но полные тексты показаны только для $examplesCount примеров.")
            summary.appendLine()
            stats.sampleRecords.take(3).forEachIndexed { index, record ->
                summary.appendLine("Запись ${index + 1}:")
                record.forEach { (key, value) ->
                    val displayValue = if (value.length > 100) {
                        value.take(100) + "..."
                    } else {
                        value
                    }
                    summary.appendLine("  $key: $displayValue")
                }
                summary.appendLine()
            }
        }
        
        // Дополнительная информация для логов
        val logRecords = records.filter { it.source == "logs" }
        if (logRecords.isNotEmpty()) {
            summary.appendLine("=== АНАЛИЗ ЛОГОВ ===")
            val levels = logRecords.mapNotNull { it.data["level"] }
            val levelCounts = levels.groupingBy { it }.eachCount()
            summary.appendLine("Уровни логов:")
            levelCounts.forEach { (level, count) ->
                summary.appendLine("  - $level: $count")
            }
            summary.appendLine()
        }
        
        return summary.toString()
    }
}
