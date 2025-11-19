package com.prike.mcpserver.telegram

import com.prike.mcpserver.data.model.TelegramMessage
import com.prike.mcpserver.data.repository.TelegramMessageRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Клиент для получения сообщений из Telegram через polling
 * Сохраняет полученные сообщения в БД
 */
class TelegramBotClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String,
    private val groupId: String,
    private val telegramMessageRepository: TelegramMessageRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val logger = LoggerFactory.getLogger(TelegramBotClient::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private var lastUpdateId: Long = 0
    private var pollingJob: Job? = null
    
    /**
     * Начать polling для получения новых сообщений
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) {
            logger.warn("Polling уже запущен")
            return
        }
        
        pollingJob = scope.launch {
            logger.info("Запуск polling для получения сообщений из Telegram группы: $groupId")
            
            while (isActive) {
                try {
                    val updates = getUpdates()
                    
                    updates.forEach { update ->
                        val message = update.get("message") as? JsonObject
                        if (message != null) {
                            processMessage(message)
                        }
                    }
                    
                    delay(1000) // Задержка между запросами (1 секунда)
                } catch (e: Exception) {
                    logger.error("Ошибка при polling сообщений: ${e.message}", e)
                    delay(5000) // Увеличиваем задержку при ошибке
                }
            }
        }
    }
    
    /**
     * Остановить polling
     */
    fun stopPolling() {
        pollingJob?.cancel()
        logger.info("Polling остановлен")
    }
    
    /**
     * Получить обновления из Telegram
     */
    private suspend fun getUpdates(): List<JsonObject> {
        val response = httpClient.get("$baseUrl/bot$token/getUpdates") {
            contentType(ContentType.Application.Json)
            parameter("offset", lastUpdateId + 1)
            parameter("timeout", 10)
        }
        
        val responseText = response.bodyAsText()
        val result = json.parseToJsonElement(responseText).jsonObject
        
        val ok = result["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!ok) {
            val errorCode = result["error_code"]?.jsonPrimitive?.intOrNull
            val description = result["description"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
            
            // Ошибка 409 означает, что другой экземпляр уже использует polling
            if (errorCode == 409) {
                logger.warn("Polling конфликт (409): $description")
                logger.warn("Другой экземпляр бота уже использует getUpdates. Остановка polling...")
                // Останавливаем polling при конфликте
                stopPolling()
                return emptyList()
            }
            
            logger.error("Ошибка получения обновлений (код $errorCode): $description")
            return emptyList()
        }
        
        val updates = result["result"]?.jsonArray ?: return emptyList()
        
        val updateList = mutableListOf<JsonObject>()
        updates.forEach { updateElement ->
            val update = updateElement.jsonObject
            val updateId = update["update_id"]?.jsonPrimitive?.longOrNull ?: 0
            if (updateId > lastUpdateId) {
                lastUpdateId = updateId
            }
            updateList.add(update)
        }
        
        return updateList
    }
    
    /**
     * Обработать сообщение и сохранить в БД
     */
    private suspend fun processMessage(messageJson: JsonObject) {
        try {
            val messageId = messageJson["message_id"]?.jsonPrimitive?.longOrNull
                ?: return
            
            val chat = messageJson["chat"]?.jsonObject
            val chatId = chat?.get("id")?.jsonPrimitive?.contentOrNull
                ?: return
            
            // Проверяем, что сообщение из нужной группы
            if (chatId != groupId) {
                return
            }
            
            val text = messageJson["text"]?.jsonPrimitive?.contentOrNull
                ?: return // Пропускаем сообщения без текста
            
            val date = messageJson["date"]?.jsonPrimitive?.longOrNull
                ?: return
            
            val from = messageJson["from"]?.jsonObject
            val author = from?.get("first_name")?.jsonPrimitive?.contentOrNull
            
            // Преобразуем timestamp из секунд в миллисекунды
            val timestamp = date * 1000
            
            val telegramMessage = TelegramMessage(
                id = UUID.randomUUID().toString(),
                messageId = messageId,
                groupId = groupId,
                content = text,
                author = author,
                timestamp = timestamp,
                createdAt = System.currentTimeMillis()
            )
            
            // Сохраняем в БД
            telegramMessageRepository.save(telegramMessage).fold(
                onSuccess = {
                    logger.debug("Сообщение сохранено: $messageId от $author")
                },
                onFailure = { error ->
                    logger.error("Ошибка сохранения сообщения: ${error.message}", error)
                }
            )
        } catch (e: Exception) {
            logger.error("Ошибка обработки сообщения: ${e.message}", e)
        }
    }
}

