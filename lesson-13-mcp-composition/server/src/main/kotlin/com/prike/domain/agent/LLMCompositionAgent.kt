package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.dto.ToolCallDto
import com.prike.data.repository.AIRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    private val mcpToolAgent: MCPToolAgent,
    private val defaultTelegramUserId: String? = null
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
            
            // 2. Получаем список доступных инструментов
            val availableTools = mcpToolAgent.getLLMTools()
            
            if (availableTools.isEmpty()) {
                logger.warn("No tools available, falling back to simple LLM response")
                val systemPrompt = buildSystemPrompt() // Получаем актуальное время
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
            var maxIterations = 10  // защита от бесконечного цикла
            var currentResponse: String? = null
            val toolCallsHistory = mutableListOf<ToolCallInfo>()
            var iterationNumber = 0
            
            while (maxIterations > 0) {
                maxIterations--
                iterationNumber++
                
                logger.info("=== Итерация $iterationNumber (осталось итераций: $maxIterations) ===")
                
                // 4. Формируем системный промпт с актуальным временем (для каждой итерации)
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
                if (assistantMessage.toolCalls != null && assistantMessage.toolCalls!!.isNotEmpty()) {
                    // Фильтруем недопустимые имена инструментов (например, "tool_calls" - это ошибка LLM)
                    val validToolCalls = assistantMessage.toolCalls!!.filter { toolCall ->
                        val toolName = toolCall.function.name
                        val isValid = toolName != "tool_calls" && toolName.isNotBlank()
                        if (!isValid) {
                            logger.error("⚠️ ОШИБКА LLM: Попытка вызвать недопустимый инструмент '$toolName'. Это ошибка формата ответа LLM. Пропускаем этот вызов.")
                        }
                        isValid
                    }
                    
                    if (validToolCalls.isEmpty()) {
                        logger.error("⚠️ Все вызовы инструментов были отфильтрованы как недопустимые. LLM вернула неправильный формат. Продолжаем цикл.")
                        // Добавляем сообщение об ошибке в историю и продолжаем цикл
                        conversationHistory.add(MessageDto(
                            role = "assistant",
                            content = "[Ошибка: LLM вернула неправильный формат вызова инструментов. Попробуйте еще раз.]"
                        ))
                        continue
                    }
                    
                    // 9. СТРОГО: вызываем только ОДИН инструмент за итерацию
                    // Если LLM пытается вызвать несколько инструментов, оставляем только первый
                    val toolCallsToExecute = if (validToolCalls.size > 1) {
                        val toolNames = validToolCalls.map { it.function.name }.joinToString(", ")
                        logger.warn("СТРОГОЕ ОГРАНИЧЕНИЕ: LLM пытается вызвать ${validToolCalls.size} инструментов одновременно: $toolNames. Оставляем ТОЛЬКО ПЕРВЫЙ: ${validToolCalls.first().function.name}")
                        listOf(validToolCalls.first())
                    } else {
                        validToolCalls
                    }
                    
                    // Объединяем дубликаты (на случай, если первый инструмент уже вызывался)
                    val uniqueToolCalls = mutableMapOf<String, ToolCallDto>()
                    for (toolCall in toolCallsToExecute) {
                        val toolName = toolCall.function.name
                        val arguments = toolCall.function.arguments
                        val key = "$toolName:$arguments"
                        if (!uniqueToolCalls.containsKey(key)) {
                            uniqueToolCalls[key] = toolCall
                        } else {
                            logger.warn("Пропущен дубликат вызова инструмента: $toolName с аргументами: $arguments")
                        }
                    }
                    
                    // ПРОВЕРКА ПЕРЕД ВЫЗОВОМ: проверяем, не вызывался ли инструмент ранее
                    val toolsToSkip = mutableSetOf<String>()
                    val previousResults = mutableMapOf<String, String>() // Сохраняем результаты для пропущенных инструментов
                    
                    for ((key, toolCall) in uniqueToolCalls) {
                        val toolName = toolCall.function.name
                        
                        // Парсим аргументы из JSON строки в JsonObject
                        val argumentsJson = json.parseToJsonElement(toolCall.function.arguments)
                        var arguments = if (argumentsJson is JsonObject) {
                            argumentsJson
                        } else {
                            buildJsonObject { } // Пустой объект, если не объект
                        }
                        
                        // Нормализуем аргументы: ВСЕГДА подставляем defaultTelegramUserId для send_telegram_message из конфигурации
                        if (toolName == "send_telegram_message") {
                            arguments = normalizeTelegramUserId(arguments)
                        }
                        
                        // ПРОВЕРКА: не вызывался ли этот инструмент ранее в истории диалога
                        val previousResult = findPreviousToolResult(toolName, arguments, conversationHistory)
                        if (previousResult != null) {
                            logger.warn("⚠️ ПРЕДУПРЕЖДЕНИЕ: Инструмент $toolName уже был вызван ранее в истории диалога! Пропускаем повторный вызов.")
                            toolsToSkip.add(toolName)
                            previousResults[toolName] = previousResult
                        }
                    }
                    
                    // Если все инструменты нужно пропустить, добавляем их результаты из истории и продолжаем
                    if (toolsToSkip.size == uniqueToolCalls.size) {
                        logger.warn("⚠️ Все инструменты уже были вызваны ранее. Используем предыдущие результаты.")
                        // Добавляем сообщение о том, что инструменты уже были вызваны
                        val calledTools = uniqueToolCalls.values.map { it.function.name }.joinToString(", ")
                        conversationHistory.add(MessageDto(
                            role = "assistant",
                            content = "[Инструменты $calledTools уже были вызваны ранее, используем предыдущие результаты]"
                        ))
                        
                        // Добавляем результаты из предыдущих вызовов
                        for ((key, toolCall) in uniqueToolCalls) {
                            val toolName = toolCall.function.name
                            val previousResult = previousResults[toolName]
                            if (previousResult != null) {
                                conversationHistory.add(MessageDto(
                                    role = "tool",
                                    content = previousResult,
                                    toolCallId = toolCall.id
                                ))
                            }
                        }
                        continue
                    }
                    
                    // 8. Добавляем краткую метку о вызове инструментов в историю (для контекста LLM)
                    // Не передаём полное сообщение с tool_calls для экономии токенов
                    val calledTools = uniqueToolCalls.values.filter { !toolsToSkip.contains(it.function.name) }
                        .map { it.function.name }.joinToString(", ")
                    if (calledTools.isNotEmpty()) {
                        conversationHistory.add(MessageDto(
                            role = "assistant",
                            content = "[Вызваны инструменты: $calledTools]"
                        ))
                    }
                    
                    // Сначала добавляем результаты для пропущенных инструментов
                    for ((key, toolCall) in uniqueToolCalls) {
                        val toolName = toolCall.function.name
                        
                        // Если инструмент нужно пропустить, добавляем его предыдущий результат в историю
                        if (toolsToSkip.contains(toolName)) {
                            logger.debug("Пропускаем инструмент $toolName - он уже был вызван ранее, добавляем предыдущий результат")
                            
                            val previousResult = previousResults[toolName]
                            if (previousResult != null) {
                                conversationHistory.add(MessageDto(
                                    role = "tool",
                                    content = previousResult,
                                    toolCallId = toolCall.id
                                ))
                            }
                            continue
                        }
                        
                        logger.debug("LLM requested tool: $toolName with arguments: ${toolCall.function.arguments}")
                        
                        // Парсим аргументы из JSON строки в JsonObject
                        val argumentsJson = json.parseToJsonElement(toolCall.function.arguments)
                        var arguments = if (argumentsJson is JsonObject) {
                            argumentsJson
                        } else {
                            buildJsonObject { } // Пустой объект, если не объект
                        }
                        
                        // Нормализуем аргументы: ВСЕГДА подставляем defaultTelegramUserId для send_telegram_message из конфигурации
                        if (toolName == "send_telegram_message") {
                            arguments = normalizeTelegramUserId(arguments)
                        }
                        
                        logger.debug("Аргументы для $toolName: $arguments")
                        
                        val toolResult = try {
                            // Вызываем MCP инструмент
                            // MCP сервер сам обработает типы аргументов согласно своей схеме
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
                    
                    // Проверяем, не пустой ли ответ и не нужно ли было вызвать инструмент
                    if (currentResponse.isNullOrBlank()) {
                        if (toolCallsHistory.isEmpty()) {
                            logger.warn("LLM вернула пустой ответ без вызова инструментов. Это может быть ошибка. Попробуем продолжить.")
                            // Продолжаем цикл, возможно, LLM попытается вызвать инструмент в следующей итерации
                            if (maxIterations > 0) {
                                conversationHistory.add(assistantMessage)
                                continue
                            }
                        } else {
                            // Есть вызовы инструментов, но пустой финальный ответ - это нормально после отправки в Telegram
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
     * Найти результат предыдущего вызова инструмента в истории диалога
     * @return результат предыдущего вызова или null, если инструмент не вызывался ранее
     */
    private fun findPreviousToolResult(
        toolName: String,
        arguments: JsonObject,
        conversationHistory: List<MessageDto>
    ): String? {
        // Проходим по истории диалога в обратном порядке (от последнего к первому)
        // Ищем пары: assistant message с вызовом инструмента + tool message с результатом
        for (i in conversationHistory.indices.reversed()) {
            val message = conversationHistory[i]
            
            // Если это tool message, проверяем, соответствует ли он нашему инструменту
            if (message.role == "tool" && message.toolCallId != null) {
                val toolResult = message.content ?: ""
                
                // Проверяем предыдущее сообщение assistant, которое вызвало этот инструмент
                if (i > 0 && conversationHistory[i - 1].role == "assistant") {
                    val assistantMsg = conversationHistory[i - 1]
                    // Если в истории есть "[Вызваны инструменты: toolName]", значит инструмент уже вызывался
                    val isToolCalled = assistantMsg.content?.contains(toolName) == true
                    
                    // Для get_chat_history также проверяем по содержимому результата
                    val isGetChatHistoryResult = toolName == "get_chat_history" && 
                        toolResult.contains("История чата за период") && 
                        toolResult.contains("Найдено сообщений:")
                    
                    if (isToolCalled || isGetChatHistoryResult) {
                        // Если результат уже есть и не пустой, и это не ошибка
                        if (toolResult.isNotBlank() && !toolResult.contains("\"success\": false")) {
                            // Для get_chat_history: если результат содержит данные (не "Найдено сообщений: 0"),
                            // используем его независимо от аргументов - это предотвратит повторные вызовы
                            if (toolName == "get_chat_history") {
                                // Проверяем, что результат содержит данные (не пустой список сообщений)
                                if (toolResult.contains("Найдено сообщений:") && !toolResult.contains("Найдено сообщений: 0")) {
                                    logger.warn("⚠️ Найден предыдущий вызов get_chat_history с данными в истории диалога. Используем его вместо повторного вызова.")
                                    return toolResult
                                } else if (areTimeRangesSimilar(arguments, toolResult)) {
                                    logger.debug("Найден предыдущий вызов get_chat_history с похожим временным диапазоном в истории диалога")
                                    return toolResult
                                }
                            } else {
                                // Для других инструментов просто проверяем наличие результата
                                logger.debug("Найден предыдущий вызов инструмента $toolName в истории диалога с результатом (длина: ${toolResult.length})")
                                return toolResult
                            }
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Проверить, похожи ли временные диапазоны для get_chat_history
     * Считаем диапазоны похожими, если разница в startTime и endTime не превышает 5 минут (300000 мс)
     */
    private fun areTimeRangesSimilar(
        newArguments: JsonObject,
        previousResult: String
    ): Boolean {
        try {
            // Извлекаем startTime и endTime из новых аргументов
            val newStartTime = newArguments["startTime"]?.jsonPrimitive?.content?.toLongOrNull()
            val newEndTime = newArguments["endTime"]?.jsonPrimitive?.content?.toLongOrNull()
            
            if (newStartTime == null || newEndTime == null) {
                // Если аргументы не содержат временные метки, считаем диапазоны похожими
                // (возможно, это тот же запрос без явных параметров)
                return true
            }
            
            // Извлекаем временные метки из предыдущего результата
            // Формат результата: "История чата за период:\n- Начало: 2025-11-13T19:48:39.559Z\n- Конец: 2025-11-20T19:48:39.559Z"
            // Или можно попробовать найти startTime и endTime в JSON сообщениях внутри результата
            // Но проще - если результат содержит данные за период, и новый запрос тоже за период,
            // считаем их похожими, если разница в endTime не превышает 5 минут
            
            // Альтернативный подход: проверяем, что предыдущий результат содержит данные
            // и новый запрос тоже за период (не пустые временные метки)
            // Если оба запроса за период и результат не пустой, считаем их похожими
            if (previousResult.contains("Найдено сообщений:") && previousResult.contains("Начало:") && previousResult.contains("Конец:")) {
                // Пытаемся извлечь временные метки из результата (ISO формат)
                // Но для упрощения: если разница в endTime не превышает 10 минут, считаем похожими
                // Это позволит избежать повторных вызовов при небольших различиях во времени
                val timeDifferenceThreshold = 600000L // 10 минут в миллисекундах
                
                // Для упрощения: если новый endTime близок к текущему времени (в пределах порога),
                // и предыдущий результат тоже был за период, считаем их похожими
                val currentTime = System.currentTimeMillis()
                val timeDiff = kotlin.math.abs(newEndTime - currentTime)
                
                // Если новый запрос за период и разница во времени невелика, считаем похожими
                if (timeDiff < timeDifferenceThreshold) {
                    logger.debug("Временные диапазоны похожи: новый endTime=$newEndTime, текущее время=$currentTime, разница=${timeDiff}мс")
                    return true
                }
            }
            
            return false
        } catch (e: Exception) {
            logger.warn("Ошибка при проверке временных диапазонов: ${e.message}")
            // В случае ошибки считаем диапазоны не похожими
            return false
        }
    }
    
    /**
     * Нормализовать userId для send_telegram_message: ВСЕГДА подставляем значение из конфигурации
     * Игнорируем то, что указала LLM - мы сами управляем userId из TELEGRAM_USER_ID
     */
    private fun normalizeTelegramUserId(arguments: JsonObject): JsonObject {
        val originalUserId = arguments["userId"]?.jsonPrimitive?.content
        
        // ВСЕГДА используем userId из конфигурации, игнорируя значение от LLM
        if (defaultTelegramUserId != null) {
            logger.info("Подставляем userId из конфигурации TELEGRAM_USER_ID: $defaultTelegramUserId (игнорируем значение от LLM: '$originalUserId')")
            return buildJsonObject {
                arguments.forEach { (key, value) ->
                    if (key == "userId") {
                        // Заменяем userId на значение из конфигурации
                        put(key, defaultTelegramUserId!!)
                    } else {
                        put(key, value)
                    }
                }
                // Если userId не было в аргументах, добавляем
                if (!arguments.containsKey("userId")) {
                    put("userId", defaultTelegramUserId!!)
                }
            }
        } else {
            // Если userId не задан в конфигурации, выбрасываем ошибку
            logger.error("TELEGRAM_USER_ID не задан в конфигурации! userId должен быть указан в server.yaml -> telegram.defaultUserId или в .env как TELEGRAM_USER_ID")
            throw IllegalArgumentException("userId не задан в конфигурации TELEGRAM_USER_ID. Укажите его в server.yaml -> telegram.defaultUserId или в .env как TELEGRAM_USER_ID")
        }
    }
    
    /**
     * Построить универсальный системный промпт
     * Не содержит конкретных инструкций по использованию инструментов
     * LLM сама решает, какие инструменты вызывать и в каком порядке
     */
    private fun buildSystemPrompt(): String {
        val currentTime = System.currentTimeMillis()
        val currentTimeSeconds = currentTime / 1000
        
        val defaultUserIdInfo = defaultTelegramUserId?.let {
            "\nЕсли для инструментов требуется userId и пользователь его не указал, можешь использовать значение по умолчанию: \"$it\""
        } ?: ""
        
        // Вычисляем временные метки для примера "последние 24 часа"
        val last24HoursStart = currentTime - 86400000L // 24 часа в миллисекундах
        val last7DaysStart = currentTime - 604800000L // 7 дней в миллисекундах
        val lastHourStart = currentTime - 3600000L // 1 час в миллисекундах
        
        return """
            Ты — интеллектуальный ассистент, который может использовать инструменты для выполнения задач пользователя.
            
            ⚠️ КРИТИЧЕСКИ ВАЖНО - ПРОВЕРКА ИСТОРИИ ДИАЛОГА:
            - ПЕРЕД КАЖДЫМ вызовом инструмента ОБЯЗАТЕЛЬНО проверяй историю диалога выше!
            - Если в истории уже есть вызов инструмента с теми же аргументами и результат уже получен - НЕ ВЫЗЫВАЙ ЕГО ПОВТОРНО!
            - Если ты видишь в истории "[Вызваны инструменты: <имя_инструмента>]" и затем результат от tool - данные УЖЕ получены, используй их!
            - НЕ вызывай один и тот же инструмент дважды с одинаковыми аргументами!
            - Если результат от инструмента уже есть в истории - используй его, НЕ вызывай инструмент снова!
            
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
            - Если пользователь просит "за последнюю неделю", используй: startTime = $last7DaysStart, endTime = $currentTime
            $defaultUserIdInfo
            
            РАБОТА С ИНСТРУМЕНТАМИ:
            
            ⚠️ КРИТИЧЕСКИ ВАЖНО - ФОРМАТ ОТВЕТА ПРИ ВЫЗОВЕ ИНСТРУМЕНТА:
            
            Когда нужно вызвать инструмент, твой ответ ДОЛЖЕН иметь такую структуру:
            {
              "role": "assistant",
              "content": null,  // ← ОБЯЗАТЕЛЬНО null или пустая строка!
              "tool_calls": [    // ← ОБЯЗАТЕЛЬНО используй поле tool_calls!
                {
                  "id": "call_...",
                  "type": "function",
                  "function": {
                    "name": "<имя_инструмента>",  // ← имя из списка tools
                    "arguments": "{\"param1\": \"value1\"}"
                  }
                }
              ]
            }
            
            ⚠️ КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО:
            - Писать JSON в поле "content" - content должен быть null или ""
            - Писать "[tool_calls=...]" в content
            - Писать {"tool_calls": [...]} в content
            - Писать любой другой JSON в content
            
            ЗАПОМНИ: tool_calls - это ОТДЕЛЬНОЕ поле в ответе, НЕ строка в content!
            API автоматически обработает tool_calls, если они в правильном формате.
            
            В поле "function.name" используй ТОЛЬКО имена из списка tools, который передается в запросе.
            НИКОГДА не используй "tool_calls" как имя функции - это название поля, а не имя инструмента!
            
            СТРОГОЕ ОГРАНИЧЕНИЕ: В ОДНОМ ОТВЕТЕ МОЖНО ВЫЗВАТЬ ТОЛЬКО ОДИН ИНСТРУМЕНТ!
            - Если ты попытаешься вызвать несколько инструментов одновременно, система вызовет ТОЛЬКО ПЕРВЫЙ, остальные будут проигнорированы!
            - Вызывай инструменты строго по одному: вызови один → получи результат → проанализируй → вызови следующий
            
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
               - Если ты вызовешь несколько инструментов одновременно, система вызовет только первый, остальные будут проигнорированы!
            
            3. Параллельные вызовы:
               - Параллельные вызовы используй ТОЛЬКО для полностью независимых операций.
               - ВЫЗЫВАЙ ИНСТРУМЕНТЫ ПО ОДНОМУ, если результат одного нужен для другого!
            
            4. ПРОВЕРКА ИСТОРИИ ДИАЛОГА ПЕРЕД ВЫЗОВОМ ИНСТРУМЕНТА - КРИТИЧЕСКИ ВАЖНО:
               - ПЕРЕД КАЖДЫМ вызовом инструмента ОБЯЗАТЕЛЬНО проверяй историю диалога!
               - Если в истории уже есть вызов этого инструмента с теми же аргументами и результат уже получен - НЕ ВЫЗЫВАЙ ЕГО ПОВТОРНО!
               - Если ты уже получила результат от инструмента - НЕ ВЫЗЫВАЙ ЕГО СНОВА! Используй уже полученные данные!
               - Пример ПРАВИЛЬНОГО поведения: если в истории есть "[Вызваны инструменты: <имя>]" и затем результат от tool с данными - данные УЖЕ получены, используй их, НЕ вызывай инструмент снова!
               - Пример НЕПРАВИЛЬНОГО поведения (НЕ ДЕЛАЙ ТАК!):
                 * История: "[Вызваны инструменты: <имя>]" → результат от tool с данными
                 * НЕПРАВИЛЬНО: снова вызвать тот же инструмент с теми же аргументами
                 * ПРАВИЛЬНО: использовать уже полученные данные для следующего шага
            
            5. Анализ результатов и следующий шаг - КРИТИЧЕСКИ ВАЖНО:
               - После получения результата от инструмента, ВСЕГДА анализируй его перед следующим действием.
               - Если результат требует обработки (суммаризации, форматирования и т.д.), сделай это с помощью своих возможностей.
               - ВСЕГДА проверяй историю диалога - если результат от инструмента уже есть, используй его, НЕ вызывай инструмент снова!
               - Если данных достаточно для следующего шага, выполни обработку (используй свои возможности)
               - В СЛЕДУЮЩЕМ ответе (НЕ в том же!) ВЫЗОВИ следующий инструмент через tool_calls, если это необходимо
               - НЕ возвращай JSON в content - используй tool_calls!
               - Если данных нет или результат пустой, сообщи пользователю об этом в content, НЕ вызывай следующий инструмент без данных!
               - Только после успешного выполнения всех необходимых действий, дай финальный ответ пользователю.
               - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО вызывать один и тот же инструмент дважды с одинаковыми аргументами!
               - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО вызывать инструмент повторно, если результат уже получен в истории диалога!
               - Если в истории диалога уже есть результат от инструмента - используй его, НЕ вызывай инструмент снова!
            
            6. Формат ответов:
               - Для вызова инструмента: content = null, tool_calls = [массив]
               - Для финального ответа: content = текст ответа, tool_calls = null
               - НИКОГДА не пиши JSON в content - используй tool_calls!
            
            7. Ошибки:
               - Если инструмент вернул ошибку, попробуй понять причину и либо повтори попытку с исправленными параметрами,
                 либо сообщи пользователю об ошибке и предложи альтернативное решение.
            
            8. Финальный ответ:
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

