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
     * Проверка подключения к Git MCP серверу
     */
    fun isConnected(): Boolean {
        return gitMCPClient.isConnected()
    }
}

