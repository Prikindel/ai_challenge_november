# Промпт для реализации: 🔥 День 13. Композиция MCP-инструментов

Ты — разработчик, создающий урок «🔥 День 13. Композиция MCP-инструментов» в модуле `lesson-13-mcp-composition`. Проект использует стек **Kotlin + Ktor + Clean Architecture + SQLite** с фронтендом на **vanilla JS**. Твоя задача — построить полностью самостоятельный модуль без упоминаний других уроков в коде и документации. Используй **официальный Kotlin SDK для MCP** (`io.modelcontextprotocol:kotlin-sdk`) и **OpenRouter API** с function calling.

## 🎯 Цель урока

Создать систему, где **LLM сама решает**, какие MCP инструменты вызывать и в каком порядке (каскадный вызов). Пользователь формулирует сложную задачу, LLM разбивает её на шаги и последовательно вызывает инструменты.

### Ключевая идея:

**LLM полностью управляет последовательностью вызовов инструментов** — мы не программируем последовательность, а даём LLM доступ к инструментам, и она сама решает, что и когда вызывать.

### Примерный flow:

```
1. Пользователь: "Собери переписку за последние 24 часа и отправь мне в TG краткую сводку"
   ↓
2. Сервер отправляет запрос в LLM (с информацией о доступных MCP инструментах)
   ↓
3. LLM анализирует задачу и решает: нужно вызвать get_chat_history
   ↓
4. LLM вызывает MCP инструмент: get_chat_history(startTime, endTime)
   ↓
5. MCP возвращает переписку → передаём результат обратно LLM (с учётом истории диалога)
   ↓
6. LLM анализирует переписку и решает: нужно суммаризировать
   ↓
7. LLM сама суммаризирует (используя свои возможности, не через MCP)
   ↓
8. LLM решает: нужно отправить результат в Telegram
   ↓
9. LLM вызывает MCP инструмент: send_telegram_message(userId, message)
   ↓
10. MCP отправляет сообщение → возвращаем результат LLM (успех/ошибка)
   ↓
11. LLM анализирует результат и решает, что делать дальше:
    - Если успех → формирует финальный ответ пользователю
    - Если ошибка → может повторить попытку или сообщить об ошибке
   ↓
12. LLM возвращает финальный ответ → отправляем пользователю в UI чат
```

---

## 📋 Поэтапная реализация (отдельные коммиты)

**ВАЖНО:** Реализуй пошагово, каждый шаг — отдельный git коммит. После каждого шага покажи изменения пользователю, он закоммитит и скажет "приступай к следующему шагу".

### Коммит 1: MCP Server - инструмент отправки в Telegram
- Создать MCP сервер с инструментом `send_telegram_message(userId, message)`
- Инструмент отправляет сообщение пользователю в Telegram через Bot API
- Возвращает результат (успех/ошибка) в формате JSON
- Тесты инструмента

### Коммит 2: Основное приложение - MCP клиент и менеджер
- MCPClientManager (управление несколькими MCP серверами)
- Подключение к MCP серверам через JAR файлы (stdio транспорт)
- Загрузка конфигурации MCP серверов из YAML
- API для получения списка доступных инструментов

### Коммит 3: LLM агент с каскадными вызовами
- MCPToolAgent (преобразование MCP инструментов в формат LLM tools)
- LLMCompositionAgent (агент, который управляет каскадными вызовами)
- Логика обработки function calling от LLM
- Цикл: LLM → tool_call → MCP → результат → LLM (с историей) → финальный ответ

### Коммит 4: API и обработка длительных операций
- ChatController с endpoint для отправки сообщений
- Обработка длительных операций (индикатор загрузки)
- Возврат промежуточных статусов (опционально)
- Обработка ошибок и таймаутов

### Коммит 5: UI - чат с индикатором загрузки
- Чат-интерфейс (как в lesson-11/12)
- Индикатор загрузки для длительных операций
- Отображение статуса обработки ("Анализирую...", "Отправляю в Telegram...")
- История диалога

---

## ⚙️ Конфигурация

### Структура проекта

```
lesson-13-mcp-composition/
├── README.md
├── THEORY.md
├── example.env
├── config/
│   ├── mcp-servers.yaml
│   └── server.yaml
├── mcp-servers/
│   ├── chat-history-mcp-server.jar  # JAR из lesson-12 (или пересобрать)
│   └── telegram-sender-mcp-server.jar  # Новый JAR
├── server/
│   └── src/main/kotlin/com/prike/...
└── client/
    ├── index.html
    ├── style.css
    └── app.js
```

### Конфиг (`config/mcp-servers.yaml`)

```yaml
mcp:
  servers:
    - id: "chat-history"
      name: "Chat History MCP Server"
      jarPath: "../lesson-12-reminder-mcp/chat-history-mcp-server/build/libs/chat-history-mcp-server-1.0.0.jar"
      configPath: "../lesson-12-reminder-mcp/config/chat-history-mcp-server.yaml"
      tools:
        - get_chat_history
    
    - id: "telegram-sender"
      name: "Telegram Sender MCP Server"
      jarPath: "mcp-servers/telegram-sender-mcp-server.jar"
      configPath: "config/telegram-sender-mcp-server.yaml"
      tools:
        - send_telegram_message
```

### Конфиг Telegram MCP Server (`config/telegram-sender-mcp-server.yaml`)

```yaml
telegram:
  botToken: "${TELEGRAM_BOT_TOKEN}"  # из .env
  defaultUserId: "${TELEGRAM_USER_ID}"  # из .env (для отправки summary)
```

### Переменные окружения (`example.env`)

```env
OPENAI_API_KEY=your_openai_api_key
OPENROUTER_API_KEY=your_openrouter_api_key  # для OpenRouter
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
TELEGRAM_USER_ID=your_telegram_user_id  # твой личный ID для получения сообщений
```

---

## 🧠 Реализация MCP Server (`telegram-sender-mcp-server/`)

### Коммит 1: Инструмент отправки в Telegram

**Структура:**
```
telegram-sender-mcp-server/
├── src/main/kotlin/com/prike/mcpserver/
│   ├── Main.kt
│   ├── server/
│   │   └── MCPServer.kt
│   ├── tools/
│   │   └── SendTelegramMessageTool.kt
│   ├── telegram/
│   │   └── TelegramBotClient.kt
│   └── config/
│       └── Config.kt
```

**SendTelegramMessageTool.kt:**
```kotlin
// MCP инструмент: send_telegram_message
server.addTool(
    name = "send_telegram_message",
    description = "Отправить сообщение пользователю в Telegram (личное сообщение)"
) { request ->
    val userId = request.params.arguments?.get("userId") as? String
        ?: throw IllegalArgumentException("userId is required")
    val message = request.params.arguments?.get("message") as? String
        ?: throw IllegalArgumentException("message is required")
    
    try {
        // Отправляем сообщение через Telegram Bot API
        val result = telegramBotClient.sendMessage(userId.toLong(), message)
        
        CallToolResult(
            content = listOf(
                TextContent(text = """
                    {
                        "success": true,
                        "messageId": ${result.messageId},
                        "sentAt": ${System.currentTimeMillis()}
                    }
                """.trimIndent())
            )
        )
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(
                TextContent(text = """
                    {
                        "success": false,
                        "error": "${e.message}"
                    }
                """.trimIndent())
            )
        )
    }
}
```

**TelegramBotClient.kt:**
- Используй библиотеку для Telegram Bot API (например, `com.github.pengrad:java-telegram-bot-api`)
- Метод `sendMessage(userId: Long, text: String): SendResponse`
- Обработка ошибок (неверный userId, недоступный бот, и т.д.)

---

## 🧠 Реализация основного приложения (`server/`)

### Коммит 2: MCP клиент и менеджер

**MCPClientManager.kt:**
```kotlin
class MCPClientManager(
    private val config: MCPConfig
) {
    private val clients = mutableMapOf<String, MCPClient>()
    
    suspend fun initialize() {
        // Загружаем конфигурацию MCP серверов
        config.servers.forEach { serverConfig ->
            val client = MCPClient(serverConfig.id)
            client.connectToServer(
                jarPath = serverConfig.jarPath,
                configPath = serverConfig.configPath
            )
            clients[serverConfig.id] = client
        }
    }
    
    suspend fun listAllTools(): List<MCPTool> {
        // Собираем все инструменты из всех серверов
        return clients.values.flatMap { it.listTools() }
    }
    
    suspend fun callTool(serverId: String, toolName: String, arguments: JsonObject): String {
        val client = clients[serverId]
            ?: throw IllegalArgumentException("MCP server not found: $serverId")
        return client.callTool(toolName, arguments)
    }
    
    fun findServerForTool(toolName: String): String? {
        // Находим сервер, который предоставляет инструмент
        return config.servers.find { it.tools.contains(toolName) }?.id
    }
}
```

**MCPClient.kt:**
- Подключение к MCP серверу через stdio (запуск JAR процесса)
- Методы: `connectToServer()`, `listTools()`, `callTool()`
- Обработка ошибок подключения

### Коммит 3: LLM агент с каскадными вызовами

**MCPToolAgent.kt:**
```kotlin
class MCPToolAgent(
    private val mcpClientManager: MCPClientManager
) {
    /**
     * Преобразует MCP инструменты в формат LLM tools (для function calling)
     */
    suspend fun getLLMTools(): List<LLMTool> {
        val mcpTools = mcpClientManager.listAllTools()
        return mcpTools.map { mcpTool ->
            LLMTool(
                type = "function",
                function = LLMFunction(
                    name = mcpTool.name,
                    description = mcpTool.description,
                    parameters = mcpTool.inputSchema
                )
            )
        }
    }
    
    /**
     * Вызывает MCP инструмент по имени
     */
    suspend fun callTool(toolName: String, arguments: JsonObject): String {
        val serverId = mcpClientManager.findServerForTool(toolName)
            ?: throw IllegalArgumentException("Tool not found: $toolName")
        return mcpClientManager.callTool(serverId, toolName, arguments)
    }
}
```

**LLMCompositionAgent.kt:**
```kotlin
class LLMCompositionAgent(
    private val aiRepository: AIRepository,
    private val mcpToolAgent: MCPToolAgent
) {
    private val conversationHistory = mutableListOf<MessageDto>()
    
    suspend fun processUserMessage(userMessage: String): AgentResponse {
        // 1. Добавляем сообщение пользователя в историю
        conversationHistory.add(MessageDto(role = "user", content = userMessage))
        
        // 2. Формируем системный промпт с информацией об инструментах
        val systemPrompt = buildSystemPrompt()
        
        // 3. Получаем список доступных инструментов
        val availableTools = mcpToolAgent.getLLMTools()
        
        // 4. Цикл обработки (может быть несколько итераций для каскадных вызовов)
        var maxIterations = 10  // защита от бесконечного цикла
        var currentResponse: String? = null
        
        while (maxIterations > 0) {
            maxIterations--
            
            // 5. Отправляем запрос в LLM с инструментами
            val llmResponse = aiRepository.getMessageWithTools(
                systemPrompt = systemPrompt,
                messages = conversationHistory,
                tools = availableTools
            )
            
            // 6. Проверяем, есть ли вызов инструмента
            if (llmResponse.hasToolCall()) {
                val toolCall = llmResponse.toolCall!!
                
                // 7. Вызываем MCP инструмент
                val toolResult = try {
                    mcpToolAgent.callTool(toolCall.name, toolCall.arguments)
                } catch (e: Exception) {
                    """{"success": false, "error": "${e.message}"}"""
                }
                
                // 8. Добавляем результат в историю диалога
                conversationHistory.add(MessageDto(
                    role = "assistant",
                    content = llmResponse.message,
                    toolCalls = listOf(toolCall)
                ))
                conversationHistory.add(MessageDto(
                    role = "tool",
                    content = toolResult,
                    toolCallId = toolCall.id
                ))
                
                // 9. Продолжаем цикл (LLM обработает результат и решит, что делать дальше)
                continue
            } else {
                // 10. Нет вызова инструмента — финальный ответ
                currentResponse = llmResponse.message
                conversationHistory.add(MessageDto(
                    role = "assistant",
                    content = currentResponse
                ))
                break
            }
        }
        
        if (currentResponse == null) {
            throw Exception("Превышено максимальное количество итераций")
        }
        
        return AgentResponse.Success(
            message = currentResponse,
            toolCalls = extractToolCallsFromHistory()
        )
    }
    
    private fun buildSystemPrompt(): String {
        return """
            Ты — интеллектуальный ассистент, который может использовать инструменты для выполнения задач.
            
            Доступные инструменты:
            - get_chat_history(startTime, endTime): получить историю переписки за период
            - send_telegram_message(userId, message): отправить сообщение пользователю в Telegram
            
            Ты можешь вызывать инструменты последовательно (каскадно):
            1. Получить данные через get_chat_history
            2. Проанализировать и суммаризировать их
            3. Отправить результат через send_telegram_message
            
            Если инструмент вернул ошибку, попробуй понять причину и либо повтори попытку,
            либо сообщи пользователю об ошибке.
            
            Всегда давай понятный финальный ответ пользователю о результате выполнения задачи.
        """.trimIndent()
    }
}
```

**AIRepository.kt:**
- Метод `getMessageWithTools()` для отправки запроса в OpenRouter с function calling
- Поддержка формата tools (OpenAI/OpenRouter API)
- Парсинг ответа LLM на предмет tool_calls

### Коммит 4: API и обработка длительных операций

**ChatController.kt:**
```kotlin
class ChatController(
    private val llmCompositionAgent: LLMCompositionAgent
) {
    fun configureRoutes(routing: Routing) {
        routing.route("/api/chat") {
            post("/message") {
                call.handleUserMessage()
            }
        }
    }
    
    private suspend fun ApplicationCall.handleUserMessage() {
        try {
            val request = receive<ChatMessageRequestDto>()
            
            // Запускаем обработку в отдельной корутине (не блокируем)
            val response = withContext(Dispatchers.Default) {
                llmCompositionAgent.processUserMessage(request.message)
            }
            
            respond(HttpStatusCode.OK, ChatMessageResponseDto(
                message = response.message,
                toolCalls = response.toolCalls.map { it.toDto() }
            ))
        } catch (e: Exception) {
            logger.error("Error processing message", e)
            respond(HttpStatusCode.InternalServerError, ErrorDto(
                message = "Ошибка обработки: ${e.message}"
            ))
        }
    }
}
```

**Обработка длительных операций:**
- Используй `withContext(Dispatchers.Default)` для неблокирующей обработки
- На фронтенде показывай индикатор загрузки
- Можно добавить WebSocket для streaming ответов (опционально, для будущего)

### Коммит 5: UI

**index.html:**
- Чат-интерфейс (как в lesson-11/12)
- Индикатор загрузки (спиннер или прогресс-бар)
- Отображение использованных инструментов (опционально)

**app.js:**
```javascript
async function sendMessage() {
    const message = messageInput.value;
    if (!message.trim()) return;
    
    // Показываем индикатор загрузки
    showLoadingIndicator();
    
    try {
        const response = await fetch('/api/chat/message', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message })
        });
        
        const data = await response.json();
        
        // Скрываем индикатор
        hideLoadingIndicator();
        
        // Отображаем ответ
        addBotMessage(data.message);
        
        // Показываем использованные инструменты (если есть)
        if (data.toolCalls && data.toolCalls.length > 0) {
            showToolCalls(data.toolCalls);
        }
    } catch (error) {
        hideLoadingIndicator();
        showError('Ошибка отправки сообщения');
    }
}
```

---

## 🗄️ Структура БД

Используем существующую БД из lesson-12 (memory.db для веб-чата, summary.db для Telegram сообщений). Новые таблицы не требуются.

---

## ✅ Финальная проверка

1. **Коммит 1:** Собери Telegram MCP сервер, протестируй инструмент `send_telegram_message`
2. **Коммит 2:** Подключись к MCP серверам, получи список инструментов
3. **Коммит 3:** Протестируй каскадный вызов: запрос переписки → суммаризация → отправка в Telegram
4. **Коммит 4:** Проверь API, обработку ошибок, таймауты
5. **Коммит 5:** Проверь UI, индикатор загрузки, отображение ответов

### Тестовый сценарий:

```
Пользователь: "Собери переписку за последние 24 часа и отправь мне в TG краткую сводку"

Ожидаемое поведение:
1. LLM вызывает get_chat_history
2. Получает переписку
3. LLM суммаризирует
4. LLM вызывает send_telegram_message
5. Сообщение отправлено в Telegram
6. LLM отвечает пользователю: "Собрал переписку за последние 24 часа, суммаризировал и отправил сводку в ваш Telegram"
```

---

## 🎯 Итоговый результат

1. ✅ MCP сервер с инструментом отправки в Telegram
2. ✅ MCP клиент и менеджер для работы с несколькими серверами
3. ✅ LLM агент, который сам решает последовательность вызовов инструментов
4. ✅ Каскадные вызовы: LLM → инструмент 1 → результат → LLM → инструмент 2 → результат → LLM → финальный ответ
5. ✅ UI с индикатором загрузки для длительных операций
6. ✅ THEORY.md с объяснением композиции MCP инструментов

**Важно:** 
- LLM полностью управляет последовательностью вызовов
- Мы не программируем последовательность, а даём LLM доступ к инструментам
- Работаем только с JAR файлами MCP серверов
- Код разбит на логические части (MCP клиент, менеджер, агент, API, UI)

---

## 📝 Дополнительные рекомендации

- **Защита от бесконечного цикла:** ограничь максимальное количество итераций (например, 10)
- **Таймауты:** установи таймауты для вызовов MCP инструментов (например, 30 секунд)
- **Логирование:** логируй все вызовы инструментов, результаты, ошибки
- **Обработка ошибок:** если инструмент вернул ошибку, передай её LLM, пусть она решает, что делать
- **История диалога:** сохраняй историю для контекста, но ограничь размер (последние N сообщений)
- **Streaming (опционально):** для будущего можно добавить WebSocket для streaming ответов LLM

**Готово к реализации!** 🚀

