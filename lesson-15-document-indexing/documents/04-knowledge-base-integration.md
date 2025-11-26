# Интеграция базы знаний с LLM агентом

## Зачем нужна база знаний?

База знаний позволяет LLM агенту:

1. **Использовать актуальную информацию** из документов
2. **Отвечать точно** на основе реальных данных
3. **Масштабироваться** — добавлять новые документы без переобучения

## Архитектура интеграции

### Поток работы

```
1. Пользователь задаёт вопрос
   ↓
2. Агент ищет в базе знаний
   ↓
3. Получает релевантные чанки
   ↓
4. Добавляет чанки в контекст LLM
   ↓
5. LLM генерирует ответ на основе найденной информации
```

## Реализация поиска

### Сервис поиска

```kotlin
class KnowledgeBaseSearchService(
    private val embeddingService: EmbeddingService,
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val similarityCalculator: CosineSimilarityCalculator
) {
    suspend fun search(
        query: String,
        limit: Int = 5
    ): List<SearchResult> {
        // 1. Генерация эмбеддинга для запроса
        val queryEmbedding = embeddingService.generateEmbedding(query)
        val normalizedQuery = vectorNormalizer.normalizeTo01(queryEmbedding)
        
        // 2. Поиск в базе знаний
        val allChunks = knowledgeBaseRepository.getAllChunks()
        
        // 3. Вычисление сходства
        val results = allChunks.map { chunk ->
            val similarity = similarityCalculator.calculateSimilarity(
                normalizedQuery,
                chunk.embedding
            )
            SearchResult(
                chunkId = chunk.id,
                documentId = chunk.documentId,
                content = chunk.content,
                similarity = similarity
            )
        }
        
        // 4. Сортировка и возврат топ-N
        return results
            .sortedByDescending { it.similarity }
            .take(limit)
    }
}
```

## Интеграция с агентом

### Добавление контекста в промпт

```kotlin
class AgentWithKnowledgeBase(
    private val searchService: KnowledgeBaseSearchService,
    private val aiRepository: AIRepository
) {
    suspend fun processUserMessage(userMessage: String): String {
        // 1. Поиск в базе знаний
        val searchResults = searchService.search(userMessage, limit = 5)
        
        // 2. Формирование контекста
        val context = buildString {
            append("Контекст из базы знаний:\n\n")
            searchResults.forEachIndexed { index, result ->
                append("[Чанк ${index + 1}]\n")
                append("${result.content}\n\n")
            }
        }
        
        // 3. Формирование промпта для LLM
        val systemPrompt = """
            Ты — интеллектуальный ассистент с доступом к базе знаний.
            
            Используй предоставленный контекст для ответа на вопрос пользователя.
            Если информация в контексте не отвечает на вопрос, скажи об этом.
        """.trimIndent()
        
        val messages = listOf(
            MessageDto(role = "system", content = systemPrompt),
            MessageDto(role = "user", content = context),
            MessageDto(role = "user", content = userMessage)
        )
        
        // 4. Генерация ответа
        val response = aiRepository.getMessageWithHistory(messages)
        return response.message
    }
}
```

## Обработка результатов поиска

### Фильтрация по порогу релевантности

```kotlin
fun filterByThreshold(
    results: List<SearchResult>,
    threshold: Float = 0.5f
): List<SearchResult> {
    return results.filter { it.similarity >= threshold }
}
```

### Дедупликация

Если один и тот же документ встречается несколько раз:

```kotlin
fun deduplicateByDocument(
    results: List<SearchResult>
): List<SearchResult> {
    val seenDocuments = mutableSetOf<String>()
    return results.filter { result ->
        seenDocuments.add(result.documentId)
    }
}
```

## Оптимизация производительности

### Кэширование эмбеддингов запросов

```kotlin
class CachedSearchService(
    private val searchService: KnowledgeBaseSearchService
) {
    private val cache = mutableMapOf<String, List<SearchResult>>()
    
    suspend fun search(query: String, limit: Int): List<SearchResult> {
        return cache.getOrPut(query) {
            searchService.search(query, limit)
        }
    }
}
```

### Асинхронная обработка

```kotlin
suspend fun processMultipleQueries(
    queries: List<String>
): List<List<SearchResult>> {
    return queries.map { query ->
        async { searchService.search(query) }
    }.awaitAll()
}
```

## Обработка ошибок

### Если база знаний пуста

```kotlin
if (allChunks.isEmpty()) {
    return "База знаний пуста. Сначала проиндексируйте документы."
}
```

### Если ничего не найдено

```kotlin
val filteredResults = results.filter { it.similarity >= 0.5f }
if (filteredResults.isEmpty()) {
    return "По вашему запросу ничего не найдено в базе знаний."
}
```

## Расширенные возможности

### Гибридный поиск

Комбинация семантического поиска и поиска по ключевым словам:

```kotlin
fun hybridSearch(
    query: String,
    semanticWeight: Float = 0.7f,
    keywordWeight: Float = 0.3f
): List<SearchResult> {
    val semanticResults = semanticSearch(query)
    val keywordResults = keywordSearch(query)
    
    return mergeResults(
        semanticResults,
        keywordResults,
        semanticWeight,
        keywordWeight
    )
}
```

### Рерайтинг результатов

Использование LLM для улучшения ранжирования:

```kotlin
suspend fun rerankResults(
    query: String,
    results: List<SearchResult>
): List<SearchResult> {
    // Использовать LLM для переоценки релевантности
    val reranked = llm.rerank(query, results)
    return reranked.sortedByDescending { it.score }
}
```

## Практические советы

1. **Используй топ-5 результатов** для большинства запросов
2. **Фильтруй по порогу релевантности** (0.5-0.6)
3. **Добавляй метаданные** (источник, дата) в контекст
4. **Логируй запросы** для анализа качества поиска
5. **Мониторь производительность** — поиск должен быть быстрым

## Заключение

Интеграция базы знаний с LLM агентом — это мощный способ создания интеллектуальных систем. Используй семантический поиск, обрабатывай результаты и оптимизируй производительность.


