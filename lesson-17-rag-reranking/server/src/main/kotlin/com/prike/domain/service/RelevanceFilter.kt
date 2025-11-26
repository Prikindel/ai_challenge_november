package com.prike.domain.service

import com.prike.domain.model.RetrievedChunk
import org.slf4j.LoggerFactory

/**
 * Статистика фильтрации
 */
data class FilterStats(
    val retrieved: Int,           // Количество чанков до фильтрации
    val kept: Int,                // Количество чанков после фильтрации
    val dropped: List<DroppedChunk>, // Отброшенные чанки с причинами
    val avgSimilarityBefore: Float,  // Среднее сходство до фильтрации
    val avgSimilarityAfter: Float   // Среднее сходство после фильтрации
)

/**
 * Информация об отброшенном чанке
 */
data class DroppedChunk(
    val chunkId: String,
    val documentPath: String?,
    val similarity: Float,
    val reason: String  // Причина отбрасывания
)

/**
 * Конфигурация порогового фильтра
 */
data class ThresholdFilterConfig(
    val minSimilarity: Float,  // Минимальное сходство для прохождения фильтра
    val keepTop: Int? = null   // Оставить только top-N чанков (null = без ограничения)
)

/**
 * Фильтр релевантности на основе порога сходства
 * 
 * Удаляет чанки, чьё сходство ниже заданного порога.
 * Это полезно, когда поиск вернул чанки с разным сходством (например, [0.9, 0.8, 0.5, 0.4, 0.3]),
 * и мы хотим отбросить слабые результаты (например, с порогом 0.6 отбросим последние 3).
 * 
 * Опционально может оставить только top-N чанков после фильтрации по порогу.
 * 
 * Пример: если поиск вернул 5 чанков с сходством [0.9, 0.8, 0.5, 0.4, 0.3],
 * а порог = 0.6, то останутся только [0.9, 0.8] - убираем шум.
 */
class RelevanceFilter(
    private val config: ThresholdFilterConfig
) {
    private val logger = LoggerFactory.getLogger(RelevanceFilter::class.java)
    
    /**
     * Фильтрует список чанков по порогу сходства
     * 
     * @param chunks список чанков для фильтрации
     * @return результат фильтрации с отфильтрованными чанками и статистикой
     */
    fun filter(chunks: List<RetrievedChunk>): FilterResult {
        if (chunks.isEmpty()) {
            return FilterResult(
                filteredChunks = emptyList(),
                stats = FilterStats(
                    retrieved = 0,
                    kept = 0,
                    dropped = emptyList(),
                    avgSimilarityBefore = 0f,
                    avgSimilarityAfter = 0f
                )
            )
        }
        
        logger.debug("Filtering ${chunks.size} chunks with threshold=${config.minSimilarity}, keepTop=${config.keepTop}")
        
        // Вычисляем среднее сходство до фильтрации
        val avgSimilarityBefore = chunks.map { it.similarity }.average().toFloat()
        
        // Фильтруем по порогу
        val dropped = mutableListOf<DroppedChunk>()
        val kept = chunks.filter { chunk ->
            if (chunk.similarity < config.minSimilarity) {
                dropped.add(
                    DroppedChunk(
                        chunkId = chunk.chunkId,
                        documentPath = chunk.documentPath,
                        similarity = chunk.similarity,
                        reason = "similarity ${chunk.similarity} < threshold ${config.minSimilarity}"
                    )
                )
                false
            } else {
                true
            }
        }
        
        // Применяем keepTop, если задан
        val finalChunks = if (config.keepTop != null && kept.size > config.keepTop) {
            // Сортируем по сходству (убывание) и берём top-N
            val sorted = kept.sortedByDescending { it.similarity }
            val topChunks = sorted.take(config.keepTop)
            
            // Добавляем отброшенные из-за keepTop в список dropped
            sorted.drop(config.keepTop).forEach { chunk ->
                dropped.add(
                    DroppedChunk(
                        chunkId = chunk.chunkId,
                        documentPath = chunk.documentPath,
                        similarity = chunk.similarity,
                        reason = "keepTop limit: only top ${config.keepTop} chunks kept"
                    )
                )
            }
            
            topChunks
        } else {
            kept
        }
        
        // Вычисляем среднее сходство после фильтрации
        val avgSimilarityAfter = if (finalChunks.isNotEmpty()) {
            finalChunks.map { it.similarity }.average().toFloat()
        } else {
            0f
        }
        
        val stats = FilterStats(
            retrieved = chunks.size,
            kept = finalChunks.size,
            dropped = dropped,
            avgSimilarityBefore = avgSimilarityBefore,
            avgSimilarityAfter = avgSimilarityAfter
        )
        
        logger.info("Filtered ${chunks.size} chunks: kept ${finalChunks.size}, dropped ${dropped.size}")
        
        return FilterResult(
            filteredChunks = finalChunks,
            stats = stats
        )
    }
}

/**
 * Результат фильтрации
 */
data class FilterResult(
    val filteredChunks: List<RetrievedChunk>,
    val stats: FilterStats
)

