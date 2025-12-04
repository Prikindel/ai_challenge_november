# Урок 22: Support Assistant с Koog

## Обзор

Урок 22 полностью переделан на использование **Koog** - фреймворка от JetBrains для создания AI-агентов на Kotlin. Все запросы к LLM, инструменты и логика работы с AI теперь используют Koog.

## Архитектура с Koog

### Компоненты

1. **SupportAgentKoog** (`com.prike.domain.koog.SupportAgentKoog`)
   - Основной AI агент на базе Koog
   - Использует Koog tools для работы с CRM и RAG
   - Формирует промпты с инструкциями по использованию инструментов

2. **Koog Tools** (`com.prike.domain.koog.tools.*`)
   - **CRMTool.kt**: Инструменты для работы с CRM (get_user, get_ticket, create_ticket, add_ticket_message, get_user_tickets)
   - **RAGTool.kt**: Инструмент для поиска в документации поддержки
   - **BaseTool.kt**: Базовый класс для всех инструментов

3. **SupportService** (`com.prike.domain.service.SupportService`)
   - Обёртка над SupportAgentKoog
   - Упрощённый интерфейс для контроллеров

### Поток обработки запроса

```
1. Пользователь задаёт вопрос → SupportController
2. SupportController → SupportService.answerQuestion()
3. SupportService → SupportAgentKoog.answerQuestion()
4. SupportAgentKoog:
   - Формирует системный промпт с инструкциями
   - Создаёт пользовательское сообщение
   - Вызывает LLM через Koog (сейчас через HTTP, будет через Koog executor)
   - LLM автоматически использует tools (get_user, get_ticket, search_support_docs)
   - Обрабатывает ответ и извлекает источники
5. Возвращает SupportResponse с ответом, источниками и предложениями
```

## Koog Tools

### CRM Tools

- **get_user**: Получить информацию о пользователе по ID или email
- **get_ticket**: Получить информацию о тикете с историей сообщений
- **get_user_tickets**: Получить все тикеты пользователя
- **create_ticket**: Создать новый тикет
- **add_ticket_message**: Добавить сообщение в тикет

### RAG Tool

- **search_support_docs**: Поиск в документации поддержки (FAQ, troubleshooting, user guide, auth guide)

## Использование

### Задать вопрос поддержке

```bash
POST /api/support/ask
Content-Type: application/json

{
  "question": "Как восстановить пароль?",
  "userId": "user123",
  "ticketId": null
}
```

### Получить информацию о тикете

```bash
GET /api/support/ticket/{ticketId}
```

### Получить тикеты пользователя

```bash
GET /api/support/user/{userId}/tickets
```

### Создать тикет

```bash
POST /api/support/ticket
Content-Type: application/json

{
  "userId": "user123",
  "subject": "Проблема с авторизацией",
  "description": "Не могу войти в систему"
}
```

## Интеграция с Koog

### Текущая реализация

- ✅ Используются Koog tools для CRM и RAG
- ✅ Формируются промпты с инструкциями по использованию инструментов
- ✅ LLM автоматически вызывает нужные инструменты
- ⚠️ HTTP вызовы к OpenRouter (временное решение)

### Планируемые улучшения

1. **Полная интеграция с Koog prompt executor**:
   ```kotlin
   val executor = simpleOpenRouterExecutor(apiKey)
   val agent = AIAgent(
       executor = executor,
       systemPrompt = systemPrompt,
       tools = allTools,
       llmModel = OpenRouterModels.OpenAIGPT4oMini
   )
   ```

2. **Использование Koog Ktor plugin**:
   ```kotlin
   install(Koog) {
       llm {
           fallback {
               provider = "openrouter"
               model = "openai/gpt-4o-mini"
           }
       }
   }
   ```

3. **Добавление Koog features**:
   - HistoryCompressionFeature для оптимизации токенов
   - MemoryFeature для сохранения контекста
   - OpenTelemetryFeature для трассировки

## Документация

- [Полный гайд по Koog](./KOOG_GUIDE.md)
- [Заметки по интеграции](./KOOG_INTEGRATION_NOTES.md)
- [Официальная документация Koog](https://docs.koog.ai/)

## Отличия от предыдущей версии

### Удалено

- ❌ `SupportPromptBuilder` - заменён на встроенную логику в SupportAgentKoog
- ❌ Прямые вызовы к LLMService из SupportService
- ❌ Ручное формирование промптов с контекстом

### Добавлено

- ✅ Koog tools для CRM и RAG
- ✅ SupportAgentKoog с использованием Koog
- ✅ Автоматическое использование инструментов LLM
- ✅ Улучшенная структура промптов

### Улучшено

- ✅ Более чистая архитектура с разделением ответственности
- ✅ Использование типобезопасного Kotlin DSL (через tools)
- ✅ Готовность к полной интеграции с Koog API

## Запуск

```bash
cd lesson-22-support-assistant/server
./gradlew run
```

Сервер запустится на `http://localhost:8080`

## Тестирование

1. Откройте `http://localhost:8080/support.html`
2. Задайте вопрос поддержке
3. Проверьте, что агент использует инструменты для получения контекста
4. Проверьте, что ответ содержит информацию из документации

## Следующие шаги

1. Изучить правильные импорты для Koog API
2. Заменить HTTP вызовы на Koog prompt executor
3. Добавить Koog features для улучшения функциональности
4. Интегрировать Koog Ktor plugin

