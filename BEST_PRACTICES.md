# Лучшие практики для работы с LLM агентами

> 📖 **Основная информация:** Вся информация о лучших практиках, архитектуре, MCP и шаблонах находится в **[ARCHITECTURE_GUIDE.md](./ARCHITECTURE_GUIDE.md)** - единой точке входа.

Этот документ содержит **детальные примеры кода** для лучших практик, описанных в ARCHITECTURE_GUIDE.md.

## Содержание

- [Работа с инструментами и заполнение content](#работа-с-инструментами-и-заполнение-content)
- [WebSocket для real-time обновлений](#websocket-для-real-time-обновлений)
- [Логирование](#логирование)
- [Обработка ошибок](#обработка-ошибок)
- [Клиентская часть (UI)](#клиентская-часть-ui)

---

## Работа с инструментами и заполнение content

### Реализация в коде

```kotlin
// При получении ответа от LLM с tool_calls
val hasToolCalls = assistantMessage.toolCalls != null && assistantMessage.toolCalls.isNotEmpty()

if (hasToolCalls) {
    val llmStatusMessage = assistantMessage.content?.trim()
    if (!llmStatusMessage.isNullOrBlank()) {
        statusCallback?.invoke(llmStatusMessage)
    } else {
        // Если content пустой, отправляем дефолтное сообщение
        val firstToolName = assistantMessage.toolCalls?.firstOrNull()?.function?.name ?: "инструмент"
        logger.warn("⚠️ LLM вернула пустой content при вызове инструмента $firstToolName. Отправляем дефолтное сообщение.")
        statusCallback?.invoke("Вызываю инструмент $firstToolName...")
    }
}
```

**См. также:** [ARCHITECTURE_GUIDE.md - Раздел 4.1](./ARCHITECTURE_GUIDE.md#41-работа-с-инструментами-и-заполнение-content)

---

## WebSocket для real-time обновлений

### Полная реализация WebSocket контроллера

```kotlin
class WebSocketChatController(
    private val orchestrationAgent: OrchestrationAgent
) {
    fun registerRoutes(routing: Routing) {
        routing.webSocket("/api/chat/ws") {
            val messageChannel = Channel<String>(Channel.UNLIMITED)
            var sessionActive = true
            
            coroutineScope {
                val senderJob = launch {
                    messageChannel.consumeEach { messageJson ->
                        if (sessionActive) {
                            try {
                                outgoing.send(Frame.Text(messageJson))
                            } catch (e: Exception) {
                                logger.error("Ошибка отправки WebSocket сообщения: ${e.message}", e)
                                sessionActive = false
                            }
                        }
                    }
                }
                
                // Обработка входящих сообщений
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        // ... обработка запроса
                    }
                }
            }
        }
    }
}
```

**См. также:** [ARCHITECTURE_GUIDE.md - Раздел 4.2](./ARCHITECTURE_GUIDE.md#42-websocket-для-real-time-обновлений)

---

## Логирование

### Примеры правильного логирования

```kotlin
// ✅ Хорошо: информативное сообщение
logger.info("Connected to ${clients.size} MCP server(s)")

// ✅ Хорошо: предупреждение с контекстом
logger.warn("⚠️ LLM вернула пустой content при вызове инструмента $toolName (итерация $iterationNumber)")

// ✅ Хорошо: ошибка с полным контекстом
logger.error("Error calling tool $toolName: ${e.message}", e)

// ❌ Плохо: избыточные DEBUG логи в production
logger.debug("Starting connection job for server ${serverConfig.id}")
```

**См. также:** [ARCHITECTURE_GUIDE.md - Раздел 4.3](./ARCHITECTURE_GUIDE.md#43-логирование)

---

## Обработка ошибок

### Пример обработки ошибок

```kotlin
try {
    val result = mcpToolAgent.callTool(toolName, arguments)
    toolCallCallback?.invoke(toolName, "success", "Инструмент выполнен успешно")
    result
} catch (e: Exception) {
    logger.error("Error calling tool $toolName: ${e.message}", e)
    val errorResult = """{"success": false, "error": "${e.message}"}"""
    toolCallCallback?.invoke(toolName, "error", "Ошибка: ${e.message}")
    errorResult
}
```

**См. также:** [ARCHITECTURE_GUIDE.md - Раздел 4.4](./ARCHITECTURE_GUIDE.md#44-обработка-ошибок)

---

## Клиентская часть (UI)

### Отображение статусов

```javascript
function handleWebSocketMessage(data) {
    switch (data.type) {
        case 'status':
            updateStreamingBotMessage(currentBotMessageDiv, data.message);
            break;
            
        case 'tool_call':
            addToolCallToMessage(
                currentBotMessageDiv,
                data.toolName,
                data.status,
                data.message
            );
            break;
            
        case 'final':
            finalizeBotMessage(
                currentBotMessageDiv,
                data.message,
                data.toolCalls || [],
                data.processingTime || 0
            );
            break;
    }
}
```

### Стили для статусов инструментов

```css
/* Статус "Выполняется" (starting) */
.tool-call-item.tool-call-starting {
    background: #fffbeb;
    border-color: #f59e0b;
    color: #78350f;
}

/* Статус "Успешно" (success) */
.tool-call-item.tool-call-success {
    background: #ecfdf5;
    border-color: #10b981;
    color: #047857;
}

/* Статус "Ошибка" (error) */
.tool-call-item.tool-call-error {
    background: #fef2f2;
    border-color: #ef4444;
    color: #991b1b;
}
```

---

## См. также

- **[ARCHITECTURE_GUIDE.md](./ARCHITECTURE_GUIDE.md)** - полный архитектурный гайд со всей информацией
- **[PROMPT_TEMPLATE.md](./PROMPT_TEMPLATE.md)** - расширенный шаблон системного промпта
- **[MCP_GUIDE.md](./MCP_GUIDE.md)** - подробное руководство по работе с MCP серверами
