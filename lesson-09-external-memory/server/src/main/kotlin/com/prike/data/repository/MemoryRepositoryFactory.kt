package com.prike.data.repository

import com.prike.Config
import com.prike.config.MemoryConfig
import com.prike.domain.repository.MemoryRepository
import java.io.File

/**
 * Фабрика для создания репозитория памяти по конфигурации
 * 
 * Выбирает реализацию (SQLite или JSON) на основе конфигурации
 */
object MemoryRepositoryFactory {
    /**
     * Создать репозиторий памяти на основе конфигурации
     * @param config конфигурация памяти
     * @param lessonRoot корневая директория урока (для относительных путей)
     * @return репозиторий памяти
     */
    fun create(config: MemoryConfig, lessonRoot: File): MemoryRepository {
        return when (config.storageType) {
            MemoryConfig.StorageType.SQLITE -> {
                val sqliteConfig = config.sqlite
                    ?: throw IllegalStateException("Конфигурация SQLite не найдена")
                
                val dbPath = resolvePath(sqliteConfig.databasePath, lessonRoot)
                SqliteMemoryRepository(databasePath = dbPath)
            }
            
            MemoryConfig.StorageType.JSON -> {
                val jsonConfig = config.json
                    ?: throw IllegalStateException("Конфигурация JSON не найдена")
                
                val jsonPath = resolvePath(jsonConfig.filePath, lessonRoot)
                JsonMemoryRepository(
                    filePath = jsonPath,
                    prettyPrint = jsonConfig.prettyPrint
                )
            }
        }
    }
    
    /**
     * Разрешить путь (абсолютный или относительный к корню урока)
     */
    private fun resolvePath(path: String, lessonRoot: File): String {
        val file = File(path)
        return if (file.isAbsolute) {
            path
        } else {
            File(lessonRoot, path).absolutePath
        }
    }
}

