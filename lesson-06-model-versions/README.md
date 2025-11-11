# Lesson 06: Версии моделей

## Описание

Этот урок посвящён сравнению нескольких версий LLM, доступных через Hugging Face Inference API. Мы запускаем один и тот же вопрос на нескольких моделях, анализируем ответы, время отклика, стоимость по токенам и формируем рекомендации, в каких сценариях стоит выбирать каждую модель.

## Цели урока

- Построить конфигурацию каталога моделей в YAML и научиться подгружать её через Ktor + DI.
- Реализовать агента, который запускает вопрос на выбранных моделях, собирает метаданные и вычисляет стоимость.
- Сформировать сводку различий в ответах и подготовить ссылки на модели в Hugging Face.
- Создать фронтенд, позволяющий добавлять/удалять модели, запускать сравнения и просматривать историю запусков.

## Стек и архитектура

- **Backend**: Kotlin, Ktor, Clean Architecture (domain/data/presentation), kotlinx.serialization.
- **Frontend**: HTML, CSS, Vanilla JS (без фреймворков, поддержка истории и модальных окон).
- **Конфигурация**: YAML (`config/ai.yaml`, `config/ai.yaml.example`, `config/models.yaml`).
- **Интеграции**: Hugging Face Inference API, авторизация через `HUGGINGFACE_API_KEY`.

## Структура проекта

```
lesson-06-model-versions/
├── config/
│   ├── ai.yaml                # Базовые параметры API и system prompt
│   ├── ai.yaml.example        # Пример файла с дефолтными значениями
│   └── models.yaml            # Каталог моделей, дефолтный вопрос и id моделей по умолчанию
├── server/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/com/prike/
│       ├── Config.kt                         # Загрузка .env, ai.yaml и models.yaml
│       ├── config/
│       │   ├── AIConfig.kt
│       │   └── ModelComparisonLessonConfig.kt
│       ├── data/
│       │   ├── client/OpenAIClient.kt        # Клиент для HF API c поддержкой override параметров
│       │   └── repository/AIRepositoryImpl.kt
│       ├── domain/
│       │   ├── agent/ModelComparisonAgent.kt
│       │   ├── entity/LLMCompletionResult.kt
│       │   └── repository/AIRepository.kt
│       ├── presentation/
│       │   ├── controller/ModelComparisonController.kt
│       │   └── dto/ModelComparisonDtos.kt
│       └── Main.kt                           # CORS, routing, DI
├── client/
│   ├── index.html             # UI выбора моделей и отображения результатов
│   ├── style.css              # Градиентная тема, карточки моделей и история
│   └── app.js                 # Логика работы каталога, запуск сравнения, история
└── README.md                  # Этот файл
```

## Конфигурация

### `.env`

В корне урока создайте `.env` (см. `example.env`) и задайте:

```
HUGGINGFACE_API_KEY=hf_xxx
SERVER_HOST=0.0.0.0 # опционально
SERVER_PORT=8080   # опционально
```

### `config/ai.yaml`

- `apiUrl` — базовый URL для inference (`https://api-inference.huggingface.co/v1/chat/completions`).
- `model`, `temperature`, `maxTokens` — могут быть `null`, чтобы взять значения из конкретной модели.
- `systemPrompt` — общий промпт (опционально).

### `config/models.yaml`

- `lesson.defaultQuestion` — встроенный вопрос для первого запуска.
- `lesson.defaultModelIds` — идентификаторы моделей, используемые по умолчанию.
- `models[]` — карточки каталога с полями:
  - `id`, `displayName`, `endpoint`, `huggingFaceUrl`.
  - `pricePer1kTokensUsd` (опционально, для расчёта стоимости).
  - `defaultParams` — объект с дефолтными параметрами (temperature, max_tokens, top_p и т.д.).

### Каталог моделей по умолчанию

- **Llama 3.1 8B Instruct (Meta)** — сбалансированная англоязычная модель среднего размера. Хорошо держит длинный контекст, даёт структурированные ответы и стабильно отрабатывает аналитические запросы.
- **Mistral 7B Instruct v0.3** — компактная и быстрая модель, оптимизированная под inference. Выдаёт лаконичные ответы, но при этом уверена в базовых рассуждениях и коде.
- **Falcon 7B Instruct** — экономичная модель с более консервативной генерацией. Подходит, когда важна низкая стоимость и предсказуемые ответы без лишней креативности.
- **GLM-4.6 (zai-org)** — двуязычная модель семейства GLM, обученная на китайско-английском корпусе. Показывает сильные результаты в задачах рассуждений и математике, добавляет азиатский фокус и альтернативный стиль формулировок.
- **Qwen3 Coder 30B (Unsloth)** — специализированная кодовая модель. Сильна в генерации и рефакторинге программ, аккуратно следует инструкциям и хорошо обрабатывает многошаговые задачи с большим контекстом.
- **Aquif 3.5 Max 42B (mlx-community)** — крупная смесь моделей 3.5 поколения. Делает подробные аналитические ответы, обладает высокой устойчивостью к длинным промптам и даёт богатые пояснения.

## API

### `GET /api/models`

Возвращает дефолтный вопрос, дефолтные идентификаторы и каталог моделей.

```json
{
  "defaultQuestion": "...",
  "defaultModelIds": ["model-a", "model-b"],
  "models": [
    {
      "id": "...",
      "displayName": "...",
      "endpoint": "https://...",
      "huggingFaceUrl": "https://huggingface.co/...",
      "pricePer1kTokensUsd": 0.12,
      "defaultParams": {
        "temperature": 0.6,
        "max_tokens": 600
      }
    }
  ]
}
```

### `POST /api/models/compare`

Запускает сравнение и возвращает ответы по моделям.

Запрос:

```json
{
  "question": "опциональный текст",
  "modelIds": ["model-a", "model-b", "model-c"]
}
```

Ответ:

```json
{
  "defaultQuestion": "...",
  "defaultModelIds": ["model-a", "model-b", "model-c"],
  "question": "...",
  "modelResults": [
    {
      "modelId": "model-a",
      "displayName": "Model A",
      "huggingFaceUrl": "https://huggingface.co/model-a",
      "answer": "...",
      "meta": {
        "durationMs": 1320,
        "promptTokens": 120,
        "completionTokens": 150,
        "totalTokens": 270,
        "costUsd": 0.03
      }
    }
  ],
  "comparisonSummary": "Сводка по различиям",
  "modelLinks": [
    { "modelId": "model-a", "huggingFaceUrl": "https://huggingface.co/model-a" }
  ]
}
```

- Если `modelIds` не переданы, агент автоматически выбирает три модели (начало, середина, конец списка каталога).
- В метаданных стоимость рассчитывается на основе `pricePer1kTokensUsd` и `totalTokens` (если оба значения доступны).

## Шаги запуска

1. Установите JDK 17+.
2. Скопируйте `example.env` → `.env`, пропишите `HUGGINGFACE_API_KEY`.
3. При необходимости обновите `config/ai.yaml` и `config/models.yaml`.
4. Запустите сервер:
   ```bash
   cd lesson-06-model-versions/server
   ./gradlew run
   ```
5. Откройте фронтенд:
   ```
   open ../client/index.html
   ```
6. В UI выберите модели из каталога, при необходимости отредактируйте вопрос и нажмите «Запустить сравнение».

## Проверка

- Убедитесь, что бэкенд стартует без ошибок (`./gradlew run`).
- Проверьте, что `GET /api/models` возвращает каталог из `models.yaml`.
- Запустите сравнение минимум с тремя моделями и подтвердите, что UI показывает карточки, сводку и ссылки.
- Проверьте историю запусков: новые попытки появляются в списке и восстанавливают результаты.

## Ограничения и заметки

- Стоимость считается только если модель вернула `totalTokens` и в конфиге указана цена.
- Hugging Face может возвращать rate limit / 429 — обработка ошибок выводится на UI.
- Для наглядности рекомендуется сравнивать модели из разных семейств (например, Llama, Mistral, Falcon).
- Токены и время передаются «как есть» из ответа API; у разных моделей метаданные могут отличаться.

## Полезные сценарии

- Сравнение кратких аналитических ответов.
- Генерация рекомендаций: увидеть, какая модель даёт более структурированные ответы.
- Измерение стоимости: оценить бюджет при выборе модели для продакшена.

Урок демонстрирует, как построить сравнение нескольких моделей с учётом стоимости, времени и качества, и формирует основу для систем выбора LLM в продакшене.
