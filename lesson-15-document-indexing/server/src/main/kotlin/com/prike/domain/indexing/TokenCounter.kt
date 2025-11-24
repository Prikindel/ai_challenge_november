package com.prike.domain.indexing

/**
 * Подсчёт токенов в тексте
 * 
 * Использует приблизительный метод: 1 токен ≈ 4 символа
 * Для более точного подсчёта можно использовать библиотеки типа tiktoken
 */
class TokenCounter {
    /**
     * Подсчитывает приблизительное количество токенов в тексте
     * 
     * @param text текст для подсчёта
     * @return количество токенов
     */
    fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        
        // Приблизительный подсчёт: 1 токен ≈ 4 символа
        // Это упрощённый метод, для точного подсчёта нужны специальные библиотеки
        return (text.length / 4.0).toInt().coerceAtLeast(1)
    }
    
    /**
     * Подсчитывает токены для нескольких текстов
     */
    fun countTokens(texts: List<String>): Int {
        return texts.sumOf { countTokens(it) }
    }
}

