package com.prike.data

import com.prike.config.DatabaseConfig
import com.prike.data.repository.initDatabase
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Менеджер для работы с БД
 */
class DatabaseManager(
    private val config: DatabaseConfig,
    private val lessonRoot: File
) {
    private val logger = LoggerFactory.getLogger(DatabaseManager::class.java)
    
    private val dbPath = File(lessonRoot, config.path)
    
    val database: Database by lazy {
        // Создаем директорию для БД, если не существует
        dbPath.parentFile.mkdirs()
        
        logger.info("Initializing database at: ${dbPath.absolutePath}")
        
        Database.connect(
            url = "jdbc:sqlite:${dbPath.absolutePath}",
            driver = "org.sqlite.JDBC"
        )
    }
    
    fun init() {
        logger.info("Initializing database schema...")
        initDatabase(database)
        logger.info("Database initialized successfully")
    }
}
