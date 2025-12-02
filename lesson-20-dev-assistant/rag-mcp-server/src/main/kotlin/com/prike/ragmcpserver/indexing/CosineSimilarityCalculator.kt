package com.prike.ragmcpserver.domain.indexing

import kotlin.math.sqrt

/**
 * Калькулятор косинусного сходства между векторами
 */
class CosineSimilarityCalculator {
    
    /**
     * Вычисляет косинусное сходство между двумя векторами
     * 
     * Формула: cosine_similarity = (A · B) / (||A|| × ||B||)
     * 
     * @param vector1 первый вектор
     * @param vector2 второй вектор
     * @return косинусное сходство в диапазоне [-1; 1]
     * @throws IllegalArgumentException если векторы имеют разную размерность
     */
    fun calculateSimilarity(
        vector1: List<Float>,
        vector2: List<Float>
    ): Float {
        require(vector1.size == vector2.size) {
            "Vectors must have the same size. Got ${vector1.size} and ${vector2.size}"
        }
        
        if (vector1.isEmpty()) {
            return 0f
        }
        
        // Скалярное произведение (dot product)
        val dotProduct = vector1.zip(vector2).sumOf { (a, b) -> 
            a.toDouble() * b.toDouble() 
        }
        
        // Длины векторов (L2 норма)
        val magnitude1 = sqrt(vector1.sumOf { it.toDouble() * it.toDouble() })
        val magnitude2 = sqrt(vector2.sumOf { it.toDouble() * it.toDouble() })
        
        // Если один из векторов нулевой, возвращаем 0
        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0f
        }
        
        // Косинусное сходство
        return (dotProduct / (magnitude1 * magnitude2)).toFloat()
    }
    
    /**
     * Вычисляет сходство для списка векторов с запросом
     * 
     * @param queryVector вектор запроса
     * @param vectors список векторов для сравнения
     * @return список пар (индекс, сходство), отсортированный по убыванию сходства
     */
    fun calculateSimilarities(
        queryVector: List<Float>,
        vectors: List<List<Float>>
    ): List<Pair<Int, Float>> {
        return vectors.mapIndexed { index, vector ->
            index to calculateSimilarity(queryVector, vector)
        }.sortedByDescending { it.second }
    }
}

