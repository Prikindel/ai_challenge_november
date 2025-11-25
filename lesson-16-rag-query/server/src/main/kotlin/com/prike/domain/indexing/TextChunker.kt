package com.prike.domain.indexing

import com.prike.domain.model.TextChunk
import java.util.UUID

/**
 * Разбивает текст на чанки с перекрытиями
 * 
 * @param chunkSize размер чанка в токенах (по умолчанию 800)
 * @param overlapSize перекрытие между чанками в токенах (по умолчанию 100)
 */
class TextChunker(
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    private val overlapSize: Int = DEFAULT_OVERLAP_SIZE
) {
    companion object {
        private const val DEFAULT_CHUNK_SIZE = 800
        private const val DEFAULT_OVERLAP_SIZE = 100
        private const val MAX_CHUNKS_LIMIT = 10000 // Защита от бесконечного цикла
    }
    
    private val tokenCounter = TokenCounter()
    private val logger = org.slf4j.LoggerFactory.getLogger(TextChunker::class.java)
    
    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(overlapSize >= 0) { "overlapSize must be non-negative" }
        require(overlapSize < chunkSize) { "overlapSize must be less than chunkSize" }
    }
    
    /**
     * Разбивает текст на чанки с перекрытиями
     * 
     * @param text исходный текст
     * @param documentId идентификатор документа (для генерации ID чанков)
     * @return список чанков
     */
    fun chunk(text: String, documentId: String = "doc-${UUID.randomUUID()}"): List<TextChunk> {
        if (text.isEmpty()) return emptyList()
        
        val chunks = mutableListOf<TextChunk>()
        var currentIndex = 0
        var chunkIndex = 0
        
        while (currentIndex < text.length) {
            // Определяем конец текущего чанка
            val endIndex = findChunkEnd(text, currentIndex, chunkSize)
            
            // Извлекаем содержимое чанка
            val chunkContent = text.substring(currentIndex, endIndex)
            
            // Создаём чанк
            val chunk = TextChunk(
                id = "${documentId}-chunk-${chunkIndex}",
                documentId = documentId,
                content = chunkContent,
                startIndex = currentIndex,
                endIndex = endIndex,
                tokenCount = tokenCounter.countTokens(chunkContent),
                chunkIndex = chunkIndex
            )
            
            chunks.add(chunk)
            
            // Переходим к следующему чанку с учётом перекрытия
            val nextStartIndex = if (endIndex >= text.length) {
                text.length
            } else {
                // Вычитаем overlapSize из текущей позиции, чтобы создать перекрытие
                val overlapStart = (endIndex - overlapSize).coerceAtLeast(currentIndex)
                // Находим границу слова для более аккуратного разбиения
                findWordBoundary(text, overlapStart)
            }
            
            // Если не продвинулись вперёд, принудительно сдвигаемся
            if (nextStartIndex <= currentIndex) {
                currentIndex = endIndex
            } else {
                currentIndex = nextStartIndex
            }
            
            chunkIndex++
            
            // Защита от бесконечного цикла
            if (chunks.size > MAX_CHUNKS_LIMIT) {
                logger.warn("Reached maximum chunks limit ($MAX_CHUNKS_LIMIT), stopping chunking")
                break
            }
        }
        
        return chunks
    }
    
    /**
     * Находит конец чанка, стараясь не разрывать слова и предложения
     */
    private fun findChunkEnd(text: String, startIndex: Int, targetTokens: Int): Int {
        // Приблизительная длина в символах для целевого количества токенов
        val targetLength = targetTokens * 4
        
        var endIndex = (startIndex + targetLength).coerceAtMost(text.length)
        
        // Если мы не достигли конца текста, пытаемся найти границу предложения или слова
        if (endIndex < text.length) {
            // Ищем конец предложения (точка, восклицательный знак, вопросительный знак)
            val sentenceEnd = findLastSentenceBoundary(text, startIndex, endIndex)
            if (sentenceEnd > startIndex) {
                endIndex = sentenceEnd + 1
            } else {
                // Если не нашли конец предложения, ищем границу слова
                val wordEnd = findLastWordBoundary(text, startIndex, endIndex)
                if (wordEnd > startIndex) {
                    endIndex = wordEnd
                }
            }
        }
        
        return endIndex.coerceAtMost(text.length)
    }
    
    /**
     * Находит последнюю границу предложения в диапазоне
     */
    private fun findLastSentenceBoundary(text: String, start: Int, end: Int): Int {
        val sentenceEndings = setOf('.', '!', '?', '\n')
        for (i in end - 1 downTo start) {
            if (sentenceEndings.contains(text[i])) {
                // Проверяем, что это действительно конец предложения (не точка в числе)
                if (i + 1 >= text.length || text[i + 1].isWhitespace() || text[i + 1] == '\n') {
                    return i
                }
            }
        }
        return -1
    }
    
    /**
     * Находит последнюю границу слова в диапазоне
     */
    private fun findLastWordBoundary(text: String, start: Int, end: Int): Int {
        for (i in end - 1 downTo start) {
            if (text[i].isWhitespace() || text[i] == '\n') {
                return i
            }
        }
        return -1
    }
    
    /**
     * Находит границу слова для начала следующего чанка
     */
    private fun findWordBoundary(text: String, position: Int): Int {
        // Ищем начало следующего слова
        var pos = position
        while (pos < text.length && text[pos].isWhitespace()) {
            pos++
        }
        return pos
    }
}

