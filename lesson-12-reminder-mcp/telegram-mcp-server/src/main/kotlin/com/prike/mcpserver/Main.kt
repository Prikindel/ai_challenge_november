package com.prike.mcpserver

import com.prike.mcpserver.data.repository.TelegramMessageRepository
import com.prike.mcpserver.server.MCPServer
import com.prike.mcpserver.telegram.TelegramBotClient
import com.prike.mcpserver.telegram.TelegramBotService
import com.prike.mcpserver.tools.TelegramMessagesTool
import com.prike.mcpserver.tools.handlers.GetTelegramMessagesHandler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("TelegramMCPServerMain")
    
    try {
        // Загрузка конфигурации
        val config = Config.load()
        logger.info("Configuration loaded: ${config.serverInfo.name}")
        
        // Создание HTTP клиента для Telegram API
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // Создание репозитория для Telegram сообщений
        val telegramMessageRepository = TelegramMessageRepository(
            databasePath = config.telegram.databasePath
        )
        
        // Создание обработчика инструмента для Telegram
        // groupId передается из конфигурации (env)
        val getTelegramMessagesHandler = GetTelegramMessagesHandler(
            telegramMessageRepository = telegramMessageRepository,
            groupId = config.telegram.groupId
        )
        
        // Создание инструмента для Telegram
        val telegramMessagesTool = TelegramMessagesTool(getTelegramMessagesHandler)
        
        // Создание Telegram Bot Client для получения сообщений
        val telegramBotClient = TelegramBotClient(
            httpClient = httpClient,
            baseUrl = "https://api.telegram.org",
            token = config.telegram.botToken,
            groupId = config.telegram.groupId,
            telegramMessageRepository = telegramMessageRepository
        )
        
        // Создание Telegram Bot Service для отправки summary
        val telegramBotService = TelegramBotService(
            httpClient = httpClient,
            baseUrl = "https://api.telegram.org",
            token = config.telegram.botToken
        )
        
        // Запуск polling для получения сообщений (если включено в конфигурации)
        var pollingStarted = false
        if (config.telegram.enablePolling) {
            telegramBotClient.startPolling()
            pollingStarted = true
            logger.info("Telegram polling запущен")
        } else {
            logger.info("Telegram polling отключен в конфигурации (enablePolling: false)")
            logger.info("Убедитесь, что polling запущен в другом экземпляре или используется webhook")
        }
        
        // Создание и запуск MCP сервера
        val server = MCPServer(
            serverInfo = Implementation(
                name = config.serverInfo.name,
                version = config.serverInfo.version
            )
        ) { mcpServer ->
            // Регистрация инструментов
            telegramMessagesTool.register(mcpServer)
            logger.info("Инструмент get_telegram_messages зарегистрирован")
        }
        
        // Добавляем shutdown hook для корректного завершения
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Остановка MCP сервера...")
            if (pollingStarted) {
                telegramBotClient.stopPolling()
            }
        })
        
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start MCP server: ${e.message}", e)
        System.exit(1)
    }
}
