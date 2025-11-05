# Lesson 02: Структурированные ответы от LLM

## Описание

AI-агент, который получает структурированные JSON ответы от LLM. Агент задает формат ответа через system prompt и API параметр `response_format`, затем парсит JSON ответ и возвращает структурированные данные.

## Задание

- Научиться задавать формат результата для возвращения
- Задать агенту формат возвращения в prompt
- Привести пример формата возврата
- Результат: Ответ от LLM можно распарсить

## Реализация

1. **JSON формат ответа**: LLM возвращает ответ строго в формате JSON
2. **System prompt**: Требует от LLM возвращать ответ только в JSON формате
3. **API параметр**: Используется `response_format: {"type": "json_object"}` для моделей, поддерживающих JSON mode
4. **Парсинг**: JSON ответ парсится в структурированный объект `ChatStructuredResponse`

## Структура JSON ответа

```json
{
  "message": "текст ответа от AI",
  "tone": "friendly|professional|casual",
  "tags": ["тег1", "тег2"],
  "sentiment": "positive|neutral|negative"
}
```

## API Endpoints

### Обычный ответ (текст)
```bash
POST /chat
{
  "message": "Привет!"
}
```

### Структурированный ответ (JSON)
```bash
POST /chat/structured
{
  "message": "Привет!"
}
```

Ответ:
```json
{
  "structuredResponse": {
    "message": "Привет! Как дела?",
    "tone": "friendly",
    "tags": ["приветствие"],
    "sentiment": "positive"
  }
}
```

## Технологии

- **Backend:** Kotlin + Ktor
- **Frontend:** HTML + JavaScript (Vanilla)
- **AI API:** OpenAI или OpenRouter (настраивается)
- **JSON:** Kotlinx Serialization для парсинга JSON

## Быстрый старт

1. Установите Java 17+

2. Настройте API ключ в `.env` файле:
   ```bash
   OPENAI_API_KEY=your_api_key_here
   ```

3. Настройте AI в `config/ai.yaml`:
   - Включите `useJsonFormat: true`
   - Настройте `systemPrompt` с требованием JSON формата
   - Убедитесь, что модель поддерживает JSON mode (например, `gpt-3.5-turbo-1106`)

4. Запустите сервер:
   ```bash
   cd server
   ./gradlew run
   ```

5. Откройте браузер: `http://localhost:8080`

## Конфигурация

### config/ai.yaml

```yaml
ai:
  useJsonFormat: true  # Включить JSON режим
  systemPrompt: |
    Ты полезный AI ассистент. 
    ВСЕГДА отвечай ТОЛЬКО в формате JSON...
```

См. `config/ai.yaml.example` для полного примера конфигурации.

## Структура проекта

```
lesson-02-structured-response/
├── config/
│   ├── ai.yaml              # Конфигурация AI (с useJsonFormat: true)
│   └── ai.yaml.example      # Пример конфигурации
├── server/                  # Kotlin + Ktor сервер
│   └── src/main/kotlin/
│       ├── data/
│       │   ├── client/      # OpenAIClient (поддержка response_format)
│       │   └── dto/         # ChatStructuredResponse
│       ├── repository/      # Парсинг JSON ответа
│       └── usecase/         # processMessageStructured()
└── client/                 # Веб-клиент
```

## Особенности реализации

1. **OpenAIRequest**: Добавлен параметр `responseFormat` для JSON режима
2. **OpenAIClient**: Автоматически добавляет `response_format: {"type": "json_object"}` при `useJsonFormat: true`
3. **AIRepositoryImpl**: Метод `getStructuredAIResponse()` парсит JSON из ответа LLM
4. **ChatUseCase**: Метод `processMessageStructured()` возвращает структурированный ответ
5. **ServerController**: Endpoint `/chat/structured` для получения структурированных ответов

## Важные замечания

- **JSON режим**: Не все модели поддерживают JSON mode. Используйте модели, которые его поддерживают (например, `gpt-3.5-turbo-1106`, `gpt-4-1106-preview`)
- **System prompt**: Критически важен для получения корректного JSON. LLM должен четко понимать формат ответа
- **Парсинг**: JSON ответ парсится с помощью Kotlinx Serialization, что позволяет валидировать структуру ответа
