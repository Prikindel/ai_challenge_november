package com.prike.config

import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Конфигурация источника данных
 */
data class DataSourceConfig(
    val name: String,
    val description: String,
    val enabled: Boolean,
    val mcpServer: MCPServerConfig
)

/**
 * Конфигурация MCP сервера
 */
data class MCPServerConfig(
    val jarPath: String?  // null или "class" означает режим разработки
)

/**
 * Конфигурация сервера
 */
data class ServerConfig(
    val port: Int,
    val host: String
)

/**
 * Конфигурация AI
 */
data class AIConfig(
    val provider: String,
    val apiKey: String,
    val model: String
)

/**
 * Конфигурация доставки summary
 */
data class DeliveryConfig(
    val telegram: TelegramDeliveryConfig
)

/**
 * Конфигурация доставки в Telegram
 */
data class TelegramDeliveryConfig(
    val enabled: Boolean,
    val userId: String?
)

/**
 * Конфигурация планировщика
 */
data class SchedulerConfig(
    val enabled: Boolean,
    val intervalMinutes: Int,
    val periodHours: Int,
    val activeSource: String,
    val delivery: DeliveryConfig
)

/**
 * Главная конфигурация приложения
 */
data class AppConfig(
    val server: ServerConfig,
    val dataSources: Map<String, DataSourceConfig>,
    val ai: AIConfig,
    val scheduler: SchedulerConfig
)

object Config {
    private val dotenv = run {
        val projectRoot = findProjectRoot()
        try {
            dotenv {
                directory = projectRoot
                filename = ".env"
                ignoreIfMissing = true
            }
        } catch (e: Exception) {
            dotenv {
                ignoreIfMissing = true
            }
        }
    }
    
    fun load(): AppConfig {
        val configFile = File(findConfigDirectory(), "server.yaml")
        if (!configFile.exists()) {
            throw IllegalStateException("Config file not found: ${configFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val configMap = yaml.load<Map<String, Any>>(configFile.readText())
        
        // Конфигурация сервера
        val serverMap = configMap["server"] as? Map<String, Any> ?: emptyMap()
        val server = ServerConfig(
            port = (serverMap["port"] as? Number)?.toInt() ?: 8080,
            host = serverMap["host"] as? String ?: "0.0.0.0"
        )
        
        // Конфигурация источников данных
        val dataSourcesMap = configMap["dataSources"] as? Map<String, Any> ?: emptyMap()
        val dataSources = dataSourcesMap.mapValues { (_, value) ->
            val sourceMap = value as Map<String, Any>
            val mcpServerMap = sourceMap["mcpServer"] as Map<String, Any>
            
            DataSourceConfig(
                name = sourceMap["name"] as? String ?: "",
                description = sourceMap["description"] as? String ?: "",
                enabled = sourceMap["enabled"] as? Boolean ?: true,
                mcpServer = MCPServerConfig(
                    jarPath = mcpServerMap["jarPath"] as? String
                )
            )
        }
        
        // Конфигурация AI
        val aiMap = configMap["ai"] as? Map<String, Any> ?: emptyMap()
        val ai = AIConfig(
            provider = aiMap["provider"] as? String ?: "openrouter",
            apiKey = resolveEnvVar(aiMap["apiKey"] as? String ?: ""),
            model = aiMap["model"] as? String ?: "openai/gpt-4o-mini"
        )
        
        // Конфигурация планировщика
        val schedulerMap = configMap["scheduler"] as? Map<String, Any> ?: emptyMap()
        val deliveryMap = schedulerMap["delivery"] as? Map<String, Any> ?: emptyMap()
        val telegramDeliveryMap = deliveryMap["telegram"] as? Map<String, Any> ?: emptyMap()
        
        val scheduler = SchedulerConfig(
            enabled = schedulerMap["enabled"] as? Boolean ?: true,
            intervalMinutes = (schedulerMap["intervalMinutes"] as? Number)?.toInt() ?: 15,
            periodHours = (schedulerMap["periodHours"] as? Number)?.toInt() ?: 24,
            activeSource = schedulerMap["activeSource"] as? String ?: "telegram",
            delivery = DeliveryConfig(
                telegram = TelegramDeliveryConfig(
                    enabled = telegramDeliveryMap["enabled"] as? Boolean ?: false,
                    userId = telegramDeliveryMap["userId"]?.let { resolveEnvVarOptional(it as String) }
                )
            )
        )
        
        return AppConfig(
            server = server,
            dataSources = dataSources,
            ai = ai,
            scheduler = scheduler
        )
    }
    
    private fun resolveEnvVar(value: String): String {
        if (value.startsWith("\${") && value.endsWith("}")) {
            val envVarName = value.substring(2, value.length - 1)
            return dotenv[envVarName]
                ?: System.getenv(envVarName)
                ?: throw IllegalStateException("Environment variable $envVarName not found in .env file or system environment")
        }
        return value
    }
    
    /**
     * Безопасное разрешение переменной окружения (возвращает null, если не найдена)
     * Используется для опциональных параметров
     */
    private fun resolveEnvVarOptional(value: String): String? {
        if (value.startsWith("\${") && value.endsWith("}")) {
            val envVarName = value.substring(2, value.length - 1)
            return dotenv[envVarName] ?: System.getenv(envVarName)
        }
        return if (value.isBlank()) null else value
    }
    
    /**
     * Находит корень проекта (ai_challenge_november)
     */
    private fun findProjectRoot(): String {
        var currentDir = File(System.getProperty("user.dir"))
        
        if (currentDir.name == "server") {
            currentDir = currentDir.parentFile
        }
        
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            val envFile = File(searchDir, ".env")
            if (envFile.exists()) {
                return searchDir.absolutePath
            }
            
            val parent = searchDir.parentFile
            if (parent == null || parent == searchDir) {
                break
            }
            searchDir = parent
        }
        
        return currentDir.absolutePath
    }
    
    private fun findConfigDirectory(): String {
        var currentDir = File(System.getProperty("user.dir"))
        
        if (currentDir.name == "server") {
            currentDir = currentDir.parentFile
        }
        
        // Проверяем config в текущей директории
        var configDir = File(currentDir, "config")
        if (configDir.exists() && File(configDir, "server.yaml").exists()) {
            return configDir.absolutePath
        }
        
        // Ищем lesson-12-reminder-mcp вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-12-reminder-mcp") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            val lessonDir = File(searchDir, "lesson-12-reminder-mcp")
            if (lessonDir.exists()) {
                configDir = File(lessonDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            searchDir = searchDir.parentFile
        }
        
        throw IllegalStateException(
            "Config file not found. Searched in:\n" +
            "- ${File(currentDir, "config").absolutePath}\n" +
            "Please ensure config/server.yaml exists in lesson-12-reminder-mcp directory."
        )
    }
}

