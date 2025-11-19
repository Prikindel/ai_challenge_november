package com.prike.mcpserver

import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

data class MCPServerConfig(
    val serverInfo: ServerInfo,
    val telegram: TelegramConfig
)

data class ServerInfo(
    val name: String,
    val version: String,
    val description: String
)

data class TelegramConfig(
    val botToken: String,
    val groupId: String,  // ID группы для получения сообщений
    val accountId: String,  // ID пользователя для отправки summary
    val databasePath: String,  // Путь к summary.db
    val enablePolling: Boolean = true  // Включить polling для получения сообщений (по умолчанию true)
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
    
    fun load(): MCPServerConfig {
        val configFile = File(findConfigDirectory(), "telegram-mcp-server.yaml")
        if (!configFile.exists()) {
            throw IllegalStateException("Config file not found: ${configFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val configMap = yaml.load<Map<String, Any>>(configFile.readText())
        
        val mcpServer = configMap["mcpServer"] as Map<String, Any>
        val info = mcpServer["info"] as Map<String, Any>
        val telegram = mcpServer["telegram"] as Map<String, Any>
        
        // Заменяем переменные окружения в значениях
        val botToken = resolveEnvVar(telegram["botToken"] as String)
        val groupId = resolveEnvVar(telegram["groupId"] as String)
        val accountId = resolveEnvVar(telegram["accountId"] as String)
        val databasePath = resolveEnvVar(telegram["databasePath"] as String)
        val enablePolling = (telegram["enablePolling"] as? Boolean) ?: true  // По умолчанию true
        
        return MCPServerConfig(
            serverInfo = ServerInfo(
                name = info["name"] as String,
                version = info["version"] as String,
                description = info["description"] as String
            ),
            telegram = TelegramConfig(
                botToken = botToken,
                groupId = groupId,
                accountId = accountId,
                databasePath = databasePath,
                enablePolling = enablePolling
            )
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
     * Находит корень проекта (ai_challenge_november)
     */
    private fun findProjectRoot(): String {
        var currentDir = File(System.getProperty("user.dir"))
        
        if (currentDir.name == "telegram-mcp-server") {
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
        
        if (currentDir.name == "telegram-mcp-server") {
            currentDir = currentDir.parentFile
        }
        
        // Проверяем config в текущей директории
        var configDir = File(currentDir, "config")
        if (configDir.exists() && File(configDir, "telegram-mcp-server.yaml").exists()) {
            return configDir.absolutePath
        }
        
        // Ищем lesson-12-reminder-mcp вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-12-reminder-mcp") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "telegram-mcp-server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            val lessonDir = File(searchDir, "lesson-12-reminder-mcp")
            if (lessonDir.exists()) {
                configDir = File(lessonDir, "config")
                if (configDir.exists() && File(configDir, "telegram-mcp-server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            searchDir = searchDir.parentFile
        }
        
        // Для JAR файлов
        try {
            val classPath = Config::class.java.protectionDomain?.codeSource?.location?.path
            if (classPath != null) {
                val jarFile = File(classPath)
                if (jarFile.name.endsWith(".jar")) {
                    val jarDir = jarFile.parentFile
                    if (jarDir != null) {
                        var lessonDir = jarDir.parentFile?.parentFile
                        if (lessonDir != null && lessonDir.name == "lesson-12-reminder-mcp") {
                            configDir = File(lessonDir, "config")
                            if (configDir.exists() && File(configDir, "telegram-mcp-server.yaml").exists()) {
                                return configDir.absolutePath
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки
        }
        
        throw IllegalStateException(
            "Config file not found. Searched in:\n" +
            "- ${File(currentDir, "config").absolutePath}\n" +
            "Please ensure config/telegram-mcp-server.yaml exists in lesson-12-reminder-mcp directory."
        )
    }
}
