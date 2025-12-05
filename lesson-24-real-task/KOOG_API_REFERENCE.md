# Koog Framework - API Reference и примеры использования

## 📚 Общая информация

**Koog** — фреймворк от JetBrains для создания AI-агентов на Kotlin
- **Зависимость:** `ai.koog:koog-agents:0.5.4`
- **Документация:** https://docs.koog.ai/

## 🎯 Основные концепции

### 1. Типы агентов

#### Функциональные агенты (Functional Agents)
- Легковесные агенты с пользовательской логикой на чистом Kotlin
- Идеальны для простых задач с инструментами

#### Базовые агенты (Basic Agents)
- Обрабатывают один ввод и предоставляют ответ
- Простая структура

#### Агенты с комплексными рабочими процессами
- Для сложных задач с графовыми стратегиями

### 2. Инструменты (Tools)

Koog поддерживает создание инструментов через аннотации `@Tool`:

```kotlin
@Tool("Описание инструмента для LLM")
fun myTool(@Param("paramName") param: String): ReturnType {
    // Реализация инструмента
}
```

### 3. Промпты (Prompts)

Создание и выполнение промптов с LLM:

```kotlin
// Пример структуры (требует уточнения из документации)
val prompt = Prompt.create {
    // Настройка промпта
}
val response = promptExecutor.execute(prompt)
```

## 🔧 Примеры использования (на основе документации)

### Создание функционального агента

```kotlin
// Примерная структура (требует уточнения)
val agent = FunctionalAgent.create {
    systemPrompt = "Вы — полезный ассистент."
    tools = listOf(
        // Регистрация инструментов с @Tool аннотациями
    )
}
```

### Создание агента с инструментами

```kotlin
class ReviewsAnalyzerAgent {
    
    @Tool("Сбор отзывов за период с сервера Company Mobile Stores")
    fun fetchReviews(
        @Param("fromDate") fromDate: String,
        @Param("toDate") toDate: String
    ): List<Review> {
        // Реализация сбора отзывов
        return reviews
    }
    
    @Tool("Классификация отзывов через LLM")
    fun classifyReviews(
        @Param("reviews") reviews: List<Review>
    ): List<ReviewAnalysis> {
        // Использование LLM для классификации
        // Вызов через Koog prompt executor
        return analyses
    }
}
```

### Вызов LLM через Koog

Koog предоставляет способ вызова LLM через промпты:

```kotlin
// Примерная структура (требует уточнения из документации)
val promptExecutor = simpleOpenAIExecutor(apiKey)

val prompt = Prompt.create {
    system = "Ты анализируешь отзывы"
    user = "Проанализируй следующие отзывы: ${reviews}"
}

val response = promptExecutor.execute(prompt)
```

### Конфигурация LLM провайдеров

```kotlin
// OpenAI
val promptExecutor = simpleOpenAIExecutor(
    apiKey = config.koog.apiKey,
    model = OpenAIModels.Chat.GPT4oMini
)

// Или через конфигурационный файл
// application.conf или config/server.yaml
```

## 🏗️ Структура для проекта

### ReviewsAnalyzerAgent

```kotlin
class ReviewsAnalyzerAgent(
    private val promptExecutor: PromptExecutor,
    private val apiClient: ReviewsApiClient,
    private val repository: ReviewsRepository,
    private val config: ReviewsConfig
) {
    
    // Инструмент: Сбор отзывов
    @Tool("Сбор отзывов за период с сервера Company Mobile Stores")
    fun fetchReviews(
        @Param("fromDate") fromDate: String,
        @Param("toDate") toDate: String
    ): List<Review> {
        return apiClient.fetchReviews(
            store = config.api.store,
            packageId = config.api.packageId,
            fromDate = fromDate,
            toDate = toDate
        )
    }
    
    // Инструмент: Классификация через LLM
    @Tool("Классификация отзывов через LLM")
    suspend fun classifyReviews(
        @Param("reviews") reviews: List<Review>
    ): List<ReviewAnalysis> {
        val prompt = createClassificationPrompt(reviews)
        val response = promptExecutor.execute(prompt)
        return parseAnalysisResponse(response)
    }
    
    // Инструмент: Сравнение недель
    @Tool("Сравнение недель через LLM")
    suspend fun compareWeeks(
        @Param("currentWeek") currentWeek: WeekStats,
        @Param("previousWeek") previousWeek: WeekStats
    ): WeekComparison {
        val prompt = createComparisonPrompt(currentWeek, previousWeek)
        val response = promptExecutor.execute(prompt)
        return parseComparisonResponse(response)
    }
    
    // Вызов LLM для анализа
    private suspend fun askLLM(prompt: String): String {
        val promptObj = Prompt.create {
            system = "Ты анализируешь отзывы приложения"
            user = prompt
        }
        return promptExecutor.execute(promptObj).content
    }
}
```

## 🔗 Полезные ссылки

- **Getting Started:** https://docs.koog.ai/getting-started/
- **Functional Agents:** https://docs.koog.ai/agent-types/functional-agents/
- **Annotation-based Tools:** https://docs.koog.ai/tools/annotation-based-tools/
- **Prompts:** https://docs.koog.ai/prompts/
- **Ktor Plugin:** https://docs.koog.ai/ktor-plugin/
- **Examples:** https://docs.koog.ai/examples/overview/

## ⚠️ Важные замечания

1. **Точный синтаксис API** нужно уточнить из документации или примеров
2. **Импорты** могут отличаться - нужно проверить реальные пакеты
3. **Асинхронность** - некоторые методы могут быть suspend функциями
4. **Конфигурация** может быть через файлы или программно

## 📝 Следующие шаги

1. ✅ Зависимость добавлена
2. 🔄 Найти конкретные примеры кода из GitHub или документации
3. 🔄 Уточнить точный синтаксис создания агентов
4. 🔄 Уточнить способ вызова LLM (метод `ask()` или через промпты)
5. 🔄 Изучить интеграцию с Ktor (если нужна)

## 💡 Рекомендации

Для реализации проекта рекомендуется:
1. Использовать функциональные агенты для простоты
2. Использовать аннотации `@Tool` для инструментов
3. Создать PromptExecutor для работы с LLM
4. Интегрировать через обычный Ktor (без плагина, если сложно)

