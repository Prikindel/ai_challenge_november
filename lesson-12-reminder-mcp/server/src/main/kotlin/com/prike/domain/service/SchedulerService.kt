package com.prike.domain.service

import com.prike.data.client.MCPClientManager
import com.prike.data.repository.Summary
import com.prike.data.repository.SummaryRepository
import com.prike.domain.agent.LLMWithSummaryAgent
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Конфигурация планировщика
 */
data class SchedulerConfig(
    val enabled: Boolean,
    val intervalMinutes: Int,
    val periodHours: Int,
    val activeSource: String,  // "web_chat", "telegram", "both"
    val telegramDeliveryEnabled: Boolean,
    val telegramUserId: String?
)

/**
 * Сервис планировщика для автоматической генерации summary
 */
class SchedulerService(
    private val llmAgent: LLMWithSummaryAgent,
    private val summaryRepository: SummaryRepository,
    private val mcpClientManager: MCPClientManager,
    private val config: SchedulerConfig
) {
    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    
    /**
     * Запустить планировщик
     */
    fun start() {
        if (!config.enabled) {
            logger.info("Scheduler is disabled in configuration")
            return
        }
        
        if (job != null && job!!.isActive) {
            logger.warn("Scheduler is already running")
            return
        }
        
        logger.info("Starting scheduler: interval=${config.intervalMinutes}min, period=${config.periodHours}h, source=${config.activeSource}")
        logger.info("First summary will be generated in ${config.intervalMinutes} minutes, then every ${config.intervalMinutes} minutes")
        
        job = scope.launch {
            // Ждём первый интервал перед первой генерацией (X минут после старта планировщика)
            delay(config.intervalMinutes * 60 * 1000L)
            
            // Бесконечный цикл генерации summary каждые X минут
            while (isActive) {
                try {
                    logger.info("Starting scheduled summary generation")
                    generateSummary()
                    logger.info("Scheduled summary generation completed")
                } catch (e: Exception) {
                    logger.error("Error in scheduler iteration: ${e.message}", e)
                }
                
                // Ждём до следующего запуска (X минут)
                logger.debug("Waiting ${config.intervalMinutes} minutes until next summary generation")
                delay(config.intervalMinutes * 60 * 1000L)
            }
        }
    }
    
    /**
     * Остановить планировщик
     */
    fun stop() {
        job?.cancel()
        job = null
        logger.info("Scheduler stopped")
    }
    
    /**
     * Генерировать summary для активного источника
     */
    private suspend fun generateSummary() {
        try {
            logger.info("Starting summary generation for source: ${config.activeSource}")
            
            // 1. Вычисляем период
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (config.periodHours * 3600 * 1000L)
            
            logger.debug("Period: ${java.time.Instant.ofEpochMilli(startTime)} - ${java.time.Instant.ofEpochMilli(endTime)}")
            
            // 2. Генерируем summary через LLM
            val summaryText = llmAgent.generateSummary(
                source = config.activeSource,
                startTime = startTime,
                endTime = endTime
            )
            
            // 3. Сохраняем summary в БД
            val summary = Summary(
                id = UUID.randomUUID().toString(),
                source = config.activeSource,
                periodStart = startTime,
                periodEnd = endTime,
                summaryText = summaryText,
                messageCount = 0, // TODO: можно подсчитать из данных
                generatedAt = System.currentTimeMillis(),
                deliveredToTelegram = false,
                llmModel = null // TODO: можно сохранить модель из конфига
            )
            
            val saveResult = summaryRepository.save(summary)
            if (saveResult.isFailure) {
                logger.error("Failed to save summary: ${saveResult.exceptionOrNull()?.message}")
                return
            }
            
            logger.info("Summary saved with ID: ${summary.id}")
            
            // 4. Отправляем summary в Telegram пользователю через MCP
            if (config.telegramDeliveryEnabled && config.telegramUserId != null) {
                try {
                    // Используем MCP инструмент send_telegram_message из telegram источника
                    val arguments = buildJsonObject {
                        put("userId", config.telegramUserId)
                        put("message", summaryText)
                    }
                    
                    val result = mcpClientManager.callTool("telegram", "send_telegram_message", arguments)
                    
                    // Отмечаем как отправленное
                    summaryRepository.markAsDelivered(summary.id)
                    logger.info("Summary delivered to Telegram user via MCP: ${config.telegramUserId}")
                } catch (e: Exception) {
                    logger.error("Failed to send summary to Telegram via MCP: ${e.message}", e)
                }
            } else {
                logger.debug("Telegram delivery is disabled or not configured")
            }
            
            logger.info("Summary generation completed successfully")
        } catch (e: Exception) {
            logger.error("Error generating summary: ${e.message}", e)
        }
    }
    
    /**
     * Получить статус планировщика
     */
    fun getStatus(): SchedulerStatus {
        return SchedulerStatus(
            isRunning = job?.isActive ?: false,
            enabled = config.enabled,
            intervalMinutes = config.intervalMinutes,
            periodHours = config.periodHours,
            activeSource = config.activeSource
        )
    }
    
    /**
     * Статус планировщика
     */
    data class SchedulerStatus(
        val isRunning: Boolean,
        val enabled: Boolean,
        val intervalMinutes: Int,
        val periodHours: Int,
        val activeSource: String
    )
}

