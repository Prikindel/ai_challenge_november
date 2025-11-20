# MCP Guide

## 1. Шаблон процесса создания MCP сервера

### 1.1. Основные шаги
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

### 1.2. Структура Kotlin-проекта (пример)
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
    ├── telegram/
    ├── tools/
    │   ├── ToolRegistry.kt
    │   └── handlers/
    └── utils/
```

### 1.3. Шаблон обработчика инструмента
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

### 1.4. Пример регистрации инструмента (Kotlin)
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

## 2. Организация работы MCP клиента

### 2.1. Взаимодействие напрямую (без LLM)
1. **MCPClient**:
   - Подключается к серверу (`connectToServer`).
   - Получает список инструментов (`listTools`).
   - Вызывает инструмент (`callTool`).
2. **Использование**:
```kotlin
val client = MCPClient("webChat")
client.connectToServer(jarPath, lessonRoot)
val tools = client.listTools()
val result = client.callTool("get_chat_history", jsonArguments)
```
3. **Сценарии**:
   - Админские задачи.
   - Ручные интеграционные тесты.
   - Автоматические ETL скрипты.

### 2.2. Взаимодействие через LLM
1. **MCPClientManager** — управляет несколькими клиентами, хранит их состояние.
2. **MCPToolAgent** — превращает список MCP инструментов в формат LLM tools.
3. **LLM агент**:
   - Получает сообщения пользователя.
   - Вызывает LLM с `tools` (function calling).
   - Если LLM просит инструмент — вызывает соответствующий MCP через `callTool`.
   - Возвращает результат пользователю.
4. **Схема**:
```
Пользователь → LLMWithSummaryAgent → LLM (с tools)
    ↳ tool_call → MCPToolAgent → MCPClientManager → MCP server
    ↳ ответ MCP → LLM (final response) → Пользователь/Планировщик
```
5. **Псевдокод**:
```kotlin
val mcpTools = mcpToolAgent.getAvailableTools()
val llmResponse = aiRepository.getMessageWithTools(messages, mcpTools)
if (llmResponse.hasToolCall()) {
    val toolResult = mcpToolAgent.callTool(sourceId, toolName, args)
    val finalResponse = aiRepository.getMessageWithTools(messages + toolResult)
}
```

### 2.3. Remote MCP (через SSE)
- Если MCP доступен по HTTPS, можно указать его в `tools` прямо в запросе к OpenAI Responses:
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
- Тогда OpenAI сам общается с MCP по SSE. Нужно, чтобы сервер был доступен из интернета.

## 3. Рекомендации
- **Разделяйте источники**: отдельные MCP серверы для разных доменов (чаты, Telegram, CRM).
- **Конфигурация**: используйте YAML + `.env` для путей и токенов.
- **Логи**: логируйте вызовы инструментов, время ответа, ошибки.
- **Планировщик**: запускайте только после подключения MCP серверов; первая задача — через интервал.
- **Тесты**: пишите интеграционные тесты для инструментов и клиентов.

