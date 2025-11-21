package com.prike.config

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Конфигурация MCP сервера
 */
data class MCPServerConfig(
    val id: String,
    val name: String,
    val description: String? = null,
    val jarPath: String,
    val configPath: String,
    val type: String = "local",
    val tools: List<String>
)

/**
 * Конфигурация MCP
 */
data class MCPConfig(
    val servers: List<MCPServerConfig>
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
 * Главная конфигурация приложения
 */
data class AppConfig(
    val server: ServerConfig,
    val mcp: MCPConfig,
    val ai: AIConfig
)

object Config {
    private val logger = LoggerFactory.getLogger(Config::class.java)
    
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
        // Загружаем конфигурацию сервера
        val serverConfigFile = File(findConfigDirectory(), "server.yaml")
        if (!serverConfigFile.exists()) {
            throw IllegalStateException("Config file not found: ${serverConfigFile.absolutePath}")
        }
        
        val yaml = Yaml()
        val serverConfigMap = yaml.load<Map<String, Any>>(serverConfigFile.readText())
        
        // Конфигурация сервера
        val serverMap = serverConfigMap["server"] as? Map<String, Any> ?: emptyMap()
        val server = ServerConfig(
            port = (serverMap["port"] as? Number)?.toInt() ?: 8080,
            host = serverMap["host"] as? String ?: "0.0.0.0"
        )
        
        // Конфигурация AI
        val aiMap = serverConfigMap["ai"] as? Map<String, Any> ?: emptyMap()
        val ai = AIConfig(
            provider = aiMap["provider"] as? String ?: "openrouter",
            apiKey = resolveEnvVar(aiMap["apiKey"] as? String ?: ""),
            model = resolveEnvVar(aiMap["model"] as? String ?: "gpt-4o-mini")
        )
        
        // Загружаем конфигурацию MCP серверов
        val mcpConfigFile = File(findConfigDirectory(), "mcp-servers.yaml")
        if (!mcpConfigFile.exists()) {
            throw IllegalStateException("MCP config file not found: ${mcpConfigFile.absolutePath}")
        }
        
        val mcpConfigMap = yaml.load<Map<String, Any>>(mcpConfigFile.readText())
        val mcpMap = mcpConfigMap["mcp"] as? Map<String, Any> ?: emptyMap()
        val serversList = mcpMap["servers"] as? List<Map<String, Any>> ?: emptyList()
        
        val mcpServers = serversList.map { serverMap ->
            MCPServerConfig(
                id = serverMap["id"] as? String ?: throw IllegalStateException("Server id is required"),
                name = serverMap["name"] as? String ?: "",
                description = serverMap["description"] as? String,
                jarPath = serverMap["jarPath"] as? String ?: throw IllegalStateException("Server jarPath is required"),
                configPath = serverMap["configPath"] as? String ?: "",
                type = serverMap["type"] as? String ?: "local",
                tools = (serverMap["tools"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
        }
        
        val mcp = MCPConfig(servers = mcpServers)
        
        return AppConfig(
            server = server,
            mcp = mcp,
            ai = ai
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
        
        // Ищем lesson-14-orchestration вверх по дереву
        var searchDir = currentDir
        while (searchDir != null && searchDir.parentFile != null) {
            if (searchDir.name == "lesson-14-orchestration") {
                configDir = File(searchDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            val lessonDir = File(searchDir, "lesson-14-orchestration")
            if (lessonDir.exists()) {
                configDir = File(lessonDir, "config")
                if (configDir.exists() && File(configDir, "server.yaml").exists()) {
                    return configDir.absolutePath
                }
            }
            
            searchDir = searchDir.parentFile
        }
        
        throw IllegalStateException(
            "Config directory not found. Searched in:\n" +
            "- ${File(currentDir, "config").absolutePath}\n" +
            "Please ensure config/server.yaml exists in lesson-14-orchestration directory."
        )
    }
}

