package com.prike.domain.model

/**
 * Конфигурация отдельного MCP сервера
 */
data class MCPServerConfig(
    val enabled: Boolean,
    val name: String,
    val description: String,
    val config: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Парсит конфигурацию MCP сервера из YAML
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(serverName: String, map: Map<String, Any>): MCPServerConfig {
            return MCPServerConfig(
                enabled = (map["enabled"] as? Boolean) ?: true,
                name = (map["name"] as? String) ?: serverName,
                description = (map["description"] as? String) ?: "",
                config = map.filterKeys { it !in setOf("enabled", "name", "description") }
            )
        }
    }
}

/**
 * Конфигурация всех MCP серверов
 */
data class MCPServersConfig(
    val enabled: Boolean,
    val servers: Map<String, MCPServerConfig>
) {
    companion object {
        /**
         * Парсит конфигурацию всех MCP серверов из YAML
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>): MCPServersConfig {
            val enabled = (map["enabled"] as? Boolean) ?: true
            
            val serversMap = map.filterKeys { it != "enabled" }
                .mapValues { (serverName, serverConfig) ->
                    val serverMap = serverConfig as? Map<String, Any> ?: emptyMap()
                    MCPServerConfig.fromMap(serverName, serverMap)
                }
            
            return MCPServersConfig(
                enabled = enabled,
                servers = serversMap
            )
        }
    }
    
    /**
     * Получить список включенных серверов
     */
    fun getEnabledServers(): List<MCPServerConfig> {
        return if (enabled) {
            servers.values.filter { it.enabled }
        } else {
            emptyList()
        }
    }
    
    /**
     * Проверить, включен ли сервер
     */
    fun isServerEnabled(serverName: String): Boolean {
        return enabled && servers[serverName]?.enabled == true
    }
    
    /**
     * Получить конфигурацию сервера по имени
     */
    fun getServer(serverName: String): MCPServerConfig? {
        return servers[serverName]
    }
}

