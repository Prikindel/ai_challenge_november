package com.prike.config

import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Конфигурация MCP сервера
 */
data class MCPConfig(
    val autoStart: Boolean = false,
    val serverJarPath: String? = null // null означает режим разработки (через Gradle)
)

/**
 * Загрузчик конфигурации MCP
 */
object MCPConfigLoader {
    /**
     * Загрузить конфигурацию MCP из mcp-server.yaml
     * @param lessonRoot корневая директория урока (lesson-11-first-mcp-tool)
     */
    fun loadMCPConfig(lessonRoot: String): MCPConfig {
        val configFile = File(lessonRoot, "config/mcp-server.yaml")
        if (!configFile.exists()) {
            // Если файл не найден, используем значения по умолчанию
            return MCPConfig()
        }
        
        val yaml = Yaml()
        val configMap: Map<String, Any>? = runCatching {
            configFile.inputStream().use { stream ->
                val loaded = yaml.load<Any>(stream)
                @Suppress("UNCHECKED_CAST")
                when (loaded) {
                    is Map<*, *> -> loaded as? Map<String, Any>
                    else -> null
                }
            }
        }.getOrNull()
        
        @Suppress("UNCHECKED_CAST")
        val mcpSection: Map<String, Any> = runCatching {
            val mcpValue = configMap?.get("mcp")
            when (mcpValue) {
                is Map<*, *> -> mcpValue as? Map<String, Any> ?: emptyMap()
                else -> emptyMap()
            }
        }.getOrElse { emptyMap() }
        
        return MCPConfig(
            autoStart = (mcpSection["autoStart"] as? Boolean) ?: false,
            serverJarPath = mcpSection["serverJarPath"] as? String
        )
    }
}

