# Lesson 02: Энциклопедия животных со структурированными ответами

## Описание

AI-агент энциклопедия о животных, который получает структурированные JSON ответы от LLM. Агент задает формат ответа через system prompt и API параметр `response_format`, валидирует соответствие темы запроса, и возвращает структурированные данные о животных или ошибку валидации темы.

## Демонстрация

[Тык сюда](https://disk.yandex.ru/i/J9rm8ifCdKIaXA)

## Задание

- Научиться задавать формат результата для возвращения
- Задать агенту формат возвращения в prompt
- Привести пример формата возврата
- Результат: Ответ от LLM можно распарсить

## Реализация

1. **JSON формат ответа**: LLM возвращает ответ строго в формате JSON
2. **System prompt**: Требует от LLM возвращать ответ только в JSON формате с валидацией темы
3. **API параметр**: Используется `response_format: {"type": "json_object"}` для моделей, поддерживающих JSON mode
4. **Парсинг**: JSON ответ парсится в структурированный объект `AnimalEncyclopediaResponse`
5. **Валидация темы**: Если запрос не относится к теме животных, возвращается ошибка `TOPIC_MISMATCH`
6. **Конфигурация темы**: Тема хранится в отдельном файле `config/topic.yaml`

## Структура JSON ответа

### Успешный ответ (информация о животном)
```json
{
  "type": "success",
  "data": {
    "name": "Лев",
    "description": "Краткое описание животного",
    "diet": "Чем питается животное",
    "lifespan": "Сколько живут",
    "habitat": "Где обитают"
  }
}
```

### Ответ с ошибкой валидации темы
```json
{
  "type": "error",
  "error": {
    "errorCode": "TOPIC_MISMATCH",
    "message": "Этот вопрос не относится к теме животных..."
  }
}
```

## API Endpoints

### Структурированный ответ (JSON) - энциклопедия животных
```bash
POST /chat
{
  "message": "Расскажи о львах"
}
```

**Успешный ответ:**
```json
{
  "response": {
    "type": "success",
    "data": {
      "name": "Лев",
      "description": "Лев - крупное хищное млекопитающее...",
      "diet": "Питаются в основном мясом...",
      "lifespan": "В дикой природе живут 10-14 лет...",
      "habitat": "Обитают в саваннах Африки..."
    }
  }
}
```

**Ответ с ошибкой валидации:**
```json
{
  "response": {
    "type": "error",
    "error": {
      "errorCode": "TOPIC_MISMATCH",
      "message": "Этот вопрос не относится к теме животных..."
    }
  }
}
```

## Технологии

- **Backend:** Kotlin + Ktor
- **Frontend:** HTML + JavaScript (Vanilla)
- **AI API:** OpenAI или OpenRouter (настраивается)
- **JSON:** Kotlinx Serialization для парсинга JSON
- **Конфигурация:** YAML для настроек темы

## Быстрый старт

1. Установите Java 17+

2. Настройте API ключ в `.env` файле:
   ```bash
   OPENAI_API_KEY=your_api_key_here
   ```

3. Настройте AI в `config/ai.yaml`:
   - Включите `useJsonFormat: true`
   - Убедитесь, что модель поддерживает JSON mode (например, `gpt-3.5-turbo-1106`)

4. Настройте тему в `config/topic.yaml`:
   - Тема по умолчанию: "Виды животных"
   - Можно изменить описание и промпт валидации

5. Запустите сервер:
   ```bash
   cd server
   ./gradlew run
   ```

6. Откройте браузер: `http://localhost:8080`

## Конфигурация

### config/ai.yaml

```yaml
ai:
  useJsonFormat: true  # Включить JSON режим
  systemPrompt: null   # Автоматически формируется из topic.yaml
```

### config/topic.yaml

```yaml
topic:
  name: "Виды животных"
  description: "Энциклопедия о различных видах животных..."
  validationPrompt: |
    Пользователь должен задавать вопросы ТОЛЬКО о животных...
```

См. `config/topic.yaml.example` для полного примера конфигурации.

## Структура проекта

```
lesson-02-structured-response/
├── config/
│   ├── ai.yaml              # Конфигурация AI (с useJsonFormat: true)
│   ├── ai.yaml.example      # Пример конфигурации AI
│   ├── topic.yaml           # Конфигурация темы
│   └── topic.yaml.example   # Пример конфигурации темы
├── server/                  # Kotlin + Ktor сервер
│   └── src/main/kotlin/com/prike/
│       ├── config/
│       │   ├── AIConfig.kt          # Конфигурация AI
│       │   ├── TopicConfig.kt       # Конфигурация темы
│       │   └── PromptBuilder.kt     # Построитель системного промпта
│       ├── data/
│       │   ├── client/
│       │   │   └── OpenAIClient.kt # HTTP клиент с поддержкой JSON mode
│       │   ├── dto/
│       │   │   ├── AnimalInfo.kt
│       │   │   ├── AnimalEncyclopediaResponse.kt
│       │   │   └── TopicValidationError.kt
│       │   └── repository/
│       │       └── AIRepositoryImpl.kt # Парсинг JSON ответа
│       ├── domain/
│       │   ├── repository/
│       │   │   └── AIRepository.kt
│       │   └── usecase/
│       │       └── ChatUseCase.kt
│       └── presentation/
│           ├── controller/
│           │   ├── ServerController.kt
│           │   └── ClientController.kt
│           └── dto/
│               └── AnimalEncyclopediaResponseDto.kt
└── client/                 # Веб-клиент
    ├── index.html          # UI с предупреждением о теме
    ├── app.js              # Обработка структурированных ответов
    └── style.css           # Стили для карточки животного и ошибки
```

## Особенности реализации

1. **TopicConfig**: Конфигурация темы загружается из отдельного файла `topic.yaml`
2. **PromptBuilder**: Автоматически формирует system prompt на основе конфигурации темы
3. **OpenAIClient**: Использует `response_format: {"type": "json_object"}` для принудительного JSON режима
4. **AnimalEncyclopediaResponse**: Sealed class с `@JsonClassDiscriminator` для полиморфной десериализации
5. **AIRepositoryImpl**: Парсит JSON ответ с fallback на ошибку валидации темы
6. **TopicValidationErrorCode**: Константа для кода ошибки `TOPIC_MISMATCH`
7. **Логирование**: JSON запросы и ответы логируются в формате OkHttp (`--> POST`, `<-- 200`)

## Frontend

### Отображение карточки животного
- Название животного (заголовок)
- Описание
- Питание
- Продолжительность жизни
- Среда обитания

### Отображение ошибки валидации
- Специальное выделение ошибки
- Сообщение об ошибке с объяснением

### Предупреждение о теме
- Информационное сообщение о теме вверху интерфейса
- Напоминание пользователю о том, какие вопросы можно задавать

## Важные замечания

- **JSON режим**: Используйте модели, которые поддерживают JSON mode (например, `gpt-3.5-turbo-1106`, `gpt-4-1106-preview`, `meta-llama/llama-3.1-8b-instruct`)
- **System prompt**: Критически важен для получения корректного JSON. LLM должен четко понимать формат ответа и правила валидации темы
- **Парсинг**: JSON ответ парсится с помощью Kotlinx Serialization с полиморфной десериализацией через `@JsonClassDiscriminator`
- **Конфигурация темы**: Тема хранится в отдельном файле `config/topic.yaml`, что позволяет легко изменять тему без изменения кода
- **Fallback**: Если ответ пустой или не парсится, возвращается ошибка валидации темы вместо технической ошибки

## Примеры запросов

**Успешные запросы:**
- "Расскажи о львах"
- "Что едят пингвины?"
- "Где обитают слоны?"
- "Сколько живут дельфины?"

**Запросы с ошибкой валидации:**
- "Как приготовить борщ?"
- "Расскажи о программировании"
- "Что такое квантовая физика?"
