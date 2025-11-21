package com.prike.mcpserver

import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

data class MCPServerConfig(
    val serverInfo: ServerInfo,
    val telegram: TelegramConfig,
    val fileSystem: FileSystemConfig
)

data class ServerInfo(
    val name: String,
    val version: String,
    val description: String
)

data class TelegramConfig(
    val botToken: String,
    val chatId: String  // ID пользователя для отправки сообщений
)

data class FileSystemConfig(
    val basePath: String
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
        val configFile = File(findConfigDirectory(), "reporting-mcp-server.yaml")
        if (!configFile.exists()) {
            throw IllegalStateException("Config file not found: ${configFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val configMap = yaml.load<Map<String, Any>>(configFile.readText())
        
        val telegram = configMap["telegram"] as Map<String, Any>
        val fileSystem = configMap["fileSystem"] as Map<String, Any>
        
        // Заменяем переменные окружения в значениях
        val botToken = resolveEnvVar(telegram["botToken"] as String)
        val chatId = resolveEnvVar(telegram["chatId"] as String)
        val fileSystemBasePath = resolveEnvVar(fileSystem["basePath"] as String)
        
        return MCPServerConfig(
            serverInfo = ServerInfo(
                name = "Reporting MCP Server",
                version = "1.0.0",
                description = "MCP сервер для генерации отчётов и их доставки (файлы, Telegram)"
            ),
            telegram = TelegramConfig(
                botToken = botToken,
                chatId = chatId
            ),
            fileSystem = FileSystemConfig(basePath = fileSystemBasePath)
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
        
        if (currentDir.name == "reporting-mcp-server") {
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
        
        if (currentDir.name == "reporting-mcp-server") {
            currentDir = currentDir.parentFile
        }
        
        // Проверяем config в текущей директории
        var configDir = File(currentDir, "config")
        if (configDir.exists() && File(configDir, "reporting-mcp-server.yaml").exists()) {
            return configDir.absolutePath
        }
        
        // Ищем lesson-14-orchestration вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-14-orchestration") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "reporting-mcp-server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            val lessonDir = File(searchDir, "lesson-14-orchestration")
            if (lessonDir.exists()) {
                configDir = File(lessonDir, "config")
                if (configDir.exists() && File(configDir, "reporting-mcp-server.yaml").exists()) {
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
                            if (configDir.exists() && File(configDir, "reporting-mcp-server.yaml").exists()) {
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
            "Please ensure config/reporting-mcp-server.yaml exists in lesson-14-orchestration directory."
        )
    }
}

