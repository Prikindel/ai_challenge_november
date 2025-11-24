# MCP Guide

> 📖 **Основная информация:** Вся информация о MCP, архитектуре и лучших практиках находится в **[ARCHITECTURE_GUIDE.md](./ARCHITECTURE_GUIDE.md)** - единой точке входа.

Этот документ содержит **детальное руководство** по работе с MCP серверами с дополнительными примерами и рекомендациями.

## Содержание

- [Шаблон процесса создания MCP сервера](#шаблон-процесса-создания-mcp-сервера)
- [Организация работы MCP клиента](#организация-работы-mcp-клиента)
- [Рекомендации](#рекомендации)
- [Критически важно: Логирование](#критически-важно-логирование)

---

## Шаблон процесса создания MCP сервера

### Основные шаги

1. **Определить задачи**: какие инструменты нужны (например, `get_chat_history`, `send_report`).
2. **Выбрать транспорт**: `stdio` для локального использования, `SSE/WebSocket` для удаленного доступа.
3. **Инициализировать проект**: Gradle/Kotlin или Python, добавить зависимости MCP SDK.
4. **Настроить конфигурацию**:
   - YAML/ENV с путями к БД, токенами, режимами запуска.
   - Логи и уровни доступа.
5. **Реализовать репозитории**: доступ к данным (SQLite, API, файловая система).
6. **Создать инструменты**:
   - Описать схему входных параметров.
   - Реализовать обработчики (см. шаблон `ToolHandler` ниже).
   - Добавить в реестр и зарегистрировать на сервере.
7. **Запуск сервера**:
   - Локально через `./gradlew run` или `python main.py`.
   - Для production — собрать JAR/Docker и развернуть.
8. **Тестирование**:
   - Локальные вызовы через MCP клиент.
   - Интеграционные тесты с LLM.

### Структура Kotlin-проекта (пример)

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

### Шаблон обработчика инструмента

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

### Пример регистрации инструмента (Kotlin)

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

**См. также:** [ARCHITECTURE_GUIDE.md - Раздел 3.2](./ARCHITECTURE_GUIDE.md#32-создание-mcp-сервера)

---

## Организация работы MCP клиента

### Взаимодействие напрямую (без LLM)

```kotlin
val client = MCPClient("webChat")
client.connectToServer(jarPath, lessonRoot)
val tools = client.listTools()
val result = client.callTool("get_chat_history", jsonArguments)
```

**Сценарии:**
- Админские задачи.
- Ручные интеграционные тесты.
- Автоматические ETL скрипты.

### Взаимодействие через LLM

**Схема:**
```
Пользователь → LLMWithSummaryAgent → LLM (с tools)
    ↳ tool_call → MCPToolAgent → MCPClientManager → MCP server
    ↳ ответ MCP → LLM (final response) → Пользователь/Планировщик
```

**Псевдокод:**
```kotlin
val mcpTools = mcpToolAgent.getAvailableTools()
val llmResponse = aiRepository.getMessageWithTools(messages, mcpTools)
if (llmResponse.hasToolCall()) {
    val toolResult = mcpToolAgent.callTool(sourceId, toolName, args)
    val finalResponse = aiRepository.getMessageWithTools(messages + toolResult)
}
```

**См. также:** [ARCHITECTURE_GUIDE.md - Раздел 3.3 и 3.4](./ARCHITECTURE_GUIDE.md#33-подключение-mcp-сервера)

### Remote MCP (через SSE)

Если MCP доступен по HTTPS, можно указать его в `tools` прямо в запросе к OpenAI Responses:

```javascript
const resp = await client.responses.create({
  model: "gpt-5",
  tools: [
    {
      type: "mcp",
      server_label: "summary",
      server_url: "https://your-mcp.example.com/sse",
      require_approval: "never"
    }
  ],
  input: "Собери summary за 24 часа"
});
```

Тогда OpenAI сам общается с MCP по SSE. Нужно, чтобы сервер был доступен из интернета.

---

## Рекомендации

- **Разделяйте источники**: отдельные MCP серверы для разных доменов (чаты, Telegram, CRM).
- **Конфигурация**: используйте YAML + `.env` для путей и токенов.
- **Логи**: логируйте вызовы инструментов, время ответа, ошибки.
- **Планировщик**: запускайте только после подключения MCP серверов; первая задача — через интервал.
- **Тесты**: пишите интеграционные тесты для инструментов и клиентов.

---

## Критически важно: Логирование

### ⚠️ Критическая проблема: Логи должны идти в stderr, а не в stdout!

**Проблема:**
При использовании stdio транспорта MCP протокол использует `stdout` для JSON-RPC сообщений. Если логи идут в `stdout`, клиент пытается парсить их как JSON-RPC и получает ошибки:

```
java.lang.IllegalArgumentException: Element class kotlinx.serialization.json.JsonLiteral is not a JsonObject
```

**Решение:**
Настройте `logback.xml` так, чтобы все логи шли в `stderr`:

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

**Проверка:**
Если видите ошибки десериализации при подключении к MCP серверу, проверьте `logback.xml` — логи должны идти в `stderr`.

**См. также:** [ARCHITECTURE_GUIDE.md - Раздел 3.6](./ARCHITECTURE_GUIDE.md#36-критически-важно-логирование-в-mcp-серверах)

---

## См. также

- **[ARCHITECTURE_GUIDE.md](./ARCHITECTURE_GUIDE.md)** - полный архитектурный гайд с информацией о MCP
- **[BEST_PRACTICES.md](./BEST_PRACTICES.md)** - лучшие практики с примерами кода
- **[PROMPT_TEMPLATE.md](./PROMPT_TEMPLATE.md)** - шаблон системного промпта
