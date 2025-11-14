package com.prike.domain.repository

import com.prike.data.model.MemoryEntry
import com.prike.data.model.MemoryStats
import java.time.Instant

/**
 * Интерфейс репозитория для работы с внешней памятью
 * Определяет контракт для сохранения и загрузки данных диалога
 * 
 * Реализации могут использовать SQLite, JSON-файл или другие хранилища
 */
interface MemoryRepository {
    /**
     * Сохранить запись в память
     * @param entry запись для сохранения
     * @return Result с Unit при успехе или ошибкой при неудаче
     */
    suspend fun save(entry: MemoryEntry): Result<Unit>
    
    /**
     * Сохранить несколько записей атомарно
     * @param entries список записей для сохранения
     * @return Result с Unit при успехе или ошибкой при неудаче
     */
    suspend fun saveAll(entries: List<MemoryEntry>): Result<Unit>
    
    /**
     * Загрузить все записи из памяти
     * @return Result со списком записей, отсортированных по времени (старые первыми)
     */
    suspend fun loadAll(): Result<List<MemoryEntry>>
    
    /**
     * Найти запись по идентификатору
     * @param id идентификатор записи
     * @return Result с записью или null, если не найдена
     */
    suspend fun findById(id: String): Result<MemoryEntry?>
    
    /**
     * Найти записи в диапазоне дат
     * @param start начало диапазона (включительно)
     * @param end конец диапазона (включительно)
     * @return Result со списком записей в указанном диапазоне
     */
    suspend fun findByDateRange(start: Instant, end: Instant): Result<List<MemoryEntry>>
    
    /**
     * Очистить всю память
     * @return Result с Unit при успехе или ошибкой при неудаче
     */
    suspend fun clear(): Result<Unit>
    
    /**
     * Получить статистику памяти
     * @return Result со статистикой (количество записей, даты и т.д.)
     */
    suspend fun getStats(): Result<MemoryStats>
}

