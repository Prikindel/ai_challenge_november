package com.prike.domain.indexing

import com.prike.domain.model.TextChunk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Тесты для TextChunker
 */
class TextChunkerTest {
    
    @Test
    fun `test chunking empty text`() {
        val chunker = TextChunker(chunkSize = 100, overlapSize = 10)
        val chunks = chunker.chunk("")
        
        assertTrue(chunks.isEmpty())
    }
    
    @Test
    fun `test chunking short text`() {
        val chunker = TextChunker(chunkSize = 100, overlapSize = 10)
        val text = "Это короткий текст для тестирования."
        val chunks = chunker.chunk(text)
        
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0].content)
    }
    
    @Test
    fun `test chunking long text`() {
        val chunker = TextChunker(chunkSize = 100, overlapSize = 10)
        // Создаём длинный текст (примерно 500 токенов)
        val longText = "Это предложение. ".repeat(100)
        val chunks = chunker.chunk(longText)
        
        assertTrue(chunks.size > 1, "Длинный текст должен быть разбит на несколько чанков")
        
        // Проверяем, что все чанки покрывают весь текст
        val totalLength = chunks.sumOf { it.content.length }
        assertTrue(totalLength >= longText.length * 0.8, "Чанки должны покрывать большую часть текста")
    }
    
    @Test
    fun `test chunking with overlap`() {
        val chunker = TextChunker(chunkSize = 100, overlapSize = 20)
        val text = "Предложение один. Предложение два. Предложение три. ".repeat(20)
        val chunks = chunker.chunk(text)
        
        if (chunks.size > 1) {
            // Проверяем, что есть перекрытие между соседними чанками
            val firstChunkEnd = chunks[0].endIndex
            val secondChunkStart = chunks[1].startIndex
            
            assertTrue(secondChunkStart < firstChunkEnd, "Должно быть перекрытие между чанками")
        }
    }
    
    @Test
    fun `test chunk indices are sequential`() {
        val chunker = TextChunker(chunkSize = 100, overlapSize = 10)
        val text = "Предложение. ".repeat(50)
        val chunks = chunker.chunk(text)
        
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.chunkIndex, "Индексы чанков должны быть последовательными")
        }
    }
    
    @Test
    fun `test chunk boundaries`() {
        val chunker = TextChunker(chunkSize = 100, overlapSize = 10)
        val text = "Это тестовый текст. ".repeat(30)
        val chunks = chunker.chunk(text)
        
        chunks.forEach { chunk ->
            assertTrue(chunk.startIndex >= 0, "Начальный индекс должен быть неотрицательным")
            assertTrue(chunk.endIndex > chunk.startIndex, "Конечный индекс должен быть больше начального")
            assertTrue(chunk.endIndex <= text.length, "Конечный индекс не должен превышать длину текста")
        }
    }
}

