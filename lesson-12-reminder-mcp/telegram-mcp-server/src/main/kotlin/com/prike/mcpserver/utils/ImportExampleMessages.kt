package com.prike.mcpserver.utils

import com.prike.mcpserver.Config
import com.prike.mcpserver.data.model.TelegramMessage
import com.prike.mcpserver.data.repository.TelegramMessageRepository
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import kotlin.math.abs

/**
 * Утилита для импорта примеров сообщений из telegram_messages_example.txt в БД
 */
object ImportExampleMessages {
    private val logger = LoggerFactory.getLogger(ImportExampleMessages::class.java)
    
    /**
     * Импортировать сообщения из файла в БД
     */
    fun importFromFile(
        exampleFile: File,
        groupId: String,
        databasePath: String,
        startTimestamp: Long = System.currentTimeMillis() - (24 * 3600 * 1000L) // 24 часа назад
    ): Result<Int> {
        return try {
            if (!exampleFile.exists()) {
                return Result.failure(IllegalArgumentException("File not found: ${exampleFile.absolutePath}"))
            }
            
            logger.info("Reading example messages from: ${exampleFile.absolutePath}")
            val messages = parseExampleFile(exampleFile, groupId, startTimestamp)
            
            if (messages.isEmpty()) {
                logger.warn("No messages found in example file")
                return Result.success(0)
            }
            
            logger.info("Parsed ${messages.size} messages from example file")
            
            // Исправляем путь к БД - должен быть относительно корня урока
            val correctedDbPath = correctDatabasePath(databasePath)
            logger.info("Using database path: $correctedDbPath")
            
            // Проверяем существующие сообщения, чтобы не дублировать
            // Используем проверку по content + author + timestamp (с небольшой погрешностью)
            val repository = TelegramMessageRepository(correctedDbPath)
            val existingMessages = getExistingMessages(repository, groupId)
            
            // Фильтруем сообщения, которые уже есть в БД (по содержимому)
            val newMessages = messages.filter { message ->
                !existingMessages.any { existing ->
                    existing.content == message.content &&
                    existing.author == message.author &&
                    kotlin.math.abs(existing.timestamp - message.timestamp) < 60 * 1000L // В пределах 1 минуты
                }
            }
            
            if (newMessages.isEmpty()) {
                logger.info("All messages from example file already exist in database")
                return Result.success(0)
            }
            
            logger.info("Adding ${newMessages.size} new messages to database (${messages.size - newMessages.size} already exist)")
            
            // Сохраняем сообщения
            val saveResult = repository.saveAll(newMessages)
            
            if (saveResult.isSuccess) {
                logger.info("Successfully imported ${newMessages.size} messages to database")
                Result.success(newMessages.size)
            } else {
                val error = saveResult.exceptionOrNull()
                logger.error("Failed to import messages: ${error?.message}", error)
                Result.failure(error ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            logger.error("Error importing messages: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить список существующих сообщений из БД
     */
    private fun getExistingMessages(repository: TelegramMessageRepository, groupId: String): List<TelegramMessage> {
        return try {
            // Получаем все сообщения за последний год
            val oneYearAgo = System.currentTimeMillis() - (365L * 24 * 3600 * 1000)
            val now = System.currentTimeMillis()
            repository.getMessagesBetween(groupId, oneYearAgo, now)
        } catch (e: Exception) {
            logger.warn("Error getting existing messages: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Исправить путь к БД - должен быть относительно корня урока, а не telegram-mcp-server
     */
    private fun correctDatabasePath(databasePath: String): String {
        // Если путь уже абсолютный, возвращаем как есть
        val dbFile = File(databasePath)
        if (dbFile.isAbsolute) {
            return databasePath
        }
        
        // Если путь относительный, ищем корень урока
        var currentDir = File(System.getProperty("user.dir"))
        
        // Если запущены из telegram-mcp-server, поднимаемся на уровень вверх
        if (currentDir.name == "telegram-mcp-server") {
            currentDir = currentDir.parentFile
        }
        
        // Ищем lesson-12-reminder-mcp
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-12-reminder-mcp") {
                val correctedPath = File(searchDir, databasePath)
                return correctedPath.absolutePath
            }
            
            val lessonDir = File(searchDir, "lesson-12-reminder-mcp")
            if (lessonDir.exists()) {
                val correctedPath = File(lessonDir, databasePath)
                return correctedPath.absolutePath
            }
            
            searchDir = searchDir.parentFile
        }
        
        // Fallback: возвращаем исходный путь
        return databasePath
    }
    
    /**
     * Парсить файл с примерами сообщений
     * Формат:
     * Пользователь 1 (Иван):
     * Текст сообщения
     * ---
     */
    private fun parseExampleFile(
        file: File,
        groupId: String,
        startTimestamp: Long
    ): List<TelegramMessage> {
        val content = file.readText()
        val messages = mutableListOf<TelegramMessage>()
        
        // Разбиваем на блоки по разделителю "---"
        val blocks = content.split("---").filter { it.trim().isNotEmpty() }
        
        var currentTimestamp = startTimestamp
        var messageIdCounter = 1000L // Начинаем с 1000, чтобы не конфликтовать с реальными ID
        
        for (block in blocks) {
            val lines = block.trim().lines().filter { it.isNotBlank() }
            if (lines.size < 2) continue
            
            // Первая строка - автор (например, "Пользователь 1 (Иван):")
            val authorLine = lines[0]
            val author = extractAuthor(authorLine)
            
            // Остальные строки - текст сообщения
            val messageText = lines.drop(1).joinToString("\n").trim()
            
            if (messageText.isBlank()) continue
            
            // Создаем сообщение
            val message = TelegramMessage(
                id = UUID.randomUUID().toString(),
                messageId = messageIdCounter++,
                groupId = groupId,
                content = messageText,
                author = author,
                timestamp = currentTimestamp,
                createdAt = System.currentTimeMillis()
            )
            
            messages.add(message)
            
            // Увеличиваем timestamp на 5 минут для следующего сообщения
            currentTimestamp += 5 * 60 * 1000L
        }
        
        return messages
    }
    
    /**
     * Извлечь имя автора из строки
     * Примеры: "Пользователь 1 (Иван):" -> "Иван"
     *          "Пользователь 2 (Мария):" -> "Мария"
     */
    private fun extractAuthor(authorLine: String): String? {
        val trimmed = authorLine.trim()
        
        // Ищем паттерн (Имя)
        val regex = "\\(([^)]+)\\)".toRegex()
        val match = regex.find(trimmed)
        if (match != null) {
            return match.groupValues[1]
        }
        
        // Если паттерн не найден, возвращаем всю строку без двоеточия
        return trimmed.removeSuffix(":").trim().takeIf { it.isNotBlank() }
    }
}

/**
 * Main функция для запуска импорта из командной строки
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("ImportExampleMessages")
    
    try {
        // Загружаем конфигурацию
        val config = Config.load()
        val groupId = config.telegram.groupId
        val databasePath = config.telegram.databasePath
        
        // Находим файл с примерами
        val exampleFileNullable = findExampleFile()
        if (exampleFileNullable == null) {
            logger.error("Example file not found. Expected: telegram_messages_example.txt in lesson-12-reminder-mcp directory")
            System.exit(1)
        }
        
        val exampleFile = exampleFileNullable!!
        
        if (!exampleFile.exists()) {
            logger.error("Example file does not exist: ${exampleFile.absolutePath}")
            System.exit(1)
        }
        
        logger.info("Starting import of example messages...")
        logger.info("Group ID: $groupId")
        logger.info("Database: $databasePath")
        logger.info("Example file: ${exampleFile.absolutePath}")
        
        // Импортируем сообщения (начинаем с 24 часов назад)
        val startTimestamp = System.currentTimeMillis() - (24 * 3600 * 1000L)
        val result = ImportExampleMessages.importFromFile(
            exampleFile = exampleFile,
            groupId = groupId,
            databasePath = databasePath,
            startTimestamp = startTimestamp
        )
        
        when {
            result.isSuccess -> {
                val count = result.getOrNull() ?: 0
                logger.info("Import completed successfully. Imported $count messages.")
                System.exit(0)
            }
            else -> {
                val error = result.exceptionOrNull()
                logger.error("Import failed: ${error?.message}", error)
                System.exit(1)
            }
        }
    } catch (e: Exception) {
        logger.error("Fatal error during import: ${e.message}", e)
        System.exit(1)
    }
}

/**
 * Найти файл с примерами сообщений
 */
private fun findExampleFile(): File? {
    var currentDir = File(System.getProperty("user.dir"))
    
    // Если запущены из telegram-mcp-server
    if (currentDir.name == "telegram-mcp-server") {
        val file = File(currentDir.parentFile, "telegram_messages_example.txt")
        if (file.exists()) return file
    }
    
    // Ищем lesson-12-reminder-mcp вверх по дереву
    var searchDir = currentDir
    while (searchDir != null && searchDir.parentFile != null) {
        if (searchDir.name == "lesson-12-reminder-mcp") {
            val file = File(searchDir, "telegram_messages_example.txt")
            if (file.exists()) return file
        }
        
        val lessonDir = File(searchDir, "lesson-12-reminder-mcp")
        if (lessonDir.exists()) {
            val file = File(lessonDir, "telegram_messages_example.txt")
            if (file.exists()) return file
        }
        
        searchDir = searchDir.parentFile
    }
    
    return null
}

