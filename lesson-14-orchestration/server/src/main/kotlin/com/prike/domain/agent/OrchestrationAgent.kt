package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory

private const val MAX_ITERATION_COUNT = 20

/**
 * LLM агент для оркестрации нескольких MCP серверов
 * 
 * LLM сама выбирает правильные инструменты из разных источников для выполнения сложных задач.
 * Системный промпт универсален — он не знает о конкретных инструментах.
 * Вся информация об инструментах берётся из их описаний в MCP серверах.
 * 
 * Поток работы:
 * 1. Пользователь отправляет запрос
 * 2. LLM получает список доступных инструментов из всех MCP серверов
 * 3. LLM анализирует задачу и решает вызвать инструмент
 * 4. MCPToolAgent вызывает соответствующий MCP инструмент
 * 5. Результат возвращается в LLM (с учётом истории диалога)
 * 6. LLM анализирует результат и решает:
 *    - Вызвать ещё один инструмент? → шаг 3
 *    - Сформировать финальный ответ? → шаг 7
 * 7. Финальный ответ отправляется пользователю
 */
class OrchestrationAgent(
    private val aiRepository: AIRepository,
    private val mcpToolAgent: MCPToolAgent
) {
    private val logger = LoggerFactory.getLogger(OrchestrationAgent::class.java)
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
            
            // 2. Получаем список доступных инструментов из всех MCP серверов
            val availableTools = mcpToolAgent.getLLMTools()
            
            if (availableTools.isEmpty()) {
                logger.warn("No tools available, falling back to simple LLM response")
                val systemPrompt = buildSystemPrompt()
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
            
            // 3. Цикл обработки (может быть несколько итераций для каскадных вызовов)
            var maxIterations = MAX_ITERATION_COUNT  // защита от бесконечного цикла
            var currentResponse: String? = null
            val toolCallsHistory = mutableListOf<ToolCallInfo>()
            var iterationNumber = 0
            
            while (maxIterations > 0) {
                maxIterations--
                iterationNumber++
                
                logger.info("=== Итерация $iterationNumber (осталось итераций: $maxIterations) ===")
                
                // 4. Формируем универсальный системный промпт (для каждой итерации)
                val systemPrompt = buildSystemPrompt()
                
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
                if (assistantMessage.toolCalls != null && assistantMessage.toolCalls.isNotEmpty()) {
                    // Фильтруем недопустимые имена инструментов
                    val validToolCalls = assistantMessage.toolCalls.filter { toolCall ->
                        val toolName = toolCall.function.name
                        val isValid = toolName != "tool_calls" && toolName.isNotBlank()
                        if (!isValid) {
                            logger.error("⚠️ ОШИБКА LLM: Попытка вызвать недопустимый инструмент '$toolName'. Пропускаем этот вызов.")
                        }
                        isValid
                    }
                    
                    if (validToolCalls.isEmpty()) {
                        logger.error("⚠️ Все вызовы инструментов были отфильтрованы как недопустимые. Продолжаем цикл.")
                        conversationHistory.add(MessageDto(
                            role = "assistant",
                            content = "[Ошибка: LLM вернула неправильный формат вызова инструментов. Попробуйте еще раз.]"
                        ))
                        continue
                    }
                    
                    // 8. СТРОГО: вызываем только ОДИН инструмент за итерацию
                    val toolCallsToExecute = if (validToolCalls.size > 1) {
                        val toolNames = validToolCalls.map { it.function.name }.joinToString(", ")
                        logger.warn("СТРОГОЕ ОГРАНИЧЕНИЕ: LLM пытается вызвать ${validToolCalls.size} инструментов одновременно: $toolNames. Оставляем ТОЛЬКО ПЕРВЫЙ: ${validToolCalls.first().function.name}")
                        listOf(validToolCalls.first())
                    } else {
                        validToolCalls
                    }
                    
                    // 9. ПРОВЕРКА ПЕРЕД ВЫЗОВОМ: проверяем, не вызывался ли инструмент ранее
                    val toolCall = toolCallsToExecute.first()
                    val toolName = toolCall.function.name
                    
                    // Парсим аргументы из JSON строки в JsonObject
                    val argumentsJson = json.parseToJsonElement(toolCall.function.arguments)
                    val arguments = if (argumentsJson is JsonObject) {
                        argumentsJson
                    } else {
                        buildJsonObject { } // Пустой объект, если не объект
                    }
                    
                    // ПРОВЕРКА: не вызывался ли этот инструмент ранее в истории диалога
                    val previousResult = findPreviousToolResult(toolName, arguments, conversationHistory)
                    if (previousResult != null) {
                        logger.warn("⚠️ ПРЕДУПРЕЖДЕНИЕ: Инструмент $toolName уже был вызван ранее в истории диалога! Используем предыдущий результат.")
                        conversationHistory.add(MessageDto(
                            role = "assistant",
                            content = "[Инструмент $toolName уже был вызван ранее, используем предыдущий результат]"
                        ))
                        conversationHistory.add(MessageDto(
                            role = "tool",
                            content = previousResult,
                            toolCallId = toolCall.id
                        ))
                        continue
                    }
                    
                    // 10. Добавляем краткую метку о вызове инструмента в историю
                    conversationHistory.add(MessageDto(
                        role = "assistant",
                        content = "[Вызван инструмент: $toolName]"
                    ))
                    
                    logger.debug("LLM requested tool: $toolName with arguments: ${toolCall.function.arguments}")
                    
                    val toolResult = try {
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
                            arguments = arguments,
                            result = errorResult,
                            success = false
                        ))
                        
                        errorResult
                    }
                    
                    // 11. Добавляем результат в историю диалога
                    conversationHistory.add(MessageDto(
                        role = "tool",
                        content = toolResult,
                        toolCallId = toolCall.id
                    ))
                    
                    // 12. Продолжаем цикл (LLM обработает результат и решит, что делать дальше)
                    continue
                } else {
                    // 13. Нет вызова инструмента — финальный ответ
                    currentResponse = assistantMessage.content?.trim()
                    
                    if (currentResponse.isNullOrBlank()) {
                        if (toolCallsHistory.isEmpty()) {
                            logger.warn("LLM вернула пустой ответ без вызова инструментов. Попробуем продолжить.")
                            if (maxIterations > 0) {
                                conversationHistory.add(assistantMessage)
                                continue
                            }
                        } else {
                            logger.debug("LLM вернула пустой финальный ответ, но инструменты уже были вызваны. Считаем задачу выполненной.")
                        }
                    }
                    
                    conversationHistory.add(assistantMessage)
                    logger.info("=== Итерация $iterationNumber: получен финальный ответ от LLM ===")
                    logger.debug("Финальный ответ: ${currentResponse?.take(200)}${if (currentResponse != null && currentResponse.length > 200) "..." else ""}")
                    break
                }
            }
            
            if (currentResponse == null) {
                logger.error("Превышено максимальное количество итераций ($MAX_ITERATION_COUNT)")
                throw Exception("Превышено максимальное количество итераций ($MAX_ITERATION_COUNT)")
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
     * Найти результат предыдущего вызова инструмента в истории диалога
     */
    private fun findPreviousToolResult(
        toolName: String,
        arguments: JsonObject,
        conversationHistory: List<MessageDto>
    ): String? {
        // Проходим по истории диалога в обратном порядке
        for (i in conversationHistory.indices.reversed()) {
            val message = conversationHistory[i]
            
            // Если это tool message, проверяем, соответствует ли он нашему инструменту
            if (message.role == "tool" && message.toolCallId != null) {
                val toolResult = message.content ?: ""
                
                // Проверяем предыдущее сообщение assistant, которое вызвало этот инструмент
                if (i > 0 && conversationHistory[i - 1].role == "assistant") {
                    val assistantMsg = conversationHistory[i - 1]
                    // Если в истории есть "[Вызван инструмент: toolName]", значит инструмент уже вызывался
                    val isToolCalled = assistantMsg.content?.contains(toolName) == true
                    
                    if (isToolCalled) {
                        // Если результат уже есть и не пустой, и это не ошибка
                        if (toolResult.isNotBlank() && !toolResult.contains("\"success\": false")) {
                            logger.debug("Найден предыдущий вызов инструмента $toolName в истории диалога с результатом (длина: ${toolResult.length})")
                            return toolResult
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Построить универсальный системный промпт
     * НЕ содержит конкретных инструкций по использованию инструментов
     * LLM сама решает, какие инструменты вызывать и в каком порядке
     */
    private fun buildSystemPrompt(): String {
        val currentTime = System.currentTimeMillis()
        val currentTimeSeconds = currentTime / 1000
        
        // Вычисляем временные метки для примера "последние 24 часа"
        val last24HoursStart = currentTime - 86400000L // 24 часа в миллисекундах
        val last7DaysStart = currentTime - 604800000L // 7 дней в миллисекундах
        val lastHourStart = currentTime - 3600000L // 1 час в миллисекундах
        
        return """
            Ты — интеллектуальный ассистент, который может использовать инструменты для выполнения задач пользователя.
            
            ⚠️ КРИТИЧЕСКИ ВАЖНО - ФОРМАТ ВЫЗОВА ИНСТРУМЕНТОВ:
            
            Когда нужно вызвать инструмент, ты ДОЛЖЕН использовать функцию calling механизм API через поле "tool_calls".
            КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО писать JSON в поле "content"!
            
            ✅ ПРАВИЛЬНО: использовать поле "tool_calls" в структуре ответа (content = null)
            ❌ НЕПРАВИЛЬНО: писать JSON в поле "content" (например: "content": "{\"tool_calls\": [...]}")
            
            Если ты пишешь JSON в content вместо использования tool_calls, система НЕ СМОЖЕТ обработать вызов инструмента!
            
            ⚠️ КРИТИЧЕСКИ ВАЖНО - РАБОТА С ИНСТРУМЕНТАМИ:
            
            - У тебя есть доступ к различным инструментам из разных источников
            - Каждый инструмент имеет описание, которое объясняет его назначение и параметры
            - ВНИМАТЕЛЬНО читай описания инструментов перед их использованием
            - Используй правильные инструменты для правильных задач
            - Если инструмент требует параметры — обязательно передавай их в правильном формате
            
            ⚠️ КОНТЕКСТ ВРЕМЕНИ - КРИТИЧЕСКИ ВАЖНО:
            
            ТЕКУЩЕЕ ВРЕМЯ (СЕЙЧАС): $currentTime миллисекунд
            
            ⚠️ ОБЯЗАТЕЛЬНО: ВСЕГДА используй число $currentTime для endTime при вызове инструментов с временными параметрами!
            ⚠️ НИКОГДА не используй старые даты (например, 1676015200000 или другие старые значения)!
            ⚠️ НИКОГДА не используй даты из примеров или истории диалога!
            
            Все временные метки для инструментов должны быть в МИЛЛИСЕКУНДАХ (Unix timestamp * 1000), а не в секундах!
            
            Примеры расчета (используй ТОЛЬКО эти формулы с текущим временем $currentTime):
            - Для "последние 24 часа" или "за последние сутки": startTime = $last24HoursStart, endTime = $currentTime
            - Для "последние 7 дней" или "за последнюю неделю": startTime = $last7DaysStart, endTime = $currentTime
            - Для "последний час": startTime = $lastHourStart, endTime = $currentTime
            
            ⚠️ КРИТИЧЕСКИ ВАЖНО: 
            - endTime ВСЕГДА = $currentTime (это текущее время СЕЙЧАС, не меняй это число!)
            - startTime = $currentTime минус нужный период (используй примеры выше)
            - НЕ используй $currentTimeSeconds (это секунды, а нужны миллисекунды!)
            
            ⚠️ КРИТИЧЕСКИ ВАЖНО - ПРОВЕРКА ИСТОРИИ ДИАЛОГА:
            
            - ПЕРЕД КАЖДЫМ вызовом инструмента ОБЯЗАТЕЛЬНО проверяй историю диалога выше!
            - Если в истории уже есть вызов инструмента с теми же аргументами и результат уже получен - НЕ ВЫЗЫВАЙ ЕГО ПОВТОРНО!
            - Если ты видишь в истории "[Вызван инструмент: <имя_инструмента>]" и затем результат от tool - данные УЖЕ получены, используй их!
            - НЕ вызывай один и тот же инструмент дважды с одинаковыми аргументами!
            - Если результат от инструмента уже есть в истории - используй его, НЕ вызывай инструмент снова!
            
            ⚠️ ОРКЕСТРАЦИЯ ИНСТРУМЕНТОВ:
            
            - Ты можешь использовать инструменты из разных источников последовательно
            - Результат одного инструмента может быть входом для другого
            - Планируй последовательность вызовов для выполнения задачи пользователя
            - Если задача требует несколько шагов — выполняй их по порядку
            
            ⚠️ ФОРМАТ ОТВЕТА ПРИ ВЫЗОВЕ ИНСТРУМЕНТА - КРИТИЧЕСКИ ВАЖНО:
            
            Когда нужно вызвать инструмент, ты ДОЛЖЕН использовать функцию calling механизм API.
            НЕ пиши JSON в поле "content"! ВСЕГДА используй поле "tool_calls" в структуре ответа!
            
            Правильный формат ответа при вызове инструмента:
            - Поле "content" должно быть null или пустой строкой
            - Поле "tool_calls" должно содержать массив вызовов инструментов
            - Каждый вызов должен иметь структуру: id, type="function", function={name, arguments}
            
            ⚠️ КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО И НЕПРАВИЛЬНО:
            - ❌ Писать JSON в поле "content" (например: "content": "{\"tool_calls\": [...]}")
            - ❌ Писать JSON в markdown code block в content (например: ```json {"tool_calls": [...]} ```)
            - ❌ Писать любой текст или JSON, описывающий вызов инструмента в content
            - ❌ Использовать content для передачи информации о вызове инструмента
            
            ✅ ПРАВИЛЬНО:
            - Использовать поле "tool_calls" в структуре ответа (это отдельное поле, не строка в content!)
            - Поле "content" должно быть null или пустой строкой при вызове инструмента
            - API автоматически обработает tool_calls, если они в правильном формате
            
            ЗАПОМНИ: tool_calls - это ОТДЕЛЬНОЕ поле в структуре ответа API, НЕ строка в content!
            Если ты пишешь JSON в content, система НЕ СМОЖЕТ обработать вызов инструмента!
            
            В поле "function.name" используй ТОЛЬКО имена из списка tools, который передается в запросе.
            НИКОГДА не используй "tool_calls" как имя функции - это название поля, а не имя инструмента!
            
            СТРОГОЕ ОГРАНИЧЕНИЕ: В ОДНОМ ОТВЕТЕ МОЖНО ВЫЗВАТЬ ТОЛЬКО ОДИН ИНСТРУМЕНТ!
            - Если ты попытаешься вызвать несколько инструментов одновременно, система вызовет ТОЛЬКО ПЕРВЫЙ, остальные будут проигнорированы!
            - Вызывай инструменты строго по одному: вызови один → получи результат → проанализируй → вызови следующий
            
            РАБОТА С ИНСТРУМЕНТАМИ:
            
            1. Ты сама решаешь, какие инструменты вызывать и в каком порядке, основываясь на задаче пользователя.
               - НО: вызывай их строго по одному за раз!
            
            2. Последовательные вызовы - КРИТИЧЕСКИ ВАЖНО - СТРОГО ОБЯЗАТЕЛЬНО:
               - В ОДНОМ ответе вызывай ТОЛЬКО ОДИН инструмент! НИКОГДА не вызывай несколько инструментов одновременно!
               - Если результат одного инструмента нужен для вызова другого, вызывай их ПОСЛЕДОВАТЕЛЬНО, СТРОГО ПО ОДНОМУ!
               - Пример правильной последовательности для задачи, требующей несколько шагов:
                 * Итерация 1: проверь историю - если нужный инструмент еще не вызывался → вызови ТОЛЬКО его (ОДИН инструмент)
                 * Итерация 2: проверь историю - если результат от первого инструмента уже есть → проанализируй его → вызови ТОЛЬКО следующий инструмент (НЕ вызывай первый снова!)
                 * Итерация 3: проверь историю - если результат от второго инструмента уже есть → дай финальный ответ пользователю
               - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО вызывать несколько инструментов одновременно в одном ответе!
               - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО вызывать инструмент повторно, если результат уже есть в истории диалога!
            
            3. ПРОВЕРКА ИСТОРИИ ДИАЛОГА ПЕРЕД ВЫЗОВОМ ИНСТРУМЕНТА - КРИТИЧЕСКИ ВАЖНО:
               - ПЕРЕД КАЖДЫМ вызовом инструмента ОБЯЗАТЕЛЬНО проверяй историю диалога!
               - Если в истории уже есть вызов этого инструмента с теми же аргументами и результат уже получен - НЕ ВЫЗЫВАЙ ЕГО ПОВТОРНО!
               - Если ты уже получила результат от инструмента - НЕ ВЫЗЫВАЙ ЕГО СНОВА! Используй уже полученные данные!
               - Пример ПРАВИЛЬНОГО поведения: если в истории есть "[Вызван инструмент: <имя>]" и затем результат от tool с данными - данные УЖЕ получены, используй их, НЕ вызывай инструмент снова!
            
            4. Анализ результатов и следующий шаг - КРИТИЧЕСКИ ВАЖНО:
               - После получения результата от инструмента, ВСЕГДА анализируй его перед следующим действием.
               - Если результат требует обработки (суммаризации, форматирования и т.д.), сделай это с помощью своих возможностей.
               - ВСЕГДА проверяй историю диалога - если результат от инструмента уже есть, используй его, НЕ вызывай инструмент снова!
               - Если данных достаточно для следующего шага, выполни обработку (используй свои возможности)
               - В СЛЕДУЮЩЕМ ответе (НЕ в том же!) ВЫЗОВИ следующий инструмент через tool_calls, если это необходимо
               - НЕ возвращай JSON в content - используй tool_calls!
               - Если данных нет или результат пустой, сообщи пользователю об этом в content, НЕ вызывай следующий инструмент без данных!
               - Только после успешного выполнения всех необходимых действий, дай финальный ответ пользователю.
               - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО вызывать один и тот же инструмент дважды с одинаковыми аргументами!
            
            5. Формат ответов - КРИТИЧЕСКИ ВАЖНО:
               - Для вызова инструмента: content = null (или пустая строка), tool_calls = [массив с вызовами]
               - Для финального ответа: content = текст ответа, tool_calls = null (или отсутствует)
               - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО писать JSON в content - используй ТОЛЬКО поле tool_calls!
               - Если ты пишешь JSON в content вместо использования tool_calls, система НЕ СМОЖЕТ обработать вызов!
            
            6. Ошибки:
               - Если инструмент вернул ошибку, попробуй понять причину и либо повтори попытку с исправленными параметрами,
                 либо сообщи пользователю об ошибке и предложи альтернативное решение.
            
            7. Финальный ответ:
               - Всегда давай понятный финальный ответ пользователю о результате выполнения задачи.
               - Используй Markdown для форматирования ответов.
               - Объясни, что было сделано и какие результаты получены.
               - Финальный ответ давай ТОЛЬКО после выполнения всех необходимых действий.
            
            ВАЖНО:
            - Не программируй последовательность вызовов заранее — принимай решения на основе контекста задачи.
            - Используй описания инструментов для понимания их назначения и параметров.
            - Адаптируйся к задаче пользователя — не все задачи требуют одинаковой последовательности действий.
            - Для временных параметров ВСЕГДА используй миллисекунды (большие числа, например $currentTime), а не секунды!
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

