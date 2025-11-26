# 🔥 День 17. Реранкинг и фильтрация

Улучшенный RAG поверх урока 16: добавляем второй этап после поиска (фильтр / реранкер), настраиваем порог отсечения нерелевантных чанков и сравниваем качество ответов **без фильтра** и **с фильтром/реранкингом**.

## Демонстрация

> 📹 Видео появится после записи (формат: Видео + Код).

## 📋 Описание

В этом уроке мы:
- Копируем базовую систему RAG из урока 16 (индексация, поиск, OpenRouter).
- Добавляем **RelevanceFilter** (порог по косинусному сходству).
- Добавляем **LLM Reranker** (второй запрос к OpenRouter для переупорядочивания чанков).
- Настраиваем гибридную стратегию `threshold → reranker`.
- Обновляем UI, чтобы выбирать стратегию и порог на лету.
- Сравниваем ответы «без фильтра» и «с фильтром/реранкингом».

## 🎯 Пайплайн

```
Вопрос
  ↓
Embedding через Ollama (nomic-embed-text)
  ↓
Поиск top-K чанков (KnowledgeBaseSearchService)
  ↓
Фильтр/реранкер (threshold / OpenRouter)
  ↓
PromptBuilder → контекст
  ↓
Запрос к OpenRouter (LLMService)
  ↓
ComparisonService: без фильтра vs с фильтром
```

## 🏗️ Архитектура

```
lesson-17-rag-reranking/
├── config/server.yaml
├── data/knowledge_base.db
├── documents/...
├── server/
│   └── src/main/kotlin/com/prike/
│       ├── config/Config.kt
│       ├── data/
│       │   ├── client/OpenAIClient.kt
│       │   ├── dto/...
│       │   └── repository/KnowledgeBaseRepository.kt
│       ├── domain/
│       │   ├── indexing/ (TextChunker, VectorNormalizer, ...)
│       │   ├── service/
│       │   │   ├── DocumentIndexer.kt
│       │   │   ├── KnowledgeBaseSearchService.kt
│       │   │   ├── LLMService.kt
│       │   │   ├── PromptBuilder.kt
│       │   │   ├── RAGService.kt
│       │   │   ├── RelevanceFilter.kt
│       │   │   ├── RerankerService.kt
│       │   │   └── ComparisonService.kt
│       │   └── model/ (TextChunk, RetrievedChunk, RAGRequest, ...)
│       └── presentation/
│           ├── controller/
│           │   ├── IndexingController.kt
│           │   ├── SearchController.kt
│           │   ├── LLMController.kt
│           │   └── RAGController.kt
│           └── dto/ (DocumentDtos, RAGDtos, FilterConfigDto, ...)
└── client/
    ├── index.html          # ссылки на все страницы
    ├── indexing.html(+js)  # индексация документов (без изменений)
    ├── search.html(+js)    # поиск по базе знаний (без изменений)
    ├── rag-compare.html    # сравнение режимов и настройка фильтра
    ├── rag-compare.js
    └── style.css
```

## 🚀 Быстрый старт

```bash
cd lesson-17-rag-reranking

# 1. Убедитесь, что база знаний заполнена (можно переиспользовать из урока 16)
# 2. Запустите сервер
cd server
./gradlew run

# 3. Откройте UI
http://localhost:8080
```

Доступные страницы:
- `/indexing.html` — индексация
- `/search.html` — поиск
- `/rag-compare.html` — новый экран сравнения RAG режимов

## 📡 API

### Индексация (unchanged)
- `POST /api/indexing/index`
- `GET /api/indexing/documents`
- `POST /api/search/query`

### Работа с фильтром
- `GET /api/rag/filter/config` — получить текущие настройки
- `POST /api/rag/filter/config` — обновить `strategy`, `minSimilarity`, `keepTop`

### RAG
```http
POST /api/rag/query
{
  "question": "Как создать MCP сервер?",
  "strategy": "reranker",        // none | threshold | reranker | hybrid
  "topK": 5,
  "minSimilarity": 0.5
}
→
{
  "question": "...",
  "answer": "...",
  "contextChunks": [...],
  "filterStats": {
      "retrieved": 5,
      "kept": 3,
      "dropped": [
          {"chunkId": "...", "reason": "similarity < 0.6"}
      ]
  },
  "rerankInsights": [
      {"chunkId": "...", "score": 0.84, "reason": "..."}
  ],
  "tokensUsed": 1350
}
```

```http
POST /api/rag/compare
{
  "question": "Как создать MCP сервер?",
  "strategy": "hybrid",
  "topK": 5
}
→
{
  "question": "...",
  "baseline": {...},   # без фильтра
  "filtered": {...},   # выбранная стратегия
  "metrics": {
     "baselineChunks": 5,
     "filteredChunks": 3,
     "avgSimilarityBefore": 0.52,
     "avgSimilarityAfter": 0.74,
     "tokensSaved": 210
  }
}
```

## ⚙️ Конфигурация (`config/server.yaml`)

```yaml
server:
  port: 8080

ollama:
  baseUrl: "http://localhost:11434"
  model: "nomic-embed-text"
  timeout: 120000

knowledgeBase:
  databasePath: "data/knowledge_base.db"

indexing:
  chunkSize: 400
  overlapSize: 50
  documentsPath: "documents"

ai:
  provider: "openrouter"
  apiKey: "${OPENAI_API_KEY}"
  model: "gpt-4o-mini"
  temperature: 0.7
  maxTokens: 2000

rag:
  retrieval:
    topK: 5
    minSimilarity: 0.4
  filter:
    enabled: true
    strategy: "hybrid"     # none | threshold | reranker | hybrid
    threshold:
      minSimilarity: 0.6
      keepTop: 3
    reranker:
      model: "gpt-4o-mini"
      maxChunks: 6
      systemPrompt: "Ты — reranker..."
```

`.env` (в корне репозитория):
```
OPENAI_API_KEY=your_openrouter_api_key
```

## 💡 Сценарии использования

1. **Точная фильтрация**: задайте порог 0.7, стратегия `threshold`. Ответ будет коротким и конкретным.
2. **Гибрид**: `strategy=hybrid`, порог 0.55 + reranker. Идеально для вопросов с большим количеством похожих чанков.
3. **Диагностика**: переключитесь на `strategy=none`, увидите все 5 чанков и поймёте, что фильтр удалил.

## 🧪 Тестирование

- `RelevanceFilterTest` — проверка порогов и режима `keepTop`.
- `RerankerPromptBuilderTest` — правильность промпта.
- `RAGServiceIntegrationTest` — fallback, если фильтр удалил все чанки.
- Manual: на странице `/rag-compare.html` сравнить ответы для разных стратегий.

## 🎓 Цели урока

- Понять, зачем нужен второй этап обработки выдачи.
- Научиться настраивать пороги релевантности.
- Освоить LLM-реранкинг и его интеграцию в RAG.
- Визуализировать и объяснять процесс фильтрации пользователю.

## 🔗 Связанные уроки

- День 16 — первый RAG-запрос (без фильтрации).
- Этот урок — следующий шаг: фильтрация/реранкинг.

## 📚 Дополнительные материалы

- [RAG: Retrieval-Augmented Generation](https://arxiv.org/abs/2005.11401)
- [OpenRouter API](https://openrouter.ai/docs)
- [Cohere Rerank Guide](https://docs.cohere.com/docs/rerank)
- [Lessons learned from RAG pipelines](https://www.pinecone.io/learn/rag/)


