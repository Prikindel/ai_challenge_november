package com.prike.domain.indexing

import kotlin.math.sqrt

/**
 * Нормализация векторов эмбеддингов
 */
class VectorNormalizer {
    
    /**
     * Нормализация к диапазону [0; 1] через деление на размерность
     * 
     * Для модели nomic-embed-text размерность = 768
     * Формула: normalized_value = value / dimension
     * 
     * @param embedding исходный вектор эмбеддинга
     * @return нормализованный вектор
     */
    fun normalizeTo01(embedding: List<Float>): List<Float> {
        if (embedding.isEmpty()) {
            return emptyList()
        }
        
        val dimension = embedding.size
        return embedding.map { it / dimension }
    }
    
    /**
     * L2 нормализация (единичный вектор)
     * 
     * Приводит вектор к единичной длине, сохраняя направление
     * Формула: normalized_value = value / magnitude
     * где magnitude = sqrt(sum(value²))
     * 
     * @param embedding исходный вектор эмбеддинга
     * @return нормализованный вектор (единичной длины)
     */
    fun normalizeL2(embedding: List<Float>): List<Float> {
        if (embedding.isEmpty()) {
            return emptyList()
        }
        
        val magnitude = sqrt(embedding.sumOf { it.toDouble() * it.toDouble() })
        
        if (magnitude == 0.0) {
            // Если вектор нулевой, возвращаем его как есть
            return embedding
        }
        
        return embedding.map { (it / magnitude).toFloat() }
    }
    
    /**
     * Min-Max нормализация к диапазону [0; 1]
     * 
     * Формула: normalized_value = (value - min) / (max - min)
     * 
     * @param embedding исходный вектор эмбеддинга
     * @return нормализованный вектор в диапазоне [0; 1]
     */
    fun normalizeMinMax(embedding: List<Float>): List<Float> {
        if (embedding.isEmpty()) {
            return emptyList()
        }
        
        val min = embedding.minOrNull() ?: 0f
        val max = embedding.maxOrNull() ?: 0f
        
        if (max == min) {
            // Если все значения одинаковы, возвращаем нулевой вектор
            return List(embedding.size) { 0f }
        }
        
        val range = max - min
        return embedding.map { (it - min) / range }
    }
}

