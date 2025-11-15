package com.prike.data.repository

import com.prike.data.model.MemoryEntry
import com.prike.data.model.MemoryMetadata
import com.prike.data.model.MemoryStats
import com.prike.data.model.MessageRole
import com.prike.domain.repository.MemoryRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

/**
 * Реализация MemoryRepository для JSON-файла
 * 
 * Сохраняет записи памяти в JSON-файл
 * Обеспечивает персистентность данных между запусками приложения
 */
class JsonMemoryRepository(
    private val filePath: String,
    private val prettyPrint: Boolean = true
) : MemoryRepository {
    private val logger = LoggerFactory.getLogger(JsonMemoryRepository::class.java)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = this@JsonMemoryRepository.prettyPrint
    }
    
    /**
     * DTO для сериализации списка записей
     */
    @Serializable
    private data class MemoryStorage(
        val entries: List<MemoryEntryDto> = emptyList()
    )
    
    /**
     * DTO для сериализации одной записи
     */
    @Serializable
    private data class MemoryEntryDto(
        val id: String,
        val role: String,
        val content: String,
        val timestamp: Long,
        val metadata: MemoryMetadataDto? = null
    ) {
        fun toMemoryEntry(): MemoryEntry {
            return MemoryEntry(
                id = id,
                role = MessageRole.valueOf(role),
                content = content,
                timestamp = timestamp,
                metadata = metadata?.toMemoryMetadata()
            )
        }
        
        companion object {
            fun fromMemoryEntry(entry: MemoryEntry): MemoryEntryDto {
                return MemoryEntryDto(
                    id = entry.id,
                    role = entry.role.name,
                    content = entry.content,
                    timestamp = entry.timestamp,
                    metadata = entry.metadata?.let { MemoryMetadataDto.fromMemoryMetadata(it) }
                )
            }
        }
    }
    
    @Serializable
    private data class MemoryMetadataDto(
        val model: String? = null,
        val promptTokens: Int? = null,
        val completionTokens: Int? = null,
        val totalTokens: Int? = null
    ) {
        fun toMemoryMetadata(): MemoryMetadata {
            return MemoryMetadata(
                model = model,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens
            )
        }
        
        companion object {
            fun fromMemoryMetadata(metadata: MemoryMetadata): MemoryMetadataDto {
                return MemoryMetadataDto(
                    model = metadata.model,
                    promptTokens = metadata.promptTokens,
                    completionTokens = metadata.completionTokens,
                    totalTokens = metadata.totalTokens
                )
            }
        }
    }
    
    /**
     * Получить файл для хранения
     */
    private fun getStorageFile(): File {
        val file = File(filePath)
        val dir = file.parentFile
        if (dir != null && !dir.exists()) {
            dir.mkdirs()
        }
        return file
    }
    
    /**
     * Загрузить данные из файла
     */
    private fun loadFromFile(): MemoryStorage {
        val file = getStorageFile()
        return if (file.exists() && file.length() > 0) {
            try {
                val content = file.readText()
                json.decodeFromString<MemoryStorage>(content)
            } catch (e: Exception) {
                logger.warn("Ошибка чтения JSON файла, создаю новый: ${e.message}")
                MemoryStorage()
            }
        } else {
            MemoryStorage()
        }
    }
    
    /**
     * Сохранить данные в файл
     */
    private fun saveToFile(storage: MemoryStorage): Result<Unit> {
        return try {
            val file = getStorageFile()
            val content = json.encodeToString(storage)
            file.writeText(content)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Ошибка записи в JSON файл: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun save(entry: MemoryEntry): Result<Unit> {
        return saveAll(listOf(entry))
    }
    
    override suspend fun saveAll(entries: List<MemoryEntry>): Result<Unit> {
        if (entries.isEmpty()) {
            return Result.success(Unit)
        }
        
        return try {
            val storage = loadFromFile()
            val newEntryIds = entries.map { it.id }.toSet()
            
            // Обновляем существующие и добавляем новые записи
            val updatedEntries = storage.entries
                .filter { it.id !in newEntryIds }
                .toMutableList()
            
            entries.forEach { entry ->
                updatedEntries.add(MemoryEntryDto.fromMemoryEntry(entry))
            }
            
            // Сортируем по времени
            updatedEntries.sortBy { it.timestamp }
            
            val newStorage = MemoryStorage(entries = updatedEntries)
            saveToFile(newStorage)
        } catch (e: Exception) {
            logger.error("Ошибка сохранения записей в JSON: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun loadAll(): Result<List<MemoryEntry>> {
        return try {
            val storage = loadFromFile()
            val entries = storage.entries.map { it.toMemoryEntry() }
            Result.success(entries)
        } catch (e: Exception) {
            logger.error("Ошибка загрузки записей из JSON: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun findById(id: String): Result<MemoryEntry?> {
        return try {
            val storage = loadFromFile()
            val entry = storage.entries.find { it.id == id }?.toMemoryEntry()
            Result.success(entry)
        } catch (e: Exception) {
            logger.error("Ошибка поиска записи в JSON: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun findByDateRange(start: Instant, end: Instant): Result<List<MemoryEntry>> {
        return try {
            val storage = loadFromFile()
            val startMillis = start.toEpochMilli()
            val endMillis = end.toEpochMilli()
            val entries = storage.entries
                .filter { it.timestamp >= startMillis && it.timestamp <= endMillis }
                .map { it.toMemoryEntry() }
            Result.success(entries)
        } catch (e: Exception) {
            logger.error("Ошибка поиска записей по диапазону дат в JSON: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun clear(): Result<Unit> {
        return try {
            val newStorage = MemoryStorage()
            saveToFile(newStorage)
            logger.info("Память JSON очищена")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Ошибка очистки памяти JSON: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getStats(): Result<MemoryStats> {
        return try {
            val storage = loadFromFile()
            val entries = storage.entries.map { it.toMemoryEntry() }
            
            val userMessages = entries.count { it.role == MessageRole.USER }
            val assistantMessages = entries.count { it.role == MessageRole.ASSISTANT }
            val oldestEntry = entries.minByOrNull { it.timestamp }?.getTimestampInstant()
            val newestEntry = entries.maxByOrNull { it.timestamp }?.getTimestampInstant()
            
            val stats = MemoryStats(
                totalEntries = entries.size,
                userMessages = userMessages,
                assistantMessages = assistantMessages,
                oldestEntry = oldestEntry,
                newestEntry = newestEntry
            )
            
            Result.success(stats)
        } catch (e: Exception) {
            logger.error("Ошибка получения статистики из JSON: ${e.message}", e)
            Result.failure(e)
        }
    }
}

