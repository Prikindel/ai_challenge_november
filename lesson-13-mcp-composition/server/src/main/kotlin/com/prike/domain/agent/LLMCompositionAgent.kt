package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.dto.ToolCallDto
import com.prike.data.dto.FunctionCallDto
import com.prike.data.repository.AIRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory

/**
 * LLM агент с каскадными вызовами инструментов
 * 
 * LLM сама решает, какие инструменты вызывать и в каком порядке.
 * Мы не программируем последовательность, а даём LLM доступ к инструментам.
 * 
 * Поток работы:
 * 1. Пользователь отправляет запрос
 * 2. LLM получает список доступных инструментов
 * 3. LLM анализирует задачу и решает вызвать инструмент
 * 4. MCPToolAgent вызывает соответствующий MCP инструмент
 * 5. Результат возвращается в LLM (с учётом истории диалога)
 * 6. LLM анализирует результат и решает:
 *    - Вызвать ещё один инструмент? → шаг 3
 *    - Сформировать финальный ответ? → шаг 7
 * 7. Финальный ответ отправляется пользователю
 */
class LLMCompositionAgent(
    private val aiRepository: AIRepository,
    private val mcpToolAgent: MCPToolAgent
) {
    private val logger = LoggerFactory.getLogger(LLMCompositionAgent::class.java)
    private val conversationHistory = mutableListOf<MessageDto>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Обработать сообщение пользователя с каскадными вызовами инструментов
     */
    suspend fun processUserMessage(userMessage: String): AgentResponse {
        try {
            // 1. Добавляем сообщение пользователя в историю
            conversationHistory.add(MessageDto(role = "user", content = userMessage))
            
            // 2. Формируем системный промпт с информацией об инструментах
            val systemPrompt = buildSystemPrompt()
            
            // 3. Получаем список доступных инструментов
            val availableTools = mcpToolAgent.getLLMTools()
            
            if (availableTools.isEmpty()) {
                logger.warn("No tools available, falling back to simple LLM response")
                val messages = listOf(
                    MessageDto(role = "system", content = systemPrompt),
                    MessageDto(role = "user", content = userMessage)
                )
                val result = aiRepository.getMessageWithTools(messages, tools = null)
                val content = aiRepository.extractContent(result.response)
                return AgentResponse.Success(
                    message = content,
                    toolCalls = emptyList()
                )
            }
            
            // 4. Цикл обработки (может быть несколько итераций для каскадных вызовов)
            var maxIterations = 10  // защита от бесконечного цикла
            var currentResponse: String? = null
            val toolCallsHistory = mutableListOf<ToolCallInfo>()
            var iterationNumber = 0
            
            while (maxIterations > 0) {
                maxIterations--
                iterationNumber++
                
                logger.info("=== Итерация $iterationNumber (осталось итераций: $maxIterations) ===")
                
                // 5. Формируем сообщения для LLM (системный промпт + история диалога)
                val messages = buildList {
                    add(MessageDto(role = "system", content = systemPrompt))
                    addAll(conversationHistory)
                }
                
                logger.debug("Отправка запроса в LLM с ${messages.size} сообщениями и ${availableTools.size} инструментами")
                
                // 6. Отправляем запрос в LLM с инструментами
                val llmResponse = aiRepository.getMessageWithTools(
                    messages = messages,
                    tools = availableTools
                )
                
                val choice = llmResponse.response.choices.firstOrNull()
                    ?: throw IllegalStateException("Empty response from LLM")
                
                val assistantMessage = choice.message
                
                // 7. Проверяем, есть ли вызов инструмента
                if (assistantMessage.toolCalls != null && assistantMessage.toolCalls!!.isNotEmpty()) {
                    // 8. Добавляем ответ ассистента с tool_calls в историю
                    conversationHistory.add(assistantMessage)
                    
                    // 9. Вызываем каждый инструмент
                    for (toolCall in assistantMessage.toolCalls!!) {
                        val toolName = toolCall.function.name
                        logger.debug("LLM requested tool: $toolName with arguments: ${toolCall.function.arguments}")
                        
                        val toolResult = try {
                            // Парсим аргументы из JSON строки в JsonObject
                            val argumentsJson = json.parseToJsonElement(toolCall.function.arguments)
                            val arguments = if (argumentsJson is JsonObject) {
                                argumentsJson
                            } else {
                                buildJsonObject { } // Пустой объект, если не объект
                            }
                            
                            // Вызываем MCP инструмент
                            logger.info("Вызов инструмента: $toolName")
                            val result = mcpToolAgent.callTool(toolName, arguments)
                            logger.info("Результат инструмента $toolName: ${result.take(200)}${if (result.length > 200) "..." else ""}")
                            
                            // Сохраняем информацию о вызове инструмента
                            toolCallsHistory.add(ToolCallInfo(
                                name = toolName,
                                arguments = arguments,
                                result = result,
                                success = true
                            ))
                            
                            result
                        } catch (e: Exception) {
                            logger.error("Error calling tool $toolName: ${e.message}", e)
                            val errorResult = """{"success": false, "error": "${e.message}"}"""
                            
                            // Сохраняем информацию об ошибке
                            toolCallsHistory.add(ToolCallInfo(
                                name = toolName,
                                arguments = buildJsonObject { },
                                result = errorResult,
                                success = false
                            ))
                            
                            errorResult
                        }
                        
                        // 10. Добавляем результат в историю диалога
                        conversationHistory.add(MessageDto(
                            role = "tool",
                            content = toolResult,
                            toolCallId = toolCall.id
                        ))
                    }
                    
                    // 11. Продолжаем цикл (LLM обработает результат и решит, что делать дальше)
                    continue
                } else {
                    // 12. Нет вызова инструмента — финальный ответ
                    currentResponse = assistantMessage.content?.trim()
                    conversationHistory.add(assistantMessage)
                    logger.info("=== Итерация $iterationNumber: получен финальный ответ от LLM ===")
                    logger.debug("Финальный ответ: ${currentResponse?.take(200)}${if (currentResponse != null && currentResponse.length > 200) "..." else ""}")
                    break
                }
            }
            
            if (currentResponse == null) {
                logger.error("Превышено максимальное количество итераций (10)")
                throw Exception("Превышено максимальное количество итераций (10)")
            }
            
            logger.info("=== Обработка завершена. Всего итераций: $iterationNumber, вызвано инструментов: ${toolCallsHistory.size} ===")
            
            return AgentResponse.Success(
                message = currentResponse,
                toolCalls = toolCallsHistory
            )
        } catch (e: Exception) {
            logger.error("Error processing user message: ${e.message}", e)
            return AgentResponse.Error(
                message = "Ошибка при обработке сообщения: ${e.message}",
                cause = e
            )
        }
    }
    
    /**
     * Построить системный промпт с информацией об инструментах
     */
    private fun buildSystemPrompt(): String {
        return """
            Ты — интеллектуальный ассистент, который может использовать инструменты для выполнения задач.
            
            Доступные инструменты:
            - get_chat_history(startTime, endTime): получить историю переписки за период
            - send_telegram_message(userId, message): отправить сообщение пользователю в Telegram
            
            Ты можешь вызывать инструменты последовательно (каскадно):
            1. Получить данные через get_chat_history
            2. Проанализировать и суммаризировать их (используя свои возможности)
            3. Отправить результат через send_telegram_message
            
            Если инструмент вернул ошибку, попробуй понять причину и либо повтори попытку,
            либо сообщи пользователю об ошибке.
            
            Всегда давай понятный финальный ответ пользователю о результате выполнения задачи.
            Используй Markdown для форматирования ответов.
        """.trimIndent()
    }
    
    /**
     * Результат обработки сообщения пользователя
     */
    sealed class AgentResponse {
        data class Success(
            val message: String,
            val toolCalls: List<ToolCallInfo>
        ) : AgentResponse()
        
        data class Error(
            val message: String,
            val cause: Throwable? = null
        ) : AgentResponse()
    }
    
    /**
     * Информация о вызове инструмента
     */
    data class ToolCallInfo(
        val name: String,
        val arguments: JsonObject,
        val result: String,
        val success: Boolean
    )
}

