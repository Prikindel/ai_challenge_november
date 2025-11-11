# Lesson 06: Версии моделей

## Описание

Этот урок посвящён сравнению нескольких версий LLM, доступных через Hugging Face Inference API. Мы запускаем один и тот же вопрос на нескольких моделях, анализируем ответы, время отклика, стоимость по токенам и формируем рекомендации, в каких сценариях стоит выбирать каждую модель.

## Демонстрация

[Тыкай сюда](https://disk.yandex.ru/i/gXBx4mnrmknXaQ)

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

```
HUGGINGFACE_API_KEY=hf_xxx   # fine-grained токен с правом "Make calls to Inference Providers"
SERVER_HOST=0.0.0.0          # опционально
SERVER_PORT=8080             # опционально
```

### `config/ai.yaml`

- В файле можно оставить `apiUrl`, `model`, `temperature`, `maxTokens` равными `null` — фактические значения берём из `models.yaml`.
- `systemPrompt` (если нужно) будет передаваться всем моделям.

### `config/models.yaml`

Каталог настроен на Hugging Face Inference Router (`https://router.huggingface.co/v1/chat/completions`). Для перечисленных моделей дополнительный доступ обычно не требуется, достаточно fine-grained токена с правом *Make calls to Inference Providers*.

#### Текущий каталог

- **DeepSeek R1** — мощная модель reasoning-класса, выдаёт подробные цепочки рассуждений. Хороша для сложных аналитических запросов и «пошагового» решения задач.
- **GPT-OSS 120B** — крупный открытый аналог GPT; сильна в генерации длинных текстов, креативных сценариях, где нужны большие знания. Требует больше токенов и времени, но даёт насыщенные ответы.
- **Qwen2.5 7B Instruct** — сбалансированный 7B с китайско-английским фокусом. Отличный выбор для смешанных языков и прикладных задач (резюме, QA, помощь в коде).
- **Gemma 2 9B IT** — модель Google, заточенная на инструкции и диалог. Отвечает компактно и структурированно; хороший выбор, когда нужно кратко и по делу.
- **Llama 3.2 3B Instruct** — лёгкий baseline. Быстро отвечает, экономит токены, подходит для простых запросов и валидационных прогонов.
- **MiniMax M2** — универсальный чат-LLM от MiniMax. Балансирует между скоростью и качеством, часто выдаёт живые, человекоподобные ответы. Подходит для пользовательских ассистентов и быстрых консультаций.
- **Llama 3.2 1B MGSM8K (autoprogrammer)** — ультра-лёгкая модель (~1B параметров), удобно держать под рукой для быстрых проверок и задач с ограниченными ресурсами. Отличный baseline, когда важно минимальное время ответа и экономия токенов.

При необходимости можно расширить список другими моделями — добавьте новый элемент в `models.yaml`, указав `endpoint` Router и идентификатор модели.

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

1. Получите токен Hugging Face (fine-grained) и включите разрешение «Make calls to Inference Providers».
2. Прописать токен в `.env` (`HUGGINGFACE_API_KEY`).
3. Убедиться, что файл `config/models.yaml` содержит нужные модели и endpoint Router.
4. Запустить сервер урока:
   ```bash
   cd lesson-06-model-versions/server
   ./gradlew run
   ```
5. Открыть `lesson-06-model-versions/client/index.html` в браузере. Выбрать модели, задать вопрос и запустить сравнение.

> Если нужно добавить другие open-source модели из каталога Hugging Face, достаточно пополнить `models.yaml` записью с тем же endpoint и идентификатором модели.

## Проверка

- `GET /api/models` должен отдавать список моделей из Router.
- При выполнении `POST /api/models/compare` каждая выбранная модель отвечает через Router; в логах сервера виден запрос на `https://router.huggingface.co/v1/chat/completions`.
- Если какая-то модель временно недоступна, карточка в UI подсветится предупреждением.

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
