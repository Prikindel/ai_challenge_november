package com.prike.domain.service

import com.prike.config.Config
import com.prike.domain.model.MCPServersConfig
import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

/**
 * Сервис для загрузки и управления конфигурацией MCP серверов
 */
class MCPConfigService(
    private val configPath: String = "config/mcp-servers.yaml"
) {
    private val logger = LoggerFactory.getLogger(MCPConfigService::class.java)
    private var cachedConfig: MCPServersConfig? = null
    private var lessonRoot: File? = null
    
    /**
     * Загружает конфигурацию MCP серверов из YAML файла
     */
    fun loadConfig(lessonRoot: File): MCPServersConfig {
        this.lessonRoot = lessonRoot
        
        val configFile = File(lessonRoot, configPath)
        
        if (!configFile.exists()) {
            logger.warn("MCP servers config file not found: ${configFile.absolutePath}, using empty config")
            return MCPServersConfig(enabled = false, servers = emptyMap())
        }
        
        logger.info("Loading MCP servers config from: ${configFile.absolutePath}")
        
        val yaml = Yaml()
        @Suppress("UNCHECKED_CAST")
        val rootMap = yaml.load<Map<String, Any>>(FileInputStream(configFile)) as Map<String, Any>
        
        // Извлекаем содержимое ключа "mcp_servers"
        @Suppress("UNCHECKED_CAST")
        val configMap = (rootMap["mcp_servers"] as? Map<String, Any>) ?: emptyMap()
        
        // Разрешаем переменные окружения в конфигурации
        @Suppress("UNCHECKED_CAST")
        val resolvedConfigMap = resolveEnvVars(configMap) as Map<String, Any>
        
        val config = MCPServersConfig.fromMap(resolvedConfigMap)
        
        logger.info("Loaded ${config.servers.size} MCP servers, ${config.getEnabledServers().size} enabled")
        
        cachedConfig = config
        return config
    }
    
    /**
     * Получить загруженную конфигурацию (из кэша или загрузить заново)
     */
    fun getConfig(): MCPServersConfig {
        return cachedConfig ?: run {
            val root = lessonRoot ?: findLessonRoot()
            loadConfig(root)
        }
    }
    
    /**
     * Получить список включенных серверов
     */
    fun getEnabledServers(): List<com.prike.domain.model.MCPServerConfig> {
        return getConfig().getEnabledServers()
    }
    
    /**
     * Проверить, включен ли сервер по имени (name из конфигурации)
     */
    fun isServerEnabled(serverName: String): Boolean {
        val config = getConfig()
        // Ищем сервер по имени (name) из конфигурации
        val serverConfig = config.servers.values.find { it.name == serverName }
        return serverConfig != null && serverConfig.enabled && config.enabled
    }
    
    /**
     * Получить конфигурацию сервера по имени
     */
    fun getServer(serverName: String): com.prike.domain.model.MCPServerConfig? {
        return getConfig().getServer(serverName)
    }
    
    /**
     * Перезагрузить конфигурацию
     */
    fun reloadConfig(lessonRoot: File): MCPServersConfig {
        cachedConfig = null
        return loadConfig(lessonRoot)
    }
    
    /**
     * Рекурсивно разрешает переменные окружения в конфигурации
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveEnvVars(obj: Any): Any {
        return when (obj) {
            is String -> Config.resolveEnvVar(obj)
            is Map<*, *> -> {
                (obj as Map<String, Any>).mapValues { (_, value) ->
                    resolveEnvVars(value)
                }
            }
            is List<*> -> {
                obj.map { item -> resolveEnvVars(item ?: "") }
            }
            else -> obj
        }
    }
    
    /**
     * Находит корень урока (lesson-32-god-agent)
     */
    private fun findLessonRoot(): File {
        var currentDir = File(System.getProperty("user.dir"))
        
        if (currentDir.name == "server") {
            currentDir = currentDir.parentFile
        }
        
        var searchDir: File? = currentDir
        while (searchDir != null) {
            if (searchDir.name == "lesson-32-god-agent") {
                return searchDir
            }
            
            val lessonDir = File(searchDir, "lesson-32-god-agent")
            if (lessonDir.exists()) {
                return lessonDir
            }
            
            searchDir = searchDir.parentFile
        }
        
        return currentDir
    }
}

