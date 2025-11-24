package com.prike.domain.indexing

/**
 * Подсчёт токенов в тексте
 * 
 * Использует приблизительный метод: 1 токен ≈ 4 символа
 * Для более точного подсчёта можно использовать библиотеки типа tiktoken
 */
class TokenCounter {
    companion object {
        private const val CHARS_PER_TOKEN = 4.0 // Приблизительное соотношение: 1 токен ≈ 4 символа
    }
    
    /**
     * Подсчитывает приблизительное количество токенов в тексте
     * 
     * @param text текст для подсчёта
     * @return количество токенов
     * 
     * Примечание: Это упрощённый метод. Для точного подсчёта токенов
     * (например, для конкретной модели) используйте специализированные библиотеки
     * типа tiktoken или аналогичные.
     */
    fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        
        return (text.length / CHARS_PER_TOKEN).toInt().coerceAtLeast(1)
    }
    
    /**
     * Подсчитывает токены для нескольких текстов
     */
    fun countTokens(texts: List<String>): Int {
        return texts.sumOf { countTokens(it) }
    }
}

