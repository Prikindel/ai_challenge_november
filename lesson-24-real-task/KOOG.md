# Koog Framework - Документация для проекта

## 📚 Быстрый старт

### Зависимость (добавлена)

```kotlin
dependencies {
    implementation("ai.koog:koog-agents:0.5.4")
}

repositories {
    mavenCentral()
}
```

### Quickstart пример

```kotlin
fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
    
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(apiKey),
        systemPrompt = "You are a helpful assistant.",
        llmModel = OpenAIModels.Chat.GPT4o
    )
    
    val result = agent.run("Hello!")
    println(result)
}
```

## 🎯 Основные концепции

### AIAgent

Основной класс для создания агентов:
- `executor` - prompt executor для LLM
- `systemPrompt` - системный промпт
- `llmModel` - модель LLM
- `agent.run()` - запуск агента

### Инструменты (@Tool)

```kotlin
@Tool("Описание инструмента")
fun myTool(@Param("param") param: String): ReturnType {
    // Реализация
}
```

## 🔗 Ссылки

- **Документация:** https://docs.koog.ai/
- **GitHub:** https://github.com/JetBrains/koog
- **Версия:** 0.5.4

## 📝 Для проекта

Используем Koog для:
- Создания агента ReviewsAnalyzerAgent
- Инструментов через @Tool аннотации
- Вызова LLM через agent.run()

---

*Подробная документация: см. `docs/koog-research/KOOG_RESEARCH.md`*
