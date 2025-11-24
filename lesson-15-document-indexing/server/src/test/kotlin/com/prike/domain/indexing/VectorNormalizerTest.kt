package com.prike.domain.indexing

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Тесты для VectorNormalizer
 */
class VectorNormalizerTest {
    
    private val normalizer = VectorNormalizer()
    
    @Test
    fun `test normalizeTo01 empty vector`() {
        val result = normalizer.normalizeTo01(emptyList())
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun `test normalizeTo01 with dimension`() {
        // Тестируем с размерностью 768 (как у nomic-embed-text)
        val embedding = listOf(123.45f, -67.89f, 234.56f)
        val result = normalizer.normalizeTo01(embedding)
        
        assertEquals(3, result.size)
        assertEquals(123.45f / 3, result[0], 0.001f)
        assertEquals(-67.89f / 3, result[1], 0.001f)
        assertEquals(234.56f / 3, result[2], 0.001f)
    }
    
    @Test
    fun `test normalizeL2 empty vector`() {
        val result = normalizer.normalizeL2(emptyList())
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun `test normalizeL2 unit vector`() {
        // Вектор [3, 4] должен стать [0.6, 0.8] после L2 нормализации
        val embedding = listOf(3f, 4f)
        val result = normalizer.normalizeL2(embedding)
        
        assertEquals(2, result.size)
        assertEquals(0.6f, result[0], 0.001f)
        assertEquals(0.8f, result[1], 0.001f)
        
        // Проверяем, что длина вектора = 1
        val magnitude = sqrt(result.sumOf { it.toDouble() * it.toDouble() })
        assertEquals(1.0, magnitude, 0.001)
    }
    
    @Test
    fun `test normalizeL2 zero vector`() {
        val embedding = listOf(0f, 0f, 0f)
        val result = normalizer.normalizeL2(embedding)
        
        assertEquals(embedding, result)
    }
    
    @Test
    fun `test normalizeMinMax empty vector`() {
        val result = normalizer.normalizeMinMax(emptyList())
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun `test normalizeMinMax`() {
        val embedding = listOf(10f, 20f, 30f)
        val result = normalizer.normalizeMinMax(embedding)
        
        assertEquals(3, result.size)
        assertEquals(0f, result[0], 0.001f) // min -> 0
        assertEquals(0.5f, result[1], 0.001f) // среднее -> 0.5
        assertEquals(1f, result[2], 0.001f) // max -> 1
    }
    
    @Test
    fun `test normalizeMinMax same values`() {
        val embedding = listOf(5f, 5f, 5f)
        val result = normalizer.normalizeMinMax(embedding)
        
        assertEquals(3, result.size)
        assertEquals(0f, result[0], 0.001f)
        assertEquals(0f, result[1], 0.001f)
        assertEquals(0f, result[2], 0.001f)
    }
    
    @Test
    fun `test normalizeTo01 preserves dimension`() {
        val embedding = List(768) { it.toFloat() }
        val result = normalizer.normalizeTo01(embedding)
        
        assertEquals(768, result.size)
    }
}

