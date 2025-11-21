package com.prike.mcpserver

import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

data class MCPServerConfig(
    val serverInfo: ServerInfo,
    val database: DatabaseConfig,
    val fileSystem: FileSystemConfig,
    val telegram: TelegramConfig
)

data class ServerInfo(
    val name: String,
    val version: String,
    val description: String
)

data class DatabaseConfig(
    val chatHistory: ChatHistoryDbConfig,
    val telegramMessages: TelegramMessagesDbConfig
)

data class ChatHistoryDbConfig(
    val path: String
)

data class TelegramMessagesDbConfig(
    val path: String
)

data class FileSystemConfig(
    val basePath: String
)

data class TelegramConfig(
    val groupId: String
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
        val configFile = File(findConfigDirectory(), "data-collection-mcp-server.yaml")
        if (!configFile.exists()) {
            throw IllegalStateException("Config file not found: ${configFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val configMap = yaml.load<Map<String, Any>>(configFile.readText())
        
        val database = configMap["database"] as Map<String, Any>
        val chatHistory = database["chatHistory"] as Map<String, Any>
        val telegramMessages = database["telegramMessages"] as Map<String, Any>
        val fileSystem = configMap["fileSystem"] as Map<String, Any>
        val telegram = configMap["telegram"] as Map<String, Any>
        
        // Заменяем переменные окружения в значениях
        val chatHistoryPath = resolveEnvVar(chatHistory["path"] as String)
        val telegramMessagesPath = resolveEnvVar(telegramMessages["path"] as String)
        val fileSystemBasePath = resolveEnvVar(fileSystem["basePath"] as String)
        val telegramGroupId = resolveEnvVar(telegram["groupId"] as String)
        
        return MCPServerConfig(
            serverInfo = ServerInfo(
                name = "Data Collection MCP Server",
                version = "1.0.0",
                description = "MCP сервер для сбора данных из разных источников (веб-чат, Telegram, файлы)"
            ),
            database = DatabaseConfig(
                chatHistory = ChatHistoryDbConfig(path = chatHistoryPath),
                telegramMessages = TelegramMessagesDbConfig(path = telegramMessagesPath)
            ),
            fileSystem = FileSystemConfig(basePath = fileSystemBasePath),
            telegram = TelegramConfig(groupId = telegramGroupId)
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
        
        if (currentDir.name == "data-collection-mcp-server") {
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
        
        if (currentDir.name == "data-collection-mcp-server") {
            currentDir = currentDir.parentFile
        }
        
        // Проверяем config в текущей директории
        var configDir = File(currentDir, "config")
        if (configDir.exists() && File(configDir, "data-collection-mcp-server.yaml").exists()) {
            return configDir.absolutePath
        }
        
        // Ищем lesson-14-orchestration вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-14-orchestration") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "data-collection-mcp-server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            val lessonDir = File(searchDir, "lesson-14-orchestration")
            if (lessonDir.exists()) {
                configDir = File(lessonDir, "config")
                if (configDir.exists() && File(configDir, "data-collection-mcp-server.yaml").exists()) {
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
                        if (lessonDir != null && lessonDir.name == "lesson-14-orchestration") {
                            configDir = File(lessonDir, "config")
                            if (configDir.exists() && File(configDir, "data-collection-mcp-server.yaml").exists()) {
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
            "Please ensure config/data-collection-mcp-server.yaml exists in lesson-14-orchestration directory."
        )
    }
}

