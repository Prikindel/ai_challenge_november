package com.prike.gitmcpserver.tools.handlers

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import org.slf4j.Logger

/**
 * Базовый класс для обработчиков инструментов MCP
 */
abstract class ToolHandler<Input, Output> {
    protected abstract val logger: Logger

    open fun handle(params: Input): CallToolResult {
        return try {
            val result = execute(params)
            val content = prepareResult(params, result)
            CallToolResult(content = listOf(content))
        } catch (e: Exception) {
            logger.error("Ошибка выполнения инструмента: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(text = "unknown")
                )
            )
        }
    }

    protected abstract fun execute(params: Input): Output
    protected abstract fun prepareResult(request: Input, result: Output): TextContent
}

