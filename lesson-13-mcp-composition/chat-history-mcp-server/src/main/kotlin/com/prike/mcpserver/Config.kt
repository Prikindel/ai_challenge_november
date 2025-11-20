package com.prike.mcpserver

import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

data class MCPServerConfig(
    val serverInfo: ServerInfo,
    val webChat: WebChatConfig
)

data class ServerInfo(
    val name: String,
    val version: String,
    val description: String
)

data class WebChatConfig(
    val memoryDbPath: String  // Путь к memory.db из lesson-09
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
        val configFile = File(findConfigDirectory(), "chat-history-mcp-server.yaml")
        if (!configFile.exists()) {
            throw IllegalStateException("Config file not found: ${configFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val configMap = yaml.load<Map<String, Any>>(configFile.readText())
        
        val mcpServer = configMap["mcpServer"] as Map<String, Any>
        val info = mcpServer["info"] as Map<String, Any>
        val webChat = mcpServer["webChat"] as Map<String, Any>
        
        // Заменяем переменные окружения в значениях и нормализуем путь
        var memoryDbPath = resolveEnvVar(webChat["memoryDbPath"] as String)
        
        // Если путь относительный, делаем его абсолютным относительно корня проекта
        if (!File(memoryDbPath).isAbsolute) {
            val projectRoot = findProjectRoot()
            // Если путь начинается с ../, разрешаем относительно корня проекта
            if (memoryDbPath.startsWith("../")) {
                memoryDbPath = File(projectRoot, memoryDbPath).canonicalPath
            } else {
                // Иначе ищем lesson-13-mcp-composition или lesson-12-reminder-mcp
                val configDir = findConfigDirectory()
                val lessonDir = File(configDir).parentFile
                if (lessonDir != null) {
                    memoryDbPath = File(lessonDir, memoryDbPath).canonicalPath
                }
            }
        }
        
        return MCPServerConfig(
            serverInfo = ServerInfo(
                name = info["name"] as String,
                version = info["version"] as String,
                description = info["description"] as String
            ),
            webChat = WebChatConfig(
                memoryDbPath = memoryDbPath
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
        
        if (currentDir.name == "mcp-server") {
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
        
        if (currentDir.name == "chat-history-mcp-server") {
            currentDir = currentDir.parentFile
        }
        
        // Проверяем config в текущей директории
        var configDir = File(currentDir, "config")
        if (configDir.exists() && File(configDir, "chat-history-mcp-server.yaml").exists()) {
            return configDir.absolutePath
        }
        
        // Ищем lesson-13-mcp-composition или lesson-12-reminder-mcp вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            // Проверяем lesson-13-mcp-composition
            if (searchDir.name == "lesson-13-mcp-composition") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "chat-history-mcp-server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            // Проверяем lesson-12-reminder-mcp (для обратной совместимости)
            if (searchDir.name == "lesson-12-reminder-mcp") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "chat-history-mcp-server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            // Проверяем в текущей директории
            val lesson13Dir = File(searchDir, "lesson-13-mcp-composition")
            if (lesson13Dir.exists()) {
                configDir = File(lesson13Dir, "config")
                if (configDir.exists() && File(configDir, "chat-history-mcp-server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            val lesson12Dir = File(searchDir, "lesson-12-reminder-mcp")
            if (lesson12Dir.exists()) {
                configDir = File(lesson12Dir, "config")
                if (configDir.exists() && File(configDir, "chat-history-mcp-server.yaml").exists()) {
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
                        // Ищем в mcp-servers/ или в корне lesson
                        var lessonDir = jarDir.parentFile
                        if (lessonDir != null) {
                            // Проверяем lesson-13-mcp-composition
                            if (lessonDir.name == "lesson-13-mcp-composition") {
                                configDir = File(lessonDir, "config")
                                if (configDir.exists() && File(configDir, "chat-history-mcp-server.yaml").exists()) {
                                    return configDir.absolutePath
                                }
                            }
                            // Проверяем lesson-12-reminder-mcp
                            if (lessonDir.name == "lesson-12-reminder-mcp") {
                                configDir = File(lessonDir, "config")
                                if (configDir.exists() && File(configDir, "chat-history-mcp-server.yaml").exists()) {
                                    return configDir.absolutePath
                                }
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
            "Please ensure config/chat-history-mcp-server.yaml exists in lesson-13-mcp-composition or lesson-12-reminder-mcp directory."
        )
    }
}

