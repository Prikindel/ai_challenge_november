package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.dto.ToolDto
import com.prike.data.dto.FunctionDto
import com.prike.data.dto.ToolCallDto
import com.prike.data.dto.FunctionCallDto
import com.prike.data.repository.AIRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import org.slf4j.LoggerFactory

/**
 * LLM агент с интеграцией MCP инструментов для суммаризации данных
 * 
 * Поток работы:
 * 1. Получает список инструментов от MCP
 * 2. Преобразует их в формат OpenAI tools
 * 3. Отправляет запрос в LLM с tools
 * 4. Обрабатывает tool_calls от LLM
 * 5. Вызывает инструменты через MCP
 * 6. Отправляет результаты обратно в LLM
 * 7. Формирует финальный ответ пользователю
 */
class LLMWithSummaryAgent(
    aiRepository: AIRepository,
    private val mcpToolAgent: MCPToolAgent,
    private val systemPrompt: String? = null
) : BaseAgent(aiRepository) {
    private val logger = LoggerFactory.getLogger(LLMWithSummaryAgent::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Обработать сообщение пользователя с использованием MCP инструментов
     */
    suspend fun processUserMessage(userMessage: String): AgentResponse {
        return try {
            // 1. Получаем список доступных инструментов от MCP
            val mcpTools = mcpToolAgent.getAvailableTools()
            if (mcpTools.isEmpty()) {
                logger.warn("No MCP tools available, falling back to simple LLM response")
                val response = aiRepository.getMessage(userMessage)
                return AgentResponse.Success(
                    message = response,
                    toolUsed = null,
                    toolResult = null
                )
            }
            
            // 2. Преобразуем MCP tools в формат OpenAI tools
            val openAITools = convertMCPToolsToOpenAI(mcpTools)
            
            // 3. Формируем сообщения для LLM
            val messages = buildList {
                if (systemPrompt != null) {
                    add(MessageDto(role = "system", content = systemPrompt))
                }
                add(MessageDto(role = "user", content = userMessage))
            }
            
            // 4. Отправляем запрос в LLM с tools
            var completionResult = aiRepository.getMessageWithTools(messages, openAITools)
            var assistantMessage = completionResult.response.choices.firstOrNull()?.message
                ?: throw IllegalStateException("Empty response from LLM")
            
            // 5. Обрабатываем tool_calls (может быть несколько итераций)
            var toolUsed: String? = null
            var toolResult: String? = null
            var toolSource: String? = null
            var iterationCount = 0
            val maxIterations = 5 // Защита от бесконечного цикла
            
            while (assistantMessage.toolCalls != null && assistantMessage.toolCalls!!.isNotEmpty() && iterationCount < maxIterations) {
                iterationCount++
                
                // Добавляем ответ ассистента с tool_calls в историю
                val updatedMessages = messages.toMutableList()
                updatedMessages.add(assistantMessage)
                
                // Вызываем каждый инструмент
                for (toolCall in assistantMessage.toolCalls!!) {
                    toolUsed = toolCall.function.name
                    
                    try {
                        // Находим источник инструмента
                        val toolInfo = mcpTools.find { it.name == toolUsed }
                        toolSource = toolInfo?.sourceId
                        
                        if (toolSource == null) {
                            throw IllegalStateException("Tool $toolUsed not found in available tools")
                        }
                        
                        // Парсим аргументы из JSON строки в JsonObject
                        val argumentsJson = json.parseToJsonElement(toolCall.function.arguments)
                        val arguments = if (argumentsJson is JsonObject) {
                            argumentsJson
                        } else {
                            buildJsonObject { } // Пустой объект, если не объект
                        }
                        
                        // Вызываем инструмент через MCP
                        val result = mcpToolAgent.callTool(toolSource, toolUsed!!, arguments)
                        
                        when (result) {
                            is MCPToolAgent.ToolResult.Success -> {
                                toolResult = result.result
                                
                                // Добавляем результат инструмента в историю
                                updatedMessages.add(
                                    MessageDto(
                                        role = "tool",
                                        content = result.result,
                                        toolCallId = toolCall.id
                                    )
                                )
                            }
                            is MCPToolAgent.ToolResult.Error -> {
                                toolResult = "Ошибка: ${result.message}"
                                logger.error("Tool call failed: ${result.message}", result.cause)
                                
                                // Добавляем ошибку в историю
                                updatedMessages.add(
                                    MessageDto(
                                        role = "tool",
                                        content = "Ошибка: ${result.message}",
                                        toolCallId = toolCall.id
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error calling tool ${toolCall.function.name}: ${e.message}", e)
                        toolResult = "Ошибка при вызове инструмента: ${e.message}"
                        
                        updatedMessages.add(
                            MessageDto(
                                role = "tool",
                                content = "Ошибка: ${e.message}",
                                toolCallId = toolCall.id
                            )
                        )
                    }
                }
                
                // 6. Отправляем результаты обратно в LLM
                val messagesWithInstruction = updatedMessages.toMutableList()
                messagesWithInstruction.add(
                    MessageDto(
                        role = "user",
                        content = """Обработай результат инструмента выше и верни дружелюбный, естественный ответ на русском языке. 
                        Используй данные из результата, но формулируй ответ простыми словами.
                        Используй Markdown для форматирования (жирный текст, списки, эмодзи).
                        НЕ возвращай JSON, технические детали или повторяй вызов инструмента - только понятный, дружелюбный текст."""
                    )
                )
                
                // НЕ отправляем tools во второй запрос, чтобы LLM точно знал, что нужно просто ответить текстом
                completionResult = aiRepository.getMessageWithTools(messagesWithInstruction, tools = null)
                val choice = completionResult.response.choices.firstOrNull()
                    ?: throw IllegalStateException("Empty response from LLM after tool call")
                
                assistantMessage = choice.message
                
                // Проверяем finish_reason - если "stop" и нет tool_calls, значит LLM закончил работу
                val finishReason = choice.finishReason
                if (finishReason == "stop" && (assistantMessage.toolCalls == null || assistantMessage.toolCalls!!.isEmpty())) {
                    break // Выходим из цикла, так как LLM закончил работу
                }
            }
            
            // 7. Формируем финальный ответ
            var finalMessage = assistantMessage.content?.trim()
            
            // Если LLM вернул пустой ответ, используем результат инструмента напрямую
            if (finalMessage.isNullOrBlank()) {
                logger.warn("LLM returned empty response after tool call. Using tool result directly.")
                
                if (toolResult != null && toolResult.isNotBlank()) {
                    if (toolResult.startsWith("Ошибка") || toolResult.contains("error", ignoreCase = true)) {
                        finalMessage = toolResult
                    } else {
                        finalMessage = when (toolUsed) {
                            "get_chat_history" -> {
                                "Вот история чата:\n\n$toolResult"
                            }
                            "get_telegram_messages" -> {
                                "Вот сообщения из Telegram:\n\n$toolResult"
                            }
                            else -> {
                                "Результат выполнения:\n\n$toolResult"
                            }
                        }
                    }
                } else {
                    finalMessage = "Инструмент был вызван, но результат не получен."
                }
            }
            
            if (finalMessage.isBlank()) {
                throw IllegalStateException("Empty final message from LLM")
            }
            
            AgentResponse.Success(
                message = finalMessage,
                toolUsed = toolUsed,
                toolResult = toolResult
            )
        } catch (e: Exception) {
            logger.error("Error processing user message: ${e.message}", e)
            AgentResponse.Error(
                message = "Ошибка при обработке сообщения: ${e.message}",
                cause = e
            )
        }
    }
    
    /**
     * Генерировать summary для указанного источника данных за период
     * 
     * @param source источник данных ("web_chat", "telegram", "both")
     * @param startTime начало периода (Unix timestamp в миллисекундах)
     * @param endTime конец периода (Unix timestamp в миллисекундах)
     * @return текст summary
     */
    suspend fun generateSummary(
        source: String,
        startTime: Long,
        endTime: Long
    ): String {
        return try {
            // 1. Получаем список доступных инструментов от MCP
            val mcpTools = mcpToolAgent.getAvailableTools()
            if (mcpTools.isEmpty()) {
                throw IllegalStateException("No MCP tools available for summary generation")
            }
            
            // 2. Определяем, какой инструмент использовать
            val toolName = when (source) {
                "web_chat" -> "get_chat_history"
                "telegram" -> "get_telegram_messages"
                else -> throw IllegalArgumentException("Unknown source: $source")
            }
            
            val toolInfo = mcpTools.find { it.name == toolName }
                ?: run {
                    val availableToolNames = mcpTools.map { it.name }.joinToString(", ")
                    logger.error("Tool $toolName not found in available tools. Available tools: $availableToolNames")
                    throw IllegalStateException(
                        "Tool $toolName not found in available tools. " +
                        "Available tools: $availableToolNames. " +
                        "Make sure the MCP server for source '$source' is enabled and connected."
                    )
                }
            
            // 3. Формируем системный промпт для генерации summary
            val summarySystemPrompt = """
                Ты — ассистент для анализа и суммаризации данных из разных источников.
                
                Твоя задача — проанализировать полученные данные и создать краткую, но информативную сводку на русском языке.
                
                Правила создания summary:
                - Используй Markdown для форматирования (заголовки, списки, жирный текст)
                - Выдели ключевые темы и важные моменты
                - Если данных нет или мало, сообщи об этом
                - Будь конкретным и информативным
                - Не добавляй технические детали о формате данных
                
                Создай summary на основе предоставленных данных.
            """.trimIndent()
            
            // 4. Вызываем инструмент для получения данных
            val arguments = buildJsonObject {
                put("startTime", startTime)
                put("endTime", endTime)
                if (source == "telegram") {
                    // Для Telegram нужен groupId, но мы его не знаем здесь
                    // Это будет обработано в MCP сервере или передано через конфиг
                }
            }
            
            val toolResult = mcpToolAgent.callTool(toolInfo.sourceId, toolName, arguments)
            
            val dataResult = when (toolResult) {
                is MCPToolAgent.ToolResult.Success -> toolResult.result
                is MCPToolAgent.ToolResult.Error -> {
                    throw IllegalStateException("Failed to get data from tool: ${toolResult.message}")
                }
            }
            
            // 5. Формируем запрос к LLM для генерации summary
            val userPrompt = """
                Проанализируй следующие данные из источника "$source" за период с ${java.time.Instant.ofEpochMilli(startTime)} по ${java.time.Instant.ofEpochMilli(endTime)}:
                
                $dataResult
                
                Создай краткую, но информативную сводку на русском языке. Используй Markdown для форматирования.
            """.trimIndent()
            
            val messages = buildList {
                add(MessageDto(role = "system", content = summarySystemPrompt))
                add(MessageDto(role = "user", content = userPrompt))
            }
            
            // 6. Отправляем запрос в LLM (без tools, так как данные уже получены)
            val completionResult = aiRepository.getMessageWithTools(messages, tools = null)
            val choice = completionResult.response.choices.firstOrNull()
                ?: throw IllegalStateException("Empty response from LLM")
            
            val summaryText = choice.message.content?.trim()
                ?: throw IllegalStateException("Empty summary from LLM")
            
            logger.info("Summary generated successfully for source: $source, period: $startTime - $endTime")
            
            summaryText
        } catch (e: Exception) {
            logger.error("Error generating summary: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Преобразовать MCP tools в формат OpenAI tools
     */
    private fun convertMCPToolsToOpenAI(mcpTools: List<MCPToolAgent.ToolInfo>): List<ToolDto> {
        return mcpTools.map { mcpTool ->
            // Используем inputSchema от MCP напрямую
            val parameters = mcpTool.inputSchema ?: run {
                logger.warn("Tool '${mcpTool.name}' has no inputSchema, using empty schema")
                buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {})
                    put("required", buildJsonArray {})
                }
            }
            
            ToolDto(
                type = "function",
                function = FunctionDto(
                    name = mcpTool.name,
                    description = mcpTool.description ?: "Инструмент ${mcpTool.name} из источника ${mcpTool.sourceId}",
                    parameters = parameters
                )
            )
        }
    }
    
    /**
     * Результат обработки сообщения пользователя
     */
    sealed class AgentResponse {
        data class Success(
            val message: String,
            val toolUsed: String?,
            val toolResult: String?
        ) : AgentResponse()
        
        data class Error(
            val message: String,
            val cause: Throwable? = null
        ) : AgentResponse()
    }
}

