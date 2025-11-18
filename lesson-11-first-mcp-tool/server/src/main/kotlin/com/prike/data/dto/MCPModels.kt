package com.prike.data.dto

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

/**
 * Обертка над MCP Tool для упрощения работы
 */
data class MCPTool(
    val name: String,
    val description: String?,
    val inputSchema: JsonObject? = null
) {
    companion object {
        fun fromMCPTool(tool: Tool): MCPTool {
            // Преобразуем Tool.Input в JsonObject
            val inputSchemaJson = tool.inputSchema?.let { input ->
                buildJsonObject {
                    put("type", input.type.toString())
                    input.properties?.let { props ->
                        put("properties", props)
                    }
                    input.required?.let { required ->
                        if (required.isNotEmpty()) {
                            put("required", buildJsonArray {
                                required.forEach { add(JsonPrimitive(it)) }
                            })
                        }
                    }
                }
            }
            
            return MCPTool(
                name = tool.name,
                description = tool.description,
                inputSchema = inputSchemaJson
            )
        }
    }
}

// Для обратной совместимости
typealias Tool = MCPTool

