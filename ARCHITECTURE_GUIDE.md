# Полный архитектурный гайд проекта AI Challenge

> 📖 **Единая точка входа** для всей информации об архитектуре, MCP, лучших практиках и шаблонах проекта.

## Содержание

1. [Архитектура проекта](#1-архитектура-проекта)
2. [Структура слоев](#2-структура-слоев)
3. [Работа с MCP серверами](#3-работа-с-mcp-серверами)
4. [Лучшие практики](#4-лучшие-практики)
5. [Шаблоны системных промптов](#5-шаблоны-системных-промптов)
6. [Создание нового урока](#6-создание-нового-урока)

---

## 1. Архитектура проекта

### 1.1. Обзор

Проект использует **Clean Architecture** с разделением на слои:

```
┌─────────────────────────────────────────────────────────┐
│              Presentation Layer (HTTP API)              │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Controllers → вызывают агентов напрямую         │  │
│  │  Client DTOs → только примитивы                  │  │
│  └───────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────┐
│              Domain Layer (Бизнес-логика)               │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Agents → логика работы с LLM                     │  │
│  │  Exceptions → доменные исключения                 │  │
│  └───────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────┐
│              Data Layer (Инфраструктура)                │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Clients → HTTP клиенты (OpenAI, MCP)             │  │
│  │  Repositories → работа с DTO                       │  │
│  │  Mappers → преобразование данных                   │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 1.2. Структура папок урока

```
lesson-XX-description/
├── server/                         # Kotlin + Ktor сервер
│   └── src/main/kotlin/com/prike/
│       ├── Main.kt                 # Точка входа, настройка Ktor
│       ├── Config.kt               # Загрузка конфигурации из .env и YAML
│       │
│       ├── domain/                 # 💖 Доменный слой (логика работы с LLM)
│       │   ├── agent/              # Все агенты (базовые и специализированные)
│       │   │   ├── BaseAgent.kt    # Базовый абстрактный класс
│       │   │   └── [AgentName]Agent.kt  # Специализированные агенты
│       │   └── exception/          # Доменные исключения
│       │
│       ├── data/                   # 🔌 Слой данных (работа с внешними API/БД)
│       │   ├── client/             # HTTP клиенты для внешних API
│       │   │   ├── OpenAIClient.kt
│       │   │   └── MCPClient.kt    # MCP клиент (если используется)
│       │   ├── dto/                # DTO для внешних API (LLM API, не для клиента)
│       │   ├── repository/         # Репозитории для работы с внешними API
│       │   │   └── AIRepository.kt
│       │   └── mapper/             # Мапперы для преобразования данных
│       │
│       ├── presentation/           # 🌐 Слой презентации (HTTP API)
│       │   ├── controller/         # HTTP контроллеры (вызывают агентов)
│       │   │   ├── ChatController.kt
│       │   │   └── WebSocketChatController.kt  # WebSocket (если используется)
│       │   └── dto/                # DTO для клиента (только примитивы)
│       │
│       ├── di/                     # 💉 Dependency Injection
│       │   └── AppModule.kt        # Создание и связывание зависимостей
│       │
│       └── config/                 # ⚙️ Конфигурация
│           └── [ConfigName]Config.kt
│
├── client/                         # Веб-клиент
│   ├── index.html
│   ├── style.css
│   └── app.js
│
├── config/                         # Конфигурационные файлы
│   ├── ai.yaml                     # Конфигурация AI (не в git)
│   ├── ai.yaml.example             # Пример конфигурации AI
│   └── mcp-servers.yaml            # Конфигурация MCP серверов (если используется)
│
└── README.md                       # Описание задания
```

### 1.3. Правила зависимостей

```
Presentation → Domain → Data
     ↓            ↓        ↓
  Client DTOs  Agents   LLM DTOs
```

**Запрещено:**
- ❌ Клиентские DTO не должны зависеть от LLM DTO
- ❌ Контроллер не должен напрямую использовать LLM DTO
- ❌ Data слой не должен зависеть от Presentation
- ❌ Domain не должен зависеть от Presentation

**Разрешено:**
- ✅ Presentation → Domain (контроллеры вызывают агентов)
- ✅ Domain → Data (агенты используют репозитории и клиенты)
- ✅ Data → Data (репозитории используют клиенты)

---

## 2. Структура слоев

### 2.1. Domain Layer (Доменный слой)

**Назначение:** Чистая бизнес-логика работы с LLM, не зависит от внешних библиотек.

#### Агенты (`domain/agent/`)

**Базовый агент** (`BaseAgent.kt`):
```kotlin
abstract class BaseAgent(
    protected val aiRepository: AIRepository
) {
    suspend fun getMessage(userMessage: String): String {
        return aiRepository.getMessage(userMessage)
    }
    
    protected suspend fun getMessageWithHistory(
        messages: List<MessageDto>
    ): AIRepository.MessageResult {
        return aiRepository.getMessageWithHistory(messages)
    }
}
```

**Специализированные агенты:**
- Наследуют `BaseAgent`
- Используют `AIRepository.getMessageWithHistory()` для получения данных
- Управляют историей сообщений (опционально)
- Имеют свой системный промпт
- Обрабатывают результаты специфичным образом

**Пример:**
```kotlin
class OrchestrationAgent(
    private val aiRepository: AIRepository,
    private val mcpToolAgent: MCPToolAgent
) {
    suspend fun processUserMessage(
        userMessage: String,
        statusCallback: ((String) -> Unit)? = null,
        toolCallCallback: ((String, String, String?) -> Unit)? = null
    ): AgentResponse {
        // Логика обработки с вызовом инструментов
    }
}
```

### 2.2. Data Layer (Слой данных)

**Назначение:** Инфраструктура для работы с внешними API/БД.

#### Клиенты (`data/client/`)

**OpenAIClient** - HTTP клиент для OpenAI/OpenRouter API:
- Настраивается через `AIConfig`
- Поддерживает системные промпты
- Поддерживает JSON mode
- Обработка ошибок и таймаутов
- Логирование запросов/ответов

**MCPClient** - клиент для подключения к MCP серверам:
- Подключение через stdio/SSE
- Получение списка инструментов
- Вызов инструментов
- Управление соединением

#### Репозитории (`data/repository/`)

**AIRepository** - репозиторий для работы с LLM:
```kotlin
interface AIRepository {
    suspend fun getMessage(userMessage: String): String
    suspend fun getMessageWithHistory(
        messages: List<MessageDto>
    ): MessageResult
    
    suspend fun getMessageWithTools(
        messages: List<MessageDto>,
        tools: List<ToolDto>?
    ): OpenAIResponse
}
```

#### DTO (`data/dto/`)

- DTO для работы с LLM API (OpenAI, OpenRouter)
- **НЕ используются** в Presentation слое
- Содержат структуры, специфичные для LLM API

### 2.3. Presentation Layer (Слой презентации)

**Назначение:** HTTP API слой.

#### Контроллеры (`presentation/controller/`)

**ChatController** - HTTP контроллер:
```kotlin
class ChatController(
    private val orchestrationAgent: OrchestrationAgent
) {
    fun registerRoutes(routing: Routing) {
        routing.post("/api/chat/message") {
            // Обработка HTTP запроса
        }
    }
}
```

**WebSocketChatController** - WebSocket контроллер для real-time обновлений:
```kotlin
class WebSocketChatController(
    private val orchestrationAgent: OrchestrationAgent
) {
    fun registerRoutes(routing: Routing) {
        routing.webSocket("/api/chat/ws") {
            // WebSocket соединение
            // Отправка статусов в реальном времени
        }
    }
}
```

#### DTO (`presentation/dto/`)

- DTO для HTTP запросов/ответов
- **Только примитивы** (String, Int, Boolean, etc.)
- **НЕ зависят** от LLM DTO
- Понятные названия (например, `ChatRequestDto`, `ChatResponseDto`)

---

## 3. Работа с MCP серверами

### 3.1. Что такое MCP?

**Model Context Protocol (MCP)** - протокол для подключения инструментов к LLM агентам.

**Преимущества:**
- Стандартизированный протокол
- Изоляция инструментов в отдельных серверах
- Легкое добавление новых инструментов
- Возможность композиции нескольких серверов

### 3.2. Создание MCP сервера

#### Структура проекта

```
mcp-server/
├── build.gradle.kts
├── config/
│   └── mcp-server.yaml
└── src/main/kotlin/com/example/mcpserver/
    ├── Main.kt
    ├── config/
    ├── data/
    │   └── repository/
    ├── tools/
    │   ├── ToolRegistry.kt
    │   └── handlers/
    └── utils/
```

#### Шаблон обработчика инструмента

```kotlin
abstract class ToolHandler<Input, Output> {
    protected abstract val logger: Logger

    open fun handle(params: Input): CallToolResult {
        return try {
            val result = execute(params)
            CallToolResult(
                content = listOf(prepareResult(params, result))
            )
        } catch (e: Exception) {
            logger.error("Ошибка выполнения инструмента: ${e.message}", e)
            CallToolResult(
                content = listOf(TextContent(text = "Ошибка: ${e.message}"))
            )
        }
    }

    protected abstract fun execute(params: Input): Output
    protected abstract fun prepareResult(request: Input, result: Output): TextContent
}
```

#### Регистрация инструмента

```kotlin
server.addTool(
    name = "get_chat_history",
    description = "Получить сообщения за период",
    inputSchema = Tool.Input(
        properties = buildJsonObject {
            putJsonObject("startTime") { put("type", "number") }
            putJsonObject("endTime") { put("type", "number") }
        },
        required = listOf("startTime", "endTime")
    )
) { request ->
    val params = GetChatHistoryParams.from(request.arguments)
    val messages = repository.getMessagesBetween(params.startTime, params.endTime)
    handler.handle(messages)
}
```

### 3.3. Подключение MCP сервера

#### MCPClientManager

Управляет несколькими MCP клиентами:

```kotlin
class MCPClientManager(
    private val config: MCPConfig,
    private val lessonRoot: File
) {
    private val clients = mutableMapOf<String, MCPClient>()
    
    suspend fun initialize() {
        coroutineScope {
            val connectionJobs = config.servers.map { serverConfig ->
                async {
                    try {
                        connectToServer(serverConfig)
                        true
                    } catch (e: Exception) {
                        logger.error("Failed to connect to server ${serverConfig.id}: ${e.message}", e)
                        false
                    }
                }
            }
            connectionJobs.awaitAll()
        }
        logger.info("Connected to ${clients.size} MCP server(s)")
    }
    
    suspend fun callTool(serverId: String, toolName: String, arguments: JsonObject): String {
        val client = clients[serverId]
            ?: throw IllegalArgumentException("MCP server not found: $serverId")
        return client.callTool(toolName, arguments)
    }
}
```

#### MCPToolAgent

Преобразует MCP инструменты в формат LLM tools:

```kotlin
class MCPToolAgent(
    private val mcpClientManager: MCPClientManager
) {
    suspend fun getLLMTools(): List<ToolDto> {
        val mcpTools = mcpClientManager.listAllTools()
        return mcpTools.map { mcpTool ->
            ToolDto(
                type = "function",
                function = FunctionDto(
                    name = mcpTool.name,
                    description = mcpTool.description ?: "Инструмент ${mcpTool.name}",
                    parameters = mcpTool.inputSchema ?: buildJsonObject { }
                )
            )
        }
    }
    
    suspend fun callTool(toolName: String, arguments: JsonObject): String {
        val serverId = mcpClientManager.findServerForTool(toolName)
            ?: throw IllegalArgumentException("Tool not found: $toolName")
        return mcpClientManager.callTool(serverId, toolName, arguments)
    }
}
```

### 3.4. Использование в агенте

```kotlin
class OrchestrationAgent(
    private val aiRepository: AIRepository,
    private val mcpToolAgent: MCPToolAgent
) {
    suspend fun processUserMessage(userMessage: String): AgentResponse {
        // Получаем список доступных инструментов
        val availableTools = mcpToolAgent.getLLMTools()
        
        // Отправляем запрос в LLM с инструментами
        val llmResponse = aiRepository.getMessageWithTools(
            messages = messages,
            tools = availableTools
        )
        
        // Если LLM хочет вызвать инструмент
        if (llmResponse.hasToolCall()) {
            val toolName = llmResponse.getToolName()
            val arguments = llmResponse.getToolArguments()
            
            // Вызываем MCP инструмент
            val result = mcpToolAgent.callTool(toolName, arguments)
            
            // Продолжаем цикл с результатом
        }
    }
}
```

### 3.5. Конфигурация MCP серверов

**config/mcp-servers.yaml:**
```yaml
mcp:
  servers:
    - id: "data-collection"
      name: "Data Collection MCP Server"
      description: "Сбор данных из разных источников"
      jarPath: "mcp-servers/data-collection-mcp-server-1.0.0.jar"
      configPath: "config/data-collection-mcp-server.yaml"
      type: "local"
      tools:
        - get_chat_history
        - get_telegram_messages
```

### 3.6. ⚠️ Критически важно: Логирование в MCP серверах

**Проблема:** При использовании stdio транспорта MCP протокол использует `stdout` для JSON-RPC сообщений. Если логи идут в `stdout`, клиент пытается парсить их как JSON-RPC и получает ошибки.

**Решение:** Настройте `logback.xml` так, чтобы все логи шли в `stderr`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Логи в stderr, чтобы не мешать MCP протоколу в stdout -->
    <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
        <target>System.err</target>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDERR" />
    </root>
</configuration>
```

**Правило:**
- ✅ `stdout` — только для JSON-RPC сообщений MCP протокола
- ✅ `stderr` — для всех логов, ошибок, отладочной информации

---

## 4. Лучшие практики

### 4.1. Работа с инструментами и заполнение content

#### ⚠️ КРИТИЧЕСКИ ВАЖНО

При вызове инструмента поле `content` **ОБЯЗАТЕЛЬНО** должно быть заполнено текстовым описанием действия. Это правило действует для **КАЖДОГО** вызова инструмента: первого, второго, третьего и всех последующих!

#### Реализация в коде

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

### 4.2. WebSocket для real-time обновлений

#### Структура WebSocket сообщений

```kotlin
// DTO для статуса от LLM (описание действия)
@Serializable
data class StatusUpdate(
    val type: String = "status",
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

// DTO для статуса вызова инструмента
@Serializable
data class ToolCallUpdate(
    val type: String = "tool_call",
    val toolName: String,
    val status: String, // "starting", "success", "error"
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// DTO для финального ответа
@Serializable
data class FinalResponse(
    val type: String = "final",
    val message: String,
    val toolCalls: List<ToolCallInfoDto> = emptyList(),
    val processingTime: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
```

#### Callbacks в агенте

```kotlin
suspend fun processUserMessage(
    userMessage: String,
    statusCallback: ((String) -> Unit)? = null,
    toolCallCallback: ((String, String, String?) -> Unit)? = null
): AgentResponse {
    // При получении ответа от LLM с tool_calls
    if (hasToolCalls) {
        val llmStatusMessage = assistantMessage.content?.trim()
        if (!llmStatusMessage.isNullOrBlank()) {
            statusCallback?.invoke(llmStatusMessage)
        }
    }
    
    // При начале вызова инструмента
    toolCallCallback?.invoke(toolName, "starting", null)
    
    // При завершении вызова инструмента
    try {
        val result = mcpToolAgent.callTool(toolName, arguments)
        toolCallCallback?.invoke(toolName, "success", "Инструмент выполнен успешно")
    } catch (e: Exception) {
        toolCallCallback?.invoke(toolName, "error", "Ошибка: ${e.message}")
    }
}
```

### 4.3. Логирование

#### Принципы

1. **INFO** - важные события (подключение серверов, успешные операции)
2. **WARN** - предупреждения (пустой content, повторные вызовы)
3. **ERROR** - ошибки с полным стектрейсом
4. **DEBUG** - детальная диагностика (только при необходимости, убрать в production)

#### Примеры

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

### 4.4. Обработка ошибок

#### Принципы

1. **Логируйте все ошибки** с полным контекстом
2. **Не скрывайте ошибки от пользователя** - показывайте понятные сообщения
3. **Graceful degradation** - если один инструмент не работает, продолжайте с другими
4. **Retry механизм** - для временных ошибок (опционально)

#### Пример

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

---

## 5. Шаблоны системных промптов

### 5.1. Базовый шаблон для работы с инструментами

```kotlin
private fun buildSystemPrompt(): String {
    return """
        Ты — интеллектуальный ассистент, который может использовать инструменты для выполнения задач пользователя.
        
        ════════════════════════════════════════════════════════════════════════════════
        🚨 САМОЕ КРИТИЧЕСКОЕ ПРАВИЛО - ЧИТАЙ ПЕРВЫМ И ЗАПОМНИ НАВСЕГДА! 🚨
        ════════════════════════════════════════════════════════════════════════════════
        
        ПРИ КАЖДОМ ВЫЗОВЕ ИНСТРУМЕНТА ПОЛЕ "content" ОБЯЗАТЕЛЬНО ДОЛЖНО БЫТЬ ЗАПОЛНЕНО!
        
        ⚠️ ВАЖНО: Это правило действует для КАЖДОГО вызова инструмента:
        - Первый вызов инструмента → content ОБЯЗАТЕЛЬНО должен быть заполнен!
        - Второй вызов инструмента → content ОБЯЗАТЕЛЬНО должен быть заполнен!
        - Третий вызов инструмента → content ОБЯЗАТЕЛЬНО должен быть заполнен!
        - Четвертый, пятый и ВСЕ последующие вызовы → content ОБЯЗАТЕЛЬНО должен быть заполнен!
        
        ❌ НИКОГДА НЕ ОСТАВЛЯЙ content ПУСТЫМ, null ИЛИ undefined ПРИ ВЫЗОВЕ ИНСТРУМЕНТА!
        ❌ НЕ ДУМАЙ, что описание нужно только в первый раз - оно нужно КАЖДЫЙ РАЗ!
        ✅ ВСЕГДА ДОБАВЛЯЙ В content ТЕКСТОВОЕ ОПИСАНИЕ ТОГО, ЧТО ТЫ ДЕЛАЕШЬ ПРЯМО СЕЙЧАС!
        
        Примеры ПРАВИЛЬНОГО использования (для каждого вызова инструмента):
        - Итерация 1: content = "Получаю данные из Telegram..." + tool_calls = [...]
        - Итерация 2: content = "Анализирую полученные сообщения..." + tool_calls = [...]
        - Итерация 3: content = "Извлекаю ключевые слова из текста..." + tool_calls = [...]
        
        ════════════════════════════════════════════════════════════════════════════════
        
        ⚠️ КРИТИЧЕСКИ ВАЖНО - ОПИСАНИЕ ВЫПОЛНЯЕМОГО ДЕЙСТВИЯ В CONTENT:
        
        При вызове инструмента ты ОБЯЗАНА добавить в поле "content" краткое описание того, что ты делаешь.
        Это описание отображается пользователю в UI, чтобы он понимал, что происходит в каждый момент времени.
        
        ✅ ПРАВИЛЬНЫЙ ФОРМАТ ОТВЕТА ПРИ ВЫЗОВЕ ИНСТРУМЕНТА:
        - Поле "tool_calls" ОБЯЗАТЕЛЬНО должно содержать массив вызовов инструментов
        - Поле "content" ОБЯЗАТЕЛЬНО должно содержать краткое описание выполняемого действия (НЕ пустое, НЕ null!)
        - Это правило действует для КАЖДОГО вызова инструмента: первого, второго, третьего и всех последующих!
        
        Примеры правильных описаний в content:
        - "Получаю историю переписки за последние 7 дней из Telegram..."
        - "Анализирую полученные сообщения и извлекаю ключевые темы..."
        - "Рассчитываю статистику по сообщениям..."
        - "Извлекаю ключевые слова из текста..."
        - "Формирую отчет на основе собранных данных..."
        
        ❌ НЕПРАВИЛЬНО (НЕ ДЕЛАЙ ТАК!):
        - content = null
        - content = ""
        - content = "{}"
        - content = "{\"tool_calls\": [...]}"
        
        ✅ ПРАВИЛЬНО (ДЕЛАЙ ТАК!):
        - content = "Получаю данные из Telegram..."
        - content = "Анализирую сообщения..."
        - content = "Вызываю инструмент для обработки..."
        
        ⚠️ КРИТИЧЕСКИ ВАЖНО - ФОРМАТ ВЫЗОВА ИНСТРУМЕНТОВ:
        
        Когда нужно вызвать инструмент, ты ДОЛЖЕН использовать функцию calling механизм API через поле "tool_calls".
        КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО писать JSON в поле "content"!
        
        ✅ ПРАВИЛЬНО: использовать поле "tool_calls" в структуре ответа И добавить описание в content
        ❌ НЕПРАВИЛЬНО: писать JSON в поле "content" (например: "content": "{\"tool_calls\": [...]}")
        
        Правильный формат ответа при вызове инструмента:
        - Поле "content" ОБЯЗАТЕЛЬНО должно содержать краткое описание выполняемого действия (НЕ пустое, НЕ null!)
        - Поле "tool_calls" должно содержать массив вызовов инструментов
        - Каждый вызов должен иметь структуру: id, type="function", function={name, arguments}
        
        [Добавьте здесь специфичные для вашего проекта инструкции...]
    """.trimIndent()
}
```

### 5.2. Дополнительные правила (добавьте по необходимости)

#### Работа с историей диалога

```
⚠️ КРИТИЧЕСКИ ВАЖНО - ПРОВЕРКА ИСТОРИИ ДИАЛОГА:

- ПЕРЕД КАЖДЫМ вызовом инструмента ОБЯЗАТЕЛЬНО проверяй историю диалога выше!
- Если в истории уже есть вызов инструмента с теми же аргументами и результат уже получен - НЕ ВЫЗЫВАЙ ЕГО ПОВТОРНО!
- Если ты видишь в истории assistant message с tool_calls и затем результат от tool - данные УЖЕ получены, используй их!
- НЕ вызывай один и тот же инструмент дважды с одинаковыми аргументами!
```

#### Последовательные вызовы

```
⚠️ СТРОГОЕ ОГРАНИЧЕНИЕ: В ОДНОМ ОТВЕТЕ МОЖНО ВЫЗВАТЬ ТОЛЬКО ОДИН ИНСТРУМЕНТ!
- Если ты попытаешься вызвать несколько инструментов одновременно, система вызовет ТОЛЬКО ПЕРВЫЙ, остальные будут проигнорированы!
- Вызывай инструменты строго по одному: вызови один → получи результат → проанализируй → вызови следующий
```

---

## 6. Создание нового урока

### 6.1. Шаги

1. **Скопировать шаблон:**
   ```bash
   cp -r lesson-00-project-template lesson-XX-description
   ```

2. **Настроить конфигурацию:**
   ```bash
   cd lesson-XX-description
   # .env файл находится в корне проекта (ai_challenge_november/.env)
   # Убедитесь, что OPENAI_API_KEY установлен в корневом .env
   
   cp config/ai.yaml.example config/ai.yaml
   # Отредактируйте config/ai.yaml (опционально)
   ```

3. **Добавить логику:**
   - Создайте контроллеры в `presentation/controller/` (вызывают агентов)
   - Создайте клиентские DTO в `presentation/dto/` (только примитивы)
   - Создайте специализированные агенты в `domain/agent/` (наследуют `BaseAgent`)
   - При необходимости создайте репозитории в `data/repository/`
   - Настройте DI в `di/AppModule.kt`

4. **Обновить Main.kt:**
   - Добавьте свои контроллеры в блок `routing { }`
   - Настройте DI в `AppModule`, если нужно

5. **Запустить сервер:**
   ```bash
   cd server
   ./gradlew run
   ```

### 6.2. Чеклист для нового урока

- [ ] Скопирован шаблон из `lesson-00-project-template`
- [ ] Настроена конфигурация (`.env` в корне проекта, `config/ai.yaml`)
- [ ] Созданы контроллеры в `presentation/controller/`
- [ ] Созданы клиентские DTO в `presentation/dto/`
- [ ] Созданы агенты в `domain/agent/` (наследуют `BaseAgent`)
- [ ] Настроен DI в `di/AppModule.kt`
- [ ] Обновлен `Main.kt` с регистрацией контроллеров
- [ ] Если используются инструменты - добавлены правила о заполнении `content` в системный промпт
- [ ] Если нужны real-time обновления - добавлен WebSocket контроллер
- [ ] Настроено логирование (INFO для важных событий, WARN для предупреждений, ERROR для ошибок)
- [ ] Создан `README.md` с описанием задания

### 6.3. Если используются MCP серверы

- [ ] Создан `MCPClientManager` для управления несколькими серверами
- [ ] Создан `MCPToolAgent` для преобразования MCP инструментов в формат LLM
- [ ] Настроена конфигурация в `config/mcp-servers.yaml`
- [ ] Настроено логирование в MCP серверах (логи в `stderr`, не в `stdout`)
- [ ] Добавлена инициализация MCP серверов в `Main.kt`
- [ ] Добавлена обработка ошибок подключения

---

## См. также

- **[BEST_PRACTICES.md](./BEST_PRACTICES.md)** - подробное описание лучших практик с примерами кода
- **[PROMPT_TEMPLATE.md](./PROMPT_TEMPLATE.md)** - расширенный шаблон системного промпта
- **[PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md)** - полный контекст проекта и архитектура
- **[MCP_GUIDE.md](./MCP_GUIDE.md)** - подробное руководство по работе с MCP серверами
- **[RAG_APPLICABILITY.md](./RAG_APPLICABILITY.md)** - применимость архитектуры и наработок для RAG уроков
- **[lesson-00-project-template/README.md](./lesson-00-project-template/README.md)** - описание шаблона проекта

---

**Примечание:** Этот файл является единой точкой входа для всей информации об архитектуре, MCP, лучших практиках и шаблонах проекта. Все важные детали собраны здесь, с ссылками на дополнительные документы для более глубокого изучения.

