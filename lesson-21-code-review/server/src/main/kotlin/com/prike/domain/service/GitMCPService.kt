package com.prike.domain.service

import com.prike.data.client.GitMCPClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Сервис для работы с Git MCP сервером
 * Предоставляет кэшированный доступ к текущей ветке git
 */
class GitMCPService(
    private val gitMCPClient: GitMCPClient,
    private val lessonRoot: File,
    private val gitMCPJarPath: String? = null
) {
    private val logger = LoggerFactory.getLogger(GitMCPService::class.java)
    
    // Кэш для текущей ветки
    private var cachedBranch: String? = null
    private var cacheTimestamp: AtomicLong = AtomicLong(0)
    private val cacheMutex = Mutex()
    
    // Время жизни кэша (5 минут)
    private val CACHE_TTL_MS = 5 * 60 * 1000L
    
    /**
     * Подключение к Git MCP серверу
     */
    suspend fun connect() {
        try {
            gitMCPClient.connectToServer(gitMCPJarPath, lessonRoot)
            logger.info("Git MCP service connected successfully")
        } catch (e: Exception) {
            logger.error("Failed to connect to Git MCP server: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Отключение от Git MCP сервера
     */
    suspend fun disconnect() {
        try {
            gitMCPClient.disconnect()
            logger.info("Git MCP service disconnected")
        } catch (e: Exception) {
            logger.warn("Error disconnecting from Git MCP server: ${e.message}")
        }
    }
    
    /**
     * Получить текущую ветку git репозитория
     * Использует кэширование для уменьшения количества запросов
     * 
     * @return имя текущей ветки или "unknown" при ошибке
     */
    suspend fun getCurrentBranch(): String {
        return cacheMutex.withLock {
            val now = System.currentTimeMillis()
            val cacheAge = now - cacheTimestamp.get()
            
            // Если кэш валиден, возвращаем закэшированное значение
            if (cachedBranch != null && cacheAge < CACHE_TTL_MS) {
                logger.debug("Returning cached git branch: $cachedBranch")
                return@withLock cachedBranch!!
            }
            
            // Обновляем кэш
            try {
                if (!gitMCPClient.isConnected()) {
                    logger.warn("Git MCP client is not connected, attempting to reconnect...")
                    connect()
                }
                
                val branch = gitMCPClient.callTool("get_current_branch")
                cachedBranch = branch
                cacheTimestamp.set(now)
                
                logger.info("Git branch retrieved: $branch")
                return@withLock branch
            } catch (e: Exception) {
                logger.error("Failed to get current git branch: ${e.message}", e)
                // Возвращаем закэшированное значение, если есть, или "unknown"
                return@withLock cachedBranch ?: "unknown"
            }
        }
    }
    
    /**
     * Читает содержимое файла проекта
     * 
     * @param filePath путь к файлу относительно корня проекта
     * @return содержимое файла или null при ошибке
     */
    suspend fun readFile(filePath: String): String? {
        return try {
            if (!gitMCPClient.isConnected()) {
                logger.warn("Git MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = kotlinx.serialization.json.buildJsonObject {
                put("path", kotlinx.serialization.json.JsonPrimitive(filePath))
            }
            
            val content = gitMCPClient.callTool("read_file", arguments)
            logger.debug("File read successfully: $filePath (${content.length} chars)")
            content
        } catch (e: Exception) {
            logger.error("Failed to read file $filePath: ${e.message}", e)
            null
        }
    }
    
    /**
     * Возвращает список файлов в директории
     * 
     * @param dirPath путь к директории относительно корня проекта (по умолчанию ".")
     * @return список файлов и директорий или null при ошибке
     */
    suspend fun listDirectory(dirPath: String = "."): String? {
        return try {
            if (!gitMCPClient.isConnected()) {
                logger.warn("Git MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = kotlinx.serialization.json.buildJsonObject {
                put("path", kotlinx.serialization.json.JsonPrimitive(dirPath))
            }
            
            val listing = gitMCPClient.callTool("list_directory", arguments)
            logger.debug("Directory listed successfully: $dirPath")
            listing
        } catch (e: Exception) {
            logger.error("Failed to list directory $dirPath: ${e.message}", e)
            null
        }
    }
    
    /**
     * Вызвать инструмент Git MCP сервера
     */
    suspend fun callTool(toolName: String, arguments: kotlinx.serialization.json.JsonObject): String {
        return try {
            if (!gitMCPClient.isConnected()) {
                logger.warn("Git MCP client is not connected, attempting to reconnect...")
                connect()
            }
            gitMCPClient.callTool(toolName, arguments)
        } catch (e: Exception) {
            logger.error("Failed to call Git MCP tool $toolName: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Получить список доступных инструментов
     */
    suspend fun listTools(): List<com.prike.data.client.MCPTool> {
        return try {
            if (!gitMCPClient.isConnected()) {
                logger.warn("Git MCP client is not connected, attempting to reconnect...")
                connect()
            }
            gitMCPClient.listTools()
        } catch (e: Exception) {
            logger.error("Failed to list Git MCP tools: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Получить diff между двумя ветками
     * 
     * @param base базовая ветка (например, "main" или "origin/main")
     * @param head целевая ветка (например, "feature-branch" или "HEAD")
     * @return diff между ветками или null при ошибке
     */
    suspend fun getDiff(base: String, head: String): String? {
        return try {
            if (!gitMCPClient.isConnected()) {
                logger.warn("Git MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = kotlinx.serialization.json.buildJsonObject {
                put("base", kotlinx.serialization.json.JsonPrimitive(base))
                put("head", kotlinx.serialization.json.JsonPrimitive(head))
            }
            
            val diff = gitMCPClient.callTool("get_diff", arguments)
            logger.debug("Diff retrieved successfully: ${base}..${head} (${diff.length} chars)")
            diff
        } catch (e: Exception) {
            logger.error("Failed to get diff between $base and $head: ${e.message}", e)
            null
        }
    }
    
    /**
     * Получить список изменённых файлов между двумя ветками
     * 
     * @param base базовая ветка
     * @param head целевая ветка
     * @return список изменённых файлов (по одному на строку) или null при ошибке
     */
    suspend fun getChangedFiles(base: String, head: String): List<String>? {
        return try {
            if (!gitMCPClient.isConnected()) {
                logger.warn("Git MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = kotlinx.serialization.json.buildJsonObject {
                put("base", kotlinx.serialization.json.JsonPrimitive(base))
                put("head", kotlinx.serialization.json.JsonPrimitive(head))
            }
            
            val result = gitMCPClient.callTool("get_changed_files", arguments)
            logger.debug("Changed files retrieved successfully: ${base}..${head}")
            
            // Парсим результат: каждая строка содержит статус и путь к файлу
            // Формат: "M\tpath/to/file.kt" или "A\tpath/to/file.kt"
            val files = result.lines()
                .filter { it.isNotBlank() }
                .map { line ->
                    // Убираем статус (первый символ и табуляцию)
                    val parts = line.split("\t")
                    if (parts.size > 1) parts[1] else parts[0]
                }
            
            files
        } catch (e: Exception) {
            logger.error("Failed to get changed files between $base and $head: ${e.message}", e)
            null
        }
    }
    
    /**
     * Получить содержимое файла (алиас для readFile с явным названием)
     * 
     * @param filePath путь к файлу относительно корня проекта
     * @return содержимое файла или null при ошибке
     */
    suspend fun getFileContent(filePath: String): String? {
        return try {
            if (!gitMCPClient.isConnected()) {
                logger.warn("Git MCP client is not connected, attempting to reconnect...")
                connect()
            }
            
            val arguments = kotlinx.serialization.json.buildJsonObject {
                put("filePath", kotlinx.serialization.json.JsonPrimitive(filePath))
            }
            
            val content = gitMCPClient.callTool("get_file_content", arguments)
            logger.debug("File content retrieved successfully: $filePath (${content.length} chars)")
            content
        } catch (e: Exception) {
            logger.error("Failed to get file content $filePath: ${e.message}", e)
            null
        }
    }
    
    /**
     * Проверка подключения к Git MCP серверу
     */
    fun isConnected(): Boolean {
        return gitMCPClient.isConnected()
    }
}

