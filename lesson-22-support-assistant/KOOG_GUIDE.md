# Полный гайд по Koog

## Оглавление

1. [Введение](#введение)
2. [Установка и настройка](#установка-и-настройка)
3. [Типы агентов](#типы-агентов)
4. [Промпты](#промпты)
5. [Инструменты (Tools)](#инструменты-tools)
6. [Стратегии](#стратегии)
7. [События](#события)
8. [Интеграция с Ktor](#интеграция-с-ktor)
9. [Интеграция с Spring Boot](#интеграция-с-spring-boot)
10. [Model Context Protocol (MCP)](#model-context-protocol-mcp)
11. [Расширенные возможности](#расширенные-возможности)
12. [Тестирование](#тестирование)
13. [API Reference](#api-reference)

---

## Введение

**Koog** — это открытый фреймворк от JetBrains для создания AI-агентов с использованием идиоматичного, типобезопасного Kotlin DSL, специально разработанного для разработчиков на JVM и Kotlin.

### Ключевые особенности

- **Мультиплатформенная разработка**: Развертывание агентов на JVM, JS, WasmJS, Android и iOS с использованием Kotlin Multiplatform
- **Надежность и отказоустойчивость**: Встроенные механизмы повторных попыток и восстановление состояния агента
- **Интеллектуальная компрессия истории**: Оптимизация использования токенов при сохранении контекста в длительных разговорах
- **Интеграция с популярными фреймворками**: Поддержка Ktor и Spring Boot
- **Наблюдаемость с OpenTelemetry**: Встроенная поддержка для мониторинга и отладки
- **Гибкие графовые рабочие процессы**: Проектирование сложных поведений агентов
- **Создание пользовательских инструментов**: Расширение возможностей агентов
- **Комплексное трассирование**: Детальное отслеживание выполнения агентов

### Поддерживаемые LLM-провайдеры

- OpenAI
- Anthropic (Claude)
- Google (Gemini)
- OpenRouter
- DeepSeek
- Mistral AI
- Ollama (локальные модели)
- Bedrock (AWS)

---

## Установка и настройка

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // Основная библиотека Koog
    implementation("ai.koog:koog-agents:0.5.4")
    
    // Интеграция с Ktor (опционально)
    implementation("ai.koog:koog-ktor:0.5.4")
    
    // Для работы с конкретными провайдерами
    implementation("ai.koog:prompt-executor-openai-client:0.5.4")
    implementation("ai.koog:prompt-executor-anthropic-client:0.5.4")
    implementation("ai.koog:prompt-executor-google-client:0.5.4")
    implementation("ai.koog:prompt-executor-openrouter-client:0.5.4")
}
```

### Maven

```xml
<dependency>
    <groupId>ai.koog</groupId>
    <artifactId>koog-agents</artifactId>
    <version>0.5.4</version>
</dependency>
```

### Настройка переменных окружения

```bash
# OpenAI
export OPENAI_API_KEY=your-api-key

# Anthropic
export ANTHROPIC_API_KEY=your-api-key

# Google
export GOOGLE_API_KEY=your-api-key

# OpenRouter
export OPENROUTER_API_KEY=your-api-key
```

---

## Типы агентов

### 1. Базовые агенты (Basic Agents)

Базовые агенты обрабатывают один ввод и предоставляют ответ.

```kotlin
import ai.koog.agents.core.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.model.OpenAIModels

// Создание executor для OpenAI
val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))

// Создание базового агента
val agent = AIAgent(
    executor = executor,
    systemPrompt = "Ты — полезный ассистент.",
    llmModel = OpenAIModels.Chat.GPT4oMini
)

// Запуск агента
val result = agent.run("Привет! Как дела?")
println(result.content)
```

### 2. Функциональные агенты (Functional Agents)

Легковесные агенты с пользовательской логикой на чистом Kotlin.

```kotlin
import ai.koog.agents.core.FunctionalAgent
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))

val agent = FunctionalAgent(executor) { input, llm, tools ->
    // Пользовательская логика обработки
    val response = llm.execute(
        prompt {
            system("Ты — помощник по программированию")
            user(input)
        }
    )
    
    // Можно вызывать инструменты
    // val result = tools.call("tool_name", arguments)
    
    response.content
}

val result = agent.run("Объясни, что такое корутины в Kotlin")
```

### 3. Агенты с комплексными рабочими процессами (Complex Workflow Agents)

Агенты, обрабатывающие сложные рабочие процессы с пользовательскими стратегиями.

```kotlin
import ai.koog.agents.core.AIAgent
import ai.koog.agents.strategies.*
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))

// Создание стратегии с графом узлов
val strategy = strategy {
    node("start") {
        // Логика начального узла
    }
    
    node("process") {
        // Логика обработки
    }
    
    node("end") {
        // Логика завершения
    }
    
    edge("start", "process")
    edge("process", "end")
}

val agent = AIAgent(
    executor = executor,
    strategy = strategy,
    systemPrompt = "Ты — агент для обработки сложных задач"
)

val result = agent.run("Сложная задача")
```

---

## Промпты

### Создание промптов

Koog предоставляет типобезопасный DSL для создания промптов.

```kotlin
import ai.koog.prompt.*

// Базовый промпт
val basicPrompt = prompt {
    system("Ты — полезный ассистент")
    user("Привет!")
}

// Промпт с несколькими сообщениями
val multiMessagePrompt = prompt {
    system("Ты — эксперт по программированию")
    user("Что такое корутины?")
    assistant("Корутины — это легковесные потоки...")
    user("А как их использовать в Kotlin?")
}

// Промпт с мультимодальными данными
val multimodalPrompt = prompt {
    system("Ты — ассистент для анализа изображений")
    user {
        text("Что изображено на этой картинке?")
        image(imageUrl = "https://example.com/image.jpg")
    }
}
```

### Выполнение промптов

```kotlin
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.model.OpenAIModels

val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))

val prompt = prompt {
    system("Ты — помощник")
    user("Привет!")
}

// Выполнение с указанием модели
val result = executor.execute(prompt, OpenAIModels.Chat.GPT4oMini)

// Получение содержимого ответа
println(result.content)

// Получение всех сообщений
result.messages.forEach { message ->
    println("${message.role}: ${message.content}")
}
```

### Переключение между LLM-провайдерами

```kotlin
import ai.koog.prompt.executor.llms.all.*
import ai.koog.prompt.model.*

// OpenAI
val openAIExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
val openAIResult = openAIExecutor.execute(prompt, OpenAIModels.Chat.GPT4oMini)

// Anthropic
val anthropicExecutor = simpleAnthropicExecutor(System.getenv("ANTHROPIC_API_KEY"))
val anthropicResult = anthropicExecutor.execute(prompt, AnthropicModels.Claude3_5Sonnet)

// Google
val googleExecutor = simpleGoogleExecutor(System.getenv("GOOGLE_API_KEY"))
val googleResult = googleExecutor.execute(prompt, GoogleModels.Gemini2_5Pro)
```

---

## Инструменты (Tools)

### Встроенные инструменты

Koog предоставляет набор встроенных инструментов для работы с файлами, HTTP-запросами и т.д.

```kotlin
import ai.koog.agents.tools.*

// Использование встроенных инструментов
val tools = listOf(
    // HTTP инструмент
    HttpTool(),
    
    // Файловый инструмент
    FileTool(),
    
    // Инструмент для работы с базой данных
    DatabaseTool()
)
```

### Аннотационные инструменты

Создание инструментов с помощью аннотаций.

```kotlin
import ai.koog.agents.tools.*

@Tool(
    name = "get_weather",
    description = "Получить текущую погоду в указанном городе"
)
suspend fun getWeather(city: String): String {
    // Логика получения погоды
    return "Погода в $city: 20°C, солнечно"
}

// Регистрация инструмента
val tools = listOf(
    annotationBasedTool(::getWeather)
)
```

### Классовые инструменты

Создание инструментов как классов.

```kotlin
import ai.koog.agents.tools.*
import kotlinx.serialization.Serializable

@Serializable
data class WeatherRequest(val city: String)

class WeatherTool : Tool {
    override val name = "get_weather"
    override val description = "Получить текущую погоду в указанном городе"
    
    override val inputSchema = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("city", buildJsonObject {
                put("type", "string")
                put("description", "Название города")
            })
        })
        put("required", JsonArray(listOf("city")))
    }
    
    override suspend fun invoke(arguments: JsonElement): ToolResult {
        val request = Json.decodeFromJsonElement<WeatherRequest>(arguments)
        // Логика получения погоды
        val weather = "Погода в ${request.city}: 20°C, солнечно"
        return ToolResult.success(Json.encodeToJsonElement(weather))
    }
}

// Использование
val tools = listOf(WeatherTool())
```

### Использование инструментов в агенте

```kotlin
import ai.koog.agents.core.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))

val tools = listOf(
    WeatherTool(),
    // другие инструменты
)

val agent = AIAgent(
    executor = executor,
    systemPrompt = "Ты — помощник с доступом к инструментам",
    tools = tools
)

// Агент автоматически будет использовать инструменты при необходимости
val result = agent.run("Какая погода в Москве?")
```

---

## Стратегии

### Создание стратегий

Стратегии позволяют создавать сложные графы рабочих процессов.

```kotlin
import ai.koog.agents.strategies.*

val strategy = strategy {
    // Определение узлов
    node("start") {
        // Логика начального узла
        // Может вызывать LLM, инструменты и т.д.
    }
    
    node("process") {
        // Логика обработки
    }
    
    node("validate") {
        // Логика валидации
    }
    
    node("end") {
        // Логика завершения
    }
    
    // Определение связей между узлами
    edge("start", "process")
    edge("process", "validate")
    edge("validate", "end")
    
    // Условные переходы
    conditionalEdge("validate", "process") { result ->
        // Условие для возврата к обработке
        result.needsRetry
    }
    conditionalEdge("validate", "end") { result ->
        // Условие для завершения
        !result.needsRetry
    }
}
```

### Предопределенные стратегии

Koog предоставляет набор предопределенных стратегий.

```kotlin
import ai.koog.agents.strategies.*

// Стратегия с автоматическим использованием инструментов
val toolUsingStrategy = ToolUsingStrategy()

// Стратегия с рефлексией
val reflectiveStrategy = ReflectiveStrategy()

// Стратегия с планированием
val planningStrategy = PlanningStrategy()
```

### Параллельное выполнение узлов

```kotlin
import ai.koog.agents.strategies.*

val strategy = strategy {
    node("start") { /* ... */ }
    
    // Параллельное выполнение
    parallel {
        node("task1") { /* ... */ }
        node("task2") { /* ... */ }
        node("task3") { /* ... */ }
    }
    
    node("merge") {
        // Объединение результатов параллельных задач
    }
    
    edge("start", "parallel")
    edge("parallel", "merge")
}
```

---

## События

### Обработка событий жизненного цикла

```kotlin
import ai.koog.agents.events.*

val agent = AIAgent(
    executor = executor,
    eventHandlers = listOf(
        // Обработчик начала выполнения агента
        onAgentStart { event ->
            println("Агент начал выполнение: ${event.input}")
        },
        
        // Обработчик завершения агента
        onAgentEnd { event ->
            println("Агент завершил выполнение: ${event.result}")
        },
        
        // Обработчик вызова LLM
        onLLMCall { event ->
            println("Вызов LLM: ${event.prompt}")
        },
        
        // Обработчик вызова инструмента
        onToolCall { event ->
            println("Вызов инструмента: ${event.toolName}")
        }
    )
)
```

### События стратегий и узлов

```kotlin
import ai.koog.agents.events.*

val eventHandlers = listOf(
    // Событие начала выполнения стратегии
    onStrategyStart { event ->
        println("Стратегия начата: ${event.strategyName}")
    },
    
    // Событие выполнения узла
    onNodeExecute { event ->
        println("Узел выполнен: ${event.nodeName}")
    },
    
    // Событие перехода между узлами
    onEdgeTraverse { event ->
        println("Переход: ${event.fromNode} -> ${event.toNode}")
    }
)
```

---

## Интеграция с Ktor

### Установка плагина

```kotlin
import ai.koog.ktor.*
import io.ktor.server.application.*

fun Application.module() {
    install(Koog) {
        // Настройка LLM-провайдеров
        llm {
            fallback {
                provider = "openai"
                model = "openai.chat.gpt4o-mini"
            }
        }
        
        // Настройка агентов
        agentConfig {
            systemPrompt = "Ты — полезный ассистент"
        }
    }
}
```

### Использование в маршрутах

```kotlin
import ai.koog.ktor.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

routing {
    post("/llm-chat") {
        val userInput = call.receiveText()
        
        // Использование LLM напрямую
        val messages = llm().execute(
            prompt("chat") {
                system("Ты — полезный ассистент")
                user(userInput)
            },
            GoogleModels.Gemini2_5Pro
        )
        
        val responseText = messages.joinToString("\n") { it.content }
        call.respondText(responseText)
    }
    
    post("/agent-chat") {
        val userInput = call.receiveText()
        
        // Использование агента
        val agent = agent("support-agent")
        val result = agent.run(userInput)
        
        call.respondText(result.content)
    }
}
```

### Конфигурация через application.conf

```hocon
koog {
    llm {
        fallback {
            provider = "openai"
            model = "openai.chat.gpt4o-mini"
        }
    }
    
    agents {
        support-agent {
            systemPrompt = "Ты — ассистент поддержки"
            tools = ["weather", "calculator"]
        }
    }
}
```

---

## Интеграция с Spring Boot

### Автоконфигурация

```kotlin
import ai.koog.spring.*
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
```

### Конфигурация через application.yml

```yaml
koog:
  llm:
    fallback:
      provider: openai
      model: openai.chat.gpt4o-mini
  agents:
    support-agent:
      systemPrompt: "Ты — ассистент поддержки"
```

### Использование в контроллерах

```kotlin
import ai.koog.spring.*
import org.springframework.web.bind.annotation.*

@RestController
class ChatController(
    private val llmExecutor: LLMExecutor,
    private val agentFactory: AgentFactory
) {
    @PostMapping("/chat")
    suspend fun chat(@RequestBody request: ChatRequest): String {
        val messages = llmExecutor.execute(
            prompt {
                system("Ты — помощник")
                user(request.message)
            }
        )
        return messages.joinToString("\n") { it.content }
    }
    
    @PostMapping("/agent")
    suspend fun agent(@RequestBody request: ChatRequest): String {
        val agent = agentFactory.create("support-agent")
        val result = agent.run(request.message)
        return result.content
    }
}
```

---

## Model Context Protocol (MCP)

### Интеграция с MCP-серверами

```kotlin
import ai.koog.agents.mcp.*

// Настройка MCP-клиента
val mcpClient = MCPClient(
    serverUrl = "https://your-mcp-server.com/sse",
    transport = SSETransport()
)

// Подключение к MCP-серверу
mcpClient.connect()

// Получение доступных инструментов
val tools = mcpClient.listTools()

// Использование MCP-инструментов в агенте
val agent = AIAgent(
    executor = executor,
    tools = tools,
    systemPrompt = "Ты — агент с доступом к MCP-инструментам"
)
```

### Использование MCP в Ktor

```kotlin
import ai.koog.ktor.*

install(Koog) {
    agentConfig {
        mcp {
            sse("https://your-mcp-server.com/sse")
        }
    }
}
```

---

## Расширенные возможности

### Компрессия истории

```kotlin
import ai.koog.agents.features.*

val agent = AIAgent(
    executor = executor,
    features = listOf(
        HistoryCompressionFeature(
            maxTokens = 4000,
            compressionStrategy = SummarizationStrategy()
        )
    )
)
```

### Сохранение состояния агента

```kotlin
import ai.koog.agents.features.*

val agent = AIAgent(
    executor = executor,
    features = listOf(
        AgentPersistenceFeature(
            storage = FileStorage("/path/to/snapshots")
        )
    )
)

// Сохранение состояния
val snapshot = agent.saveSnapshot()

// Восстановление состояния
agent.restoreSnapshot(snapshot)
```

### Структурированный вывод

```kotlin
import ai.koog.prompt.structure.*
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val name: String,
    val age: Int,
    val email: String
)

val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))

val result = executor.execute(
    prompt {
        system("Извлеки информацию о пользователе из текста")
        user("Меня зовут Иван, мне 25 лет, email: ivan@example.com")
    },
    structuredOutput = UserInfo.serializer()
)

val userInfo: UserInfo = result.content
```

### Streaming API

```kotlin
import ai.koog.prompt.streaming.*

val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))

executor.executeStreaming(
    prompt {
        system("Ты — помощник")
        user("Расскажи о Kotlin")
    }
).collect { chunk ->
    // Обработка каждого чанка в реальном времени
    print(chunk.content)
}
```

### Embeddings и векторное хранилище

```kotlin
import ai.koog.embeddings.*
import ai.koog.vector.storage.*

// Создание embeddings
val embeddingExecutor = simpleOpenAIEmbeddingExecutor(System.getenv("OPENAI_API_KEY"))
val embedding = embeddingExecutor.embed("Текст для векторизации")

// Векторное хранилище
val storage = InMemoryVectorStorage()

// Сохранение документа
storage.add(
    id = "doc1",
    content = "Содержимое документа",
    embedding = embedding
)

// Поиск похожих документов
val results = storage.search(embedding, topK = 5)
```

### Трассировка с OpenTelemetry

```kotlin
import ai.koog.agents.features.*
import ai.koog.opentelemetry.*

val agent = AIAgent(
    executor = executor,
    features = listOf(
        OpenTelemetryFeature(
            exporter = LangfuseExporter(
                apiKey = System.getenv("LANGFUSE_API_KEY"),
                publicKey = System.getenv("LANGFUSE_PUBLIC_KEY")
            )
        )
    )
)
```

---

## Тестирование

### Мокирование LLM-ответов

```kotlin
import ai.koog.testing.*

val mockExecutor = MockLLMExecutor()
mockExecutor.mockResponse("Привет!") { "Привет! Как дела?" }

val agent = AIAgent(
    executor = mockExecutor,
    systemPrompt = "Ты — помощник"
)

val result = agent.run("Привет!")
assertEquals("Привет! Как дела?", result.content)
```

### Тестирование инструментов

```kotlin
import ai.koog.testing.*

val mockToolRegistry = MockToolRegistry()
mockToolRegistry.mockTool("get_weather") { "20°C, солнечно" }

val agent = AIAgent(
    executor = executor,
    tools = mockToolRegistry.tools
)

val result = agent.run("Какая погода?")
```

### Тестирование стратегий

```kotlin
import ai.koog.testing.*

val strategy = strategy {
    node("start") { /* ... */ }
    node("end") { /* ... */ }
    edge("start", "end")
}

val testEnvironment = AgentTestEnvironment()
testEnvironment.executeStrategy(strategy, "input")

// Проверка выполнения узлов
assertTrue(testEnvironment.wasNodeExecuted("start"))
assertTrue(testEnvironment.wasNodeExecuted("end"))
```

---

## API Reference

### Основные классы

#### AIAgent

```kotlin
class AIAgent(
    val executor: PromptExecutor,
    val systemPrompt: String? = null,
    val tools: List<Tool> = emptyList(),
    val strategy: Strategy? = null,
    val features: List<AgentFeature> = emptyList(),
    val eventHandlers: List<EventHandler> = emptyList()
) {
    suspend fun run(input: String): AgentResult
    suspend fun runStreaming(input: String): Flow<String>
    fun saveSnapshot(): AgentSnapshot
    fun restoreSnapshot(snapshot: AgentSnapshot)
}
```

#### PromptExecutor

```kotlin
interface PromptExecutor {
    suspend fun execute(
        prompt: Prompt,
        model: LLMModel? = null
    ): PromptResult
    
    suspend fun executeStreaming(
        prompt: Prompt,
        model: LLMModel? = null
    ): Flow<PromptChunk>
}
```

#### Tool

```kotlin
interface Tool {
    val name: String
    val description: String
    val inputSchema: JsonObject
    
    suspend fun invoke(arguments: JsonElement): ToolResult
}
```

### Создание executor'ов

```kotlin
// OpenAI
val openAIExecutor = simpleOpenAIExecutor(apiKey)

// Anthropic
val anthropicExecutor = simpleAnthropicExecutor(apiKey)

// Google
val googleExecutor = simpleGoogleExecutor(apiKey)

// OpenRouter
val openRouterExecutor = simpleOpenRouterExecutor(apiKey)

// Ollama
val ollamaExecutor = simpleOllamaExecutor(baseUrl = "http://localhost:11434")
```

### Модели

```kotlin
// OpenAI
OpenAIModels.Chat.GPT4oMini
OpenAIModels.Chat.GPT4o
OpenAIModels.Chat.GPT4Turbo

// Anthropic
AnthropicModels.Claude3_5Sonnet
AnthropicModels.Claude3Opus

// Google
GoogleModels.Gemini2_5Pro
GoogleModels.Gemini1_5Pro

// OpenRouter
OpenRouterModels.OpenAIGPT4oMini
OpenRouterModels.AnthropicClaude3_5Sonnet
```

---

## Заключение

Koog предоставляет мощный и гибкий инструментарий для разработки AI-агентов на Kotlin. Благодаря типобезопасному DSL, интеграции с популярными фреймворками и поддержке различных LLM-провайдеров, Koog упрощает создание сложных AI-приложений.

### Полезные ссылки

- [Официальная документация](https://docs.koog.ai/)
- [GitHub репозиторий](https://github.com/JetBrains/koog)
- [Примеры использования](https://docs.koog.ai/examples/)

### Дополнительные ресурсы

- [Глоссарий терминов](https://docs.koog.ai/glossary/)
- [Ключевые особенности](https://docs.koog.ai/key-features/)
- [Поддерживаемые LLM-провайдеры](https://docs.koog.ai/llm-providers/)

---

## Дополнительные разделы

### Подграфы (Subgraphs)

Подграфы позволяют создавать переиспользуемые части стратегий.

```kotlin
import ai.koog.agents.strategies.*

// Создание подграфа
val processingSubgraph = subgraph("processing") {
    node("validate") { /* ... */ }
    node("transform") { /* ... */ }
    edge("validate", "transform")
}

// Использование в основной стратегии
val strategy = strategy {
    node("start") { /* ... */ }
    
    // Включение подграфа
    include(processingSubgraph)
    
    node("end") { /* ... */ }
    
    edge("start", "processing")
    edge("processing", "end")
}
```

### Передача данных между узлами

```kotlin
import ai.koog.agents.strategies.*

val strategy = strategy {
    node("start") {
        // Установка данных для передачи
        setData("input", userInput)
        setData("timestamp", System.currentTimeMillis())
    }
    
    node("process") {
        // Получение данных из предыдущего узла
        val input = getData<String>("input")
        val timestamp = getData<Long>("timestamp")
        
        // Обработка данных
        val result = processInput(input)
        
        // Сохранение результата
        setData("result", result)
    }
    
    node("end") {
        // Получение результата
        val result = getData<String>("result")
        return result
    }
    
    edge("start", "process")
    edge("process", "end")
}
```

### Пользовательские узлы

```kotlin
import ai.koog.agents.strategies.*

class CustomNode(
    private val processor: (String) -> String
) : Node {
    override suspend fun execute(context: NodeContext): NodeResult {
        val input = context.getData<String>("input")
        val result = processor(input)
        context.setData("result", result)
        return NodeResult.success(result)
    }
}

// Использование
val strategy = strategy {
    customNode("process", CustomNode { input ->
        input.uppercase()
    })
}
```

### Память агента (Memory)

```kotlin
import ai.koog.agents.features.*

val agent = AIAgent(
    executor = executor,
    features = listOf(
        MemoryFeature(
            storage = InMemoryStorage(),
            retrievalStrategy = SimilarityRetrievalStrategy()
        )
    )
)

// Сохранение в память
agent.memory.save("user_preference", "Любит кофе")

// Извлечение из памяти
val preference = agent.memory.retrieve("user_preference")
```

### Ранжированное хранилище документов

```kotlin
import ai.koog.rag.*

val documentStorage = RankedDocumentStorage(
    embeddingExecutor = embeddingExecutor,
    vectorStorage = vectorStorage
)

// Добавление документов
documentStorage.add(
    id = "doc1",
    content = "Содержимое документа 1",
    metadata = mapOf("category" to "tech", "author" to "John")
)

documentStorage.add(
    id = "doc2",
    content = "Содержимое документа 2",
    metadata = mapOf("category" to "science", "author" to "Jane")
)

// Поиск с ранжированием
val results = documentStorage.search(
    query = "технологии",
    topK = 5,
    filters = mapOf("category" to "tech")
)
```

### Модерация контента

```kotlin
import ai.koog.content.moderation.*

val agent = AIAgent(
    executor = executor,
    features = listOf(
        ContentModerationFeature(
            moderationService = OpenAI moderationService,
            onModerationFailure = { content, reason ->
                // Обработка неподходящего контента
                logger.warn("Content moderated: $reason")
                throw ContentModerationException(reason)
            }
        )
    )
)
```

### Управление сессиями LLM

```kotlin
import ai.koog.llm.sessions.*

// Создание сессии
val session = LLMSession(
    executor = executor,
    model = OpenAIModels.Chat.GPT4oMini
)

// Добавление сообщений
session.addSystemMessage("Ты — помощник")
session.addUserMessage("Привет!")
session.addAssistantMessage("Привет! Как дела?")

// Получение истории
val history = session.getHistory()

// Очистка истории
session.clearHistory()

// Установка максимального размера истории
session.maxHistorySize = 10
```

### Ручное управление историей

```kotlin
import ai.koog.agents.history.*

val agent = AIAgent(
    executor = executor,
    historyManager = ManualHistoryManager()
)

// Добавление сообщения в историю
agent.history.addUserMessage("Вопрос 1")
agent.history.addAssistantMessage("Ответ 1")

// Получение истории
val history = agent.history.getMessages()

// Удаление последнего сообщения
agent.history.removeLast()

// Очистка истории
agent.history.clear()
```

### Экспортер Weave

```kotlin
import ai.koog.opentelemetry.*

val agent = AIAgent(
    executor = executor,
    features = listOf(
        OpenTelemetryFeature(
            exporter = WeaveExporter(
                apiKey = System.getenv("WEAVE_API_KEY")
            )
        )
    )
)
```

### Примеры использования

#### Пример 1: Простой чат-бот

```kotlin
import ai.koog.agents.core.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.model.OpenAIModels

fun main() = runBlocking {
    val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
    
    val agent = AIAgent(
        executor = executor,
        systemPrompt = "Ты — дружелюбный чат-бот. Отвечай кратко и по делу.",
        llmModel = OpenAIModels.Chat.GPT4oMini
    )
    
    while (true) {
        print("Вы: ")
        val input = readLine() ?: break
        
        if (input == "exit") break
        
        val result = agent.run(input)
        println("Бот: ${result.content}")
    }
}
```

#### Пример 2: Агент с инструментами

```kotlin
import ai.koog.agents.core.AIAgent
import ai.koog.agents.tools.*
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

@Tool(
    name = "calculate",
    description = "Выполняет математические вычисления"
)
fun calculate(expression: String): String {
    return try {
        val result = evaluateExpression(expression)
        result.toString()
    } catch (e: Exception) {
        "Ошибка: ${e.message}"
    }
}

fun main() = runBlocking {
    val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
    
    val tools = listOf(
        annotationBasedTool(::calculate)
    )
    
    val agent = AIAgent(
        executor = executor,
        systemPrompt = "Ты — помощник с доступом к калькулятору.",
        tools = tools
    )
    
    val result = agent.run("Сколько будет 25 * 4 + 10?")
    println(result.content)
}
```

#### Пример 3: Агент с RAG

```kotlin
import ai.koog.agents.core.AIAgent
import ai.koog.rag.*
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

fun main() = runBlocking {
    val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
    val embeddingExecutor = simpleOpenAIEmbeddingExecutor(System.getenv("OPENAI_API_KEY"))
    
    // Создание RAG-системы
    val rag = RAGSystem(
        embeddingExecutor = embeddingExecutor,
        documentStorage = RankedDocumentStorage(
            embeddingExecutor = embeddingExecutor,
            vectorStorage = InMemoryVectorStorage()
        )
    )
    
    // Добавление документов
    rag.addDocument("doc1", "Kotlin — это язык программирования...")
    rag.addDocument("doc2", "Корутины в Kotlin позволяют...")
    
    val agent = AIAgent(
        executor = executor,
        systemPrompt = "Ты — помощник по Kotlin. Используй предоставленную документацию.",
        features = listOf(
            RAGFeature(rag)
        )
    )
    
    val result = agent.run("Что такое корутины?")
    println(result.content)
}
```

---

## Часто задаваемые вопросы (FAQ)

### Как выбрать подходящий тип агента?

- **Базовый агент**: Для простых задач с одним вводом и одним выводом
- **Функциональный агент**: Когда нужна полная контроль над логикой обработки
- **Агент с комплексными рабочими процессами**: Для сложных многоэтапных задач

### Как оптимизировать использование токенов?

Используйте компрессию истории:

```kotlin
val agent = AIAgent(
    executor = executor,
    features = listOf(
        HistoryCompressionFeature(
            maxTokens = 4000,
            compressionStrategy = SummarizationStrategy()
        )
    )
)
```

### Как обрабатывать ошибки?

```kotlin
val agent = AIAgent(
    executor = executor,
    eventHandlers = listOf(
        onError { event ->
            logger.error("Ошибка в агенте: ${event.error}")
            // Обработка ошибки
        }
    )
)
```

### Как тестировать агентов?

Используйте мокирование:

```kotlin
val mockExecutor = MockLLMExecutor()
mockExecutor.mockResponse("вход") { "выход" }

val agent = AIAgent(executor = mockExecutor)
val result = agent.run("вход")
assertEquals("выход", result.content)
```

---

**Версия документации**: 0.5.4  
**Дата обновления**: 2024-12-04  
**Источник**: [Официальная документация Koog](https://docs.koog.ai/)

