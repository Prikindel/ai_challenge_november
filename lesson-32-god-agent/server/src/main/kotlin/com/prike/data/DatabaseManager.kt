package com.prike.data

import com.prike.config.DatabaseConfig
import com.prike.data.repository.initDatabase
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
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
        
        // Сначала подключаемся через прямой JDBC для установки PRAGMA (нельзя делать внутри транзакции)
        val jdbcUrl = "jdbc:sqlite:${dbPath.absolutePath}"
        java.sql.DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                // Устанавливаем PRAGMA до создания Exposed Database
                statement.execute("PRAGMA journal_mode=WAL")
                statement.execute("PRAGMA busy_timeout=30000") // 30 секунд timeout
                statement.execute("PRAGMA synchronous=NORMAL") // Баланс между производительностью и надежностью
            }
        }
        
        // Теперь создаем Exposed Database
        val db = Database.connect(
            url = jdbcUrl,
            driver = "org.sqlite.JDBC"
        )
        
        logger.info("Database initialized with WAL mode and busy_timeout=30000")
        
        db
    }
    
    fun init() {
        logger.info("Initializing database schema...")
        initDatabase(database)
        logger.info("Database initialized successfully")
    }
}
