# Создание MCP сервера на Kotlin

## Введение

Model Context Protocol (MCP) — это открытый протокол для взаимодействия LLM с внешними инструментами и источниками данных. В этом документе мы рассмотрим, как создать собственный MCP сервер на Kotlin.

## Основные компоненты

### 1. Зависимости

Для создания MCP сервера на Kotlin нужно добавить официальный SDK:

```kotlin
dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk:1.0.0")
}
```

### 2. Структура проекта

Рекомендуемая структура MCP сервера:

```
mcp-server/
├── src/main/kotlin/com/prike/mcpserver/
│   ├── Main.kt
│   ├── Config.kt
│   ├── server/
│   │   └── MCPServer.kt
│   ├── tools/
│   │   ├── handlers/
│   │   │   └── ToolHandler.kt
│   │   └── ToolRegistry.kt
│   └── data/
│       └── repository/
└── config/
    └── mcp-server.yaml
```

## Регистрация инструментов

### Базовый обработчик

Все обработчики инструментов должны наследоваться от абстрактного класса `ToolHandler`:

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

### Пример инструмента

```kotlin
class GetDataHandler(
    private val repository: DataRepository
) : ToolHandler<GetDataHandler.Params, List<Data>>() {
    
    override val logger = LoggerFactory.getLogger(GetDataHandler::class.java)
    
    override fun execute(params: Params): List<Data> {
        return repository.getData(params.startTime, params.endTime)
    }
    
    override fun prepareResult(request: Params, result: List<Data>): TextContent {
        val json = Json.encodeToString(result)
        return TextContent(text = json)
    }
    
    data class Params(
        val startTime: Long,
        val endTime: Long
    )
}
```

## Конфигурация

### YAML конфигурация

```yaml
database:
  path: "data/database.db"
  
telegram:
  botToken: "${TELEGRAM_BOT_TOKEN}"
  groupId: "${TELEGRAM_GROUP_ID}"
```

## Логирование

**КРИТИЧЕСКИ ВАЖНО:** Все логи должны идти в `stderr`, а не в `stdout`!

Настрой `logback.xml`:

```xml
<configuration>
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

## Сборка JAR

Для создания исполняемого JAR файла используй Shadow plugin:

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

tasks.shadowJar {
    archiveBaseName.set("mcp-server")
    archiveVersion.set("1.0.0")
    mergeServiceFiles()
}
```

Затем собери JAR:

```bash
./gradlew shadowJar
```

JAR файл будет в `build/libs/mcp-server-1.0.0.jar`.

## Тестирование

Для тестирования MCP сервера можно использовать MCP клиент или напрямую через stdio:

```bash
java -jar mcp-server-1.0.0.jar
```

Сервер будет ожидать JSON-RPC сообщения через stdin и отправлять ответы через stdout.

## Заключение

Создание MCP сервера на Kotlin — это мощный способ расширить возможности LLM агентов. Используй официальный SDK, следуй лучшим практикам и не забывай про логирование в stderr.


