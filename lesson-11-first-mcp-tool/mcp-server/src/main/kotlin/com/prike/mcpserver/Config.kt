package com.prike.mcpserver

import io.github.cdimascio.dotenv.dotenv
import org.yaml.snakeyaml.Yaml
import java.io.File

data class MCPServerConfig(
    val serverInfo: ServerInfo,
    val api: ApiConfig,
    val transport: TransportConfig
)

data class ServerInfo(
    val name: String,
    val version: String,
    val description: String
)

data class ApiConfig(
    val baseUrl: String,
    val token: String,
    val defaultChatId: String? = null  // ID чата по умолчанию (опционально)
)

data class TransportConfig(
    val type: String
)

object Config {
    private val dotenv = run {
        // Ищем .env файл в корне проекта (ai_challenge_november/.env)
        val projectRoot = findProjectRoot()
        
        try {
            dotenv {
                directory = projectRoot
                filename = ".env"
                ignoreIfMissing = true
            }
        } catch (e: Exception) {
            // Если не удалось загрузить из файла, используем системные переменные
            dotenv {
                ignoreIfMissing = true
            }
        }
    }
    
    fun load(): MCPServerConfig {
        val configFile = File(findConfigDirectory(), "mcp-server.yaml")
        if (!configFile.exists()) {
            throw IllegalStateException("Config file not found: ${configFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val configMap = yaml.load<Map<String, Any>>(configFile.readText())
        
        val mcpServer = configMap["mcpServer"] as Map<String, Any>
        val info = mcpServer["info"] as Map<String, Any>
        val api = mcpServer["api"] as Map<String, Any>
        val transport = mcpServer["transport"] as Map<String, Any>
        
        // Заменяем переменные окружения в значениях
        val token = resolveEnvVar(api["token"] as String)
        val baseUrl = resolveEnvVar(api["baseUrl"] as String)
        val defaultChatId = (api["defaultChatId"] as? String)?.let { resolveEnvVar(it) }
        
        return MCPServerConfig(
            serverInfo = ServerInfo(
                name = info["name"] as String,
                version = info["version"] as String,
                description = info["description"] as String
            ),
            api = ApiConfig(
                baseUrl = baseUrl,
                token = token,
                defaultChatId = defaultChatId
            ),
            transport = TransportConfig(
                type = transport["type"] as String
            )
        )
    }
    
    private fun resolveEnvVar(value: String): String {
        if (value.startsWith("\${") && value.endsWith("}")) {
            val envVarName = value.substring(2, value.length - 1)
            // Сначала пробуем из .env, потом из системных переменных окружения
            return dotenv[envVarName] 
                ?: System.getenv(envVarName)
                ?: throw IllegalStateException("Environment variable $envVarName not found in .env file or system environment")
        }
        return value
    }
    
    /**
     * Находит корень проекта (ai_challenge_november)
     * .env файл находится в корне проекта
     */
    private fun findProjectRoot(): String {
        var currentDir = File(System.getProperty("user.dir"))
        
        // Если мы в mcp-server, идем на уровень выше
        if (currentDir.name == "mcp-server") {
            currentDir = currentDir.parentFile
        }
        
        // Ищем корень проекта (ai_challenge_november)
        // Идем вверх по дереву, пока не найдем директорию с .env файлом
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            val envFile = File(searchDir, ".env")
            if (envFile.exists()) {
                return searchDir.absolutePath
            }
            
            // Проверяем, может быть мы уже в корне проекта
            // (если есть несколько уроков в директории)
            val parent = searchDir.parentFile
            if (parent == null || parent == searchDir) {
                break
            }
            searchDir = parent
        }
        
        // Если не нашли, возвращаем текущую директорию
        // (fallback на системные переменные окружения)
        return currentDir.absolutePath
    }
    
    private fun findConfigDirectory(): String {
        // Пробуем несколько стратегий поиска config директории
        
        // Стратегия 1: Относительно текущей рабочей директории
        var currentDir = File(System.getProperty("user.dir"))
        
        // Если мы в mcp-server, идем на уровень выше
        if (currentDir.name == "mcp-server") {
            currentDir = currentDir.parentFile
        }
        
        // Проверяем config в текущей директории (lesson-11-first-mcp-tool/config)
        var configDir = File(currentDir, "config")
        if (configDir.exists() && File(configDir, "mcp-server.yaml").exists()) {
            return configDir.absolutePath
        }
        
        // Стратегия 2: Ищем lesson-11-first-mcp-tool вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            // Проверяем, может быть мы уже в lesson-11-first-mcp-tool
            if (searchDir.name == "lesson-11-first-mcp-tool") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "mcp-server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            // Проверяем, есть ли lesson-11-first-mcp-tool в текущей директории
            val lessonDir = File(searchDir, "lesson-11-first-mcp-tool")
            if (lessonDir.exists()) {
                configDir = File(lessonDir, "config")
                if (configDir.exists() && File(configDir, "mcp-server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            searchDir = searchDir.parentFile
        }
        
        // Стратегия 3: Используем путь к классу (для JAR файлов)
        try {
            val classPath = Config::class.java.protectionDomain?.codeSource?.location?.path
            if (classPath != null) {
                val jarFile = File(classPath)
                if (jarFile.name.endsWith(".jar")) {
                    // Если запущено из JAR, ищем config относительно JAR
                    val jarDir = jarFile.parentFile
                    if (jarDir != null) {
                        // JAR обычно в build/libs, идем на 2 уровня выше
                        var lessonDir = jarDir.parentFile?.parentFile
                        if (lessonDir != null && lessonDir.name == "lesson-11-first-mcp-tool") {
                            configDir = File(lessonDir, "config")
                            if (configDir.exists() && File(configDir, "mcp-server.yaml").exists()) {
                                return configDir.absolutePath
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки при определении пути к классу
        }
        
        // Если ничего не помогло, выбрасываем исключение
        throw IllegalStateException(
            "Config file not found. Searched in:\n" +
            "- ${File(currentDir, "config").absolutePath}\n" +
            "Please ensure config/mcp-server.yaml exists in lesson-11-first-mcp-tool directory."
        )
    }
}

