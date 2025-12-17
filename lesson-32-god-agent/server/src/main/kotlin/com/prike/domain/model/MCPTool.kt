package com.prike.domain.model

/**
 * Модель MCP инструмента
 */
data class MCPTool(
    val serverName: String,
    val name: String,
    val description: String,
    val parameters: Map<String, Any> = emptyMap()
)

/**
 * Результат выполнения MCP инструмента
 */
data class MCPToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null
) {
    companion object {
        fun success(data: Any?): MCPToolResult {
            return MCPToolResult(success = true, data = data)
        }
        
        fun failure(error: String): MCPToolResult {
            return MCPToolResult(success = false, error = error)
        }
    }
}

/**
 * Вызов инструмента для роутинга
 */
data class ToolCall(
    val server: String,
    val tool: String,
    val args: Map<String, Any> = emptyMap()
)

/**
 * Решение роутера о том, какие инструменты использовать
 */
data class RoutingDecision(
    val action: RoutingAction,
    val tools: List<ToolCall> = emptyList(),
    val category: String? = null,
    val dataSource: String? = null,
    val reasoning: String? = null
)

/**
 * Тип действия роутера
 */
enum class RoutingAction {
    MCP_TOOLS,      // Использовать MCP инструменты
    RAG_SEARCH,     // Поиск в базе знаний (RAG)
    ANALYTICS,      // Анализ данных
    DIRECT_ANSWER   // Прямой ответ без инструментов
}

