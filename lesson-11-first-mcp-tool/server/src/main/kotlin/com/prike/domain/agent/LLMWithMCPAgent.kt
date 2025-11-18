package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.dto.ToolDto
import com.prike.data.dto.FunctionDto
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.MCPToolAgent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import org.slf4j.LoggerFactory

/**
 * LLM агент с интеграцией MCP инструментов
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
class LLMWithMCPAgent(
    aiRepository: AIRepository,
    private val mcpToolAgent: MCPToolAgent,
    private val systemPrompt: String? = null
) : BaseAgent(aiRepository) {
    private val logger = LoggerFactory.getLogger(LLMWithMCPAgent::class.java)
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
            var iterationCount = 0
            val maxIterations = 5 // Защита от бесконечного цикла
            
            while (assistantMessage.toolCalls != null && assistantMessage.toolCalls!!.isNotEmpty() && iterationCount < maxIterations) {
                iterationCount++
                logger.info("Processing ${assistantMessage.toolCalls!!.size} tool call(s), iteration $iterationCount")
                
                // Добавляем ответ ассистента с tool_calls в историю
                val updatedMessages = messages.toMutableList()
                updatedMessages.add(assistantMessage)
                
                // Вызываем каждый инструмент
                for (toolCall in assistantMessage.toolCalls!!) {
                    toolUsed = toolCall.function.name
                    logger.info("Calling tool: ${toolCall.function.name} with arguments: ${toolCall.function.arguments}")
                    
                    try {
                        // Парсим аргументы из JSON строки
                        val arguments = json.decodeFromString<Map<String, Any>>(toolCall.function.arguments)
                        
                        // Вызываем инструмент через MCP
                        val result = mcpToolAgent.callTool(toolCall.function.name, arguments)
                        
                        when (result) {
                            is MCPToolAgent.ToolResult.Success -> {
                                toolResult = result.result
                                logger.info("Tool call successful: $toolUsed")
                                
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
                completionResult = aiRepository.getMessageWithTools(updatedMessages, openAITools)
                assistantMessage = completionResult.response.choices.firstOrNull()?.message
                    ?: throw IllegalStateException("Empty response from LLM after tool call")
            }
            
            // 7. Формируем финальный ответ
            val finalMessage = assistantMessage.content
                ?: throw IllegalStateException("Empty final message from LLM")
            
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
     * Преобразовать MCP tools в формат OpenAI tools
     * 
     * MCP использует JSON Schema для описания параметров инструментов.
     * OpenAI API также использует JSON Schema в том же формате.
     * Мы просто используем inputSchema от MCP без каких-либо изменений.
     * 
     * Если inputSchema отсутствует (что не должно происходить при правильной регистрации),
     * создаем минимальную схему без параметров.
     */
    private fun convertMCPToolsToOpenAI(mcpTools: List<MCPToolAgent.ToolInfo>): List<ToolDto> {
        return mcpTools.map { mcpTool ->
            // Используем inputSchema от MCP напрямую
            // MCP inputSchema уже в формате JSON Schema, совместимом с OpenAI
            val parameters = mcpTool.inputSchema ?: run {
                // Fallback: если inputSchema отсутствует (не должно происходить),
                // создаем минимальную схему без параметров
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
                    description = mcpTool.description ?: "Инструмент ${mcpTool.name}",
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

