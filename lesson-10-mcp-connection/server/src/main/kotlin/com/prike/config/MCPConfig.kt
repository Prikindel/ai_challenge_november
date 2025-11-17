package com.prike.config

import org.yaml.snakeyaml.Yaml
import java.io.File

data class MCPServerConfig(
    val name: String,
    val type: String,  // "stdio", "sse", "websocket"
    val enabled: Boolean,
    val command: String? = null,
    val args: List<String>? = null,
    val url: String? = null,
    val description: String? = null
)

data class MCPConfig(
    val servers: List<MCPServerConfig>,
    val default: DefaultConfig = DefaultConfig()
)

data class DefaultConfig(
    val timeout: Int = 30,
    val retryAttempts: Int = 3
)

class MCPConfigLoader {
    fun loadConfig(lessonRoot: String): MCPConfig {
        val configFile = File(lessonRoot, "config/mcp.yaml")
        if (!configFile.exists()) {
            return MCPConfig(servers = emptyList())
        }
        
        val yaml = Yaml()
        val configMap = yaml.load<Map<String, Any>>(configFile.readText())
        
        @Suppress("UNCHECKED_CAST")
        val mcpSection = configMap?.get("mcp") as? Map<String, Any> ?: return MCPConfig(servers = emptyList())
        
        @Suppress("UNCHECKED_CAST")
        val serversList = mcpSection["servers"] as? List<Map<String, Any>> ?: emptyList()
        
        val servers = serversList.mapNotNull { serverMap ->
            try {
                MCPServerConfig(
                    name = serverMap["name"] as? String ?: return@mapNotNull null,
                    type = serverMap["type"] as? String ?: "stdio",
                    enabled = serverMap["enabled"] as? Boolean ?: false,
                    command = serverMap["command"] as? String,
                    args = (serverMap["args"] as? List<*>)?.mapNotNull { it as? String },
                    url = serverMap["url"] as? String,
                    description = serverMap["description"] as? String
                )
            } catch (e: Exception) {
                null
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        val defaultSection = mcpSection["default"] as? Map<String, Any> ?: emptyMap()
        
        val defaultConfig = DefaultConfig(
            timeout = (defaultSection["timeout"] as? Number)?.toInt() ?: 30,
            retryAttempts = (defaultSection["retryAttempts"] as? Number)?.toInt() ?: 3
        )
        
        return MCPConfig(
            servers = servers,
            default = defaultConfig
        )
    }
}

