# Лучшие практики работы с базой знаний

## Индексация документов

### Подготовка документов

1. **Очистка текста**: Удаляй лишние пробелы, форматирование
2. **Структурирование**: Используй заголовки, списки для лучшей разбивки
3. **Метаданные**: Сохраняй информацию об источнике, дате, авторе

### Параметры индексации

**Рекомендуемые значения:**

- Размер чанка: **800 токенов** (баланс точности и количества)
- Перекрытие: **100 токенов** (достаточно для сохранения контекста)
- Минимальный размер чанка: **200 токенов** (не индексировать слишком маленькие фрагменты)

### Инкрементальная индексация

Не переиндексируй весь документ при обновлении:

```kotlin
fun indexIncremental(document: Document) {
    // 1. Проверить, был ли документ уже проиндексирован
    val existing = repository.getDocument(document.id)
    
    if (existing != null) {
        // 2. Сравнить хеши для определения изменений
        if (existing.contentHash != document.contentHash) {
            // 3. Удалить старые чанки
            repository.deleteChunks(document.id)
            // 4. Индексировать заново
            indexer.index(document)
        }
    } else {
        // 5. Индексировать новый документ
        indexer.index(document)
    }
}
```

## Хранение эмбеддингов

### Формат хранения

**Для начала:** JSON строка (легче отлаживать)

```kotlin
val embeddingJson = Json.encodeToString(embedding)
repository.saveChunk(chunk.copy(embedding = embeddingJson))
```

**Для production:** BLOB (меньше места, быстрее)

```kotlin
val embeddingBytes = embedding.toByteArray()
repository.saveChunk(chunk.copy(embeddingBytes = embeddingBytes))
```

### Сжатие

Для больших баз знаний можно использовать сжатие:

```kotlin
fun compressEmbedding(embedding: List<Float>): ByteArray {
    // Использовать сжатие (например, gzip)
    val json = Json.encodeToString(embedding)
    return json.encodeToByteArray().gzip()
}
```

## Поиск

### Оптимизация запросов

1. **Кэширование**: Кэшируй результаты частых запросов
2. **Батчинг**: Обрабатывай несколько запросов параллельно
3. **Индексы**: Используй индексы в БД для быстрого поиска

### Пороги релевантности

**Рекомендуемые значения:**

- Минимальный порог: **0.5** (показывать только релевантные результаты)
- Высокий порог: **0.8** (только очень релевантные результаты)
- Средний порог: **0.6** (баланс между количеством и качеством)

### Обработка пустых результатов

```kotlin
fun searchWithFallback(query: String): List<SearchResult> {
    val results = searchService.search(query, limit = 10)
    
    if (results.isEmpty() || results.first().similarity < 0.3f) {
        // Попробовать более широкий поиск
        return searchService.search(query, limit = 20)
            .filter { it.similarity >= 0.3f }
    }
    
    return results.filter { it.similarity >= 0.5f }
}
```

## Мониторинг и анализ

### Метрики качества

Отслеживай следующие метрики:

1. **Среднее сходство**: Средний score результатов поиска
2. **Покрытие**: Процент запросов, для которых найдены результаты
3. **Время поиска**: Время выполнения поиска
4. **Размер базы знаний**: Количество документов и чанков

### Логирование

```kotlin
logger.info("Search query: $query")
logger.info("Found ${results.size} results")
logger.info("Top result similarity: ${results.first().similarity}")
logger.info("Search time: ${duration}ms")
```

## Безопасность

### Валидация входных данных

```kotlin
fun validateQuery(query: String): Boolean {
    // Проверка длины
    if (query.length < 3 || query.length > 500) {
        return false
    }
    
    // Проверка на вредоносный контент
    if (containsMaliciousContent(query)) {
        return false
    }
    
    return true
}
```

### Ограничение доступа

```kotlin
fun searchWithAuth(query: String, userId: String): List<SearchResult> {
    // Проверка прав доступа
    if (!hasAccess(userId)) {
        throw UnauthorizedException()
    }
    
    // Фильтрация по доступным документам
    val availableDocs = getAvailableDocuments(userId)
    return searchService.search(query)
        .filter { it.documentId in availableDocs }
}
```

## Масштабирование

### Для малых баз (до 1000 чанков)

Простой поиск по всем записям достаточен:

```kotlin
val results = allChunks
    .map { cosineSimilarity(queryEmbedding, it.embedding) to it }
    .sortedByDescending { it.first }
    .take(limit)
```

### Для средних баз (1000-10000 чанков)

Используй индексы и оптимизацию:

```kotlin
// Создать индекс по document_id для быстрого поиска
CREATE INDEX idx_chunks_document ON document_chunks(document_id);

// Кэшировать часто используемые эмбеддинги
val cachedEmbeddings = cache.getOrPut(documentId) {
    repository.getChunks(documentId).map { it.embedding }
}
```

### Для больших баз (10000+ чанков)

Используй векторные БД:

- **FAISS**: Для локального использования
- **Qdrant**: Для распределённых систем
- **Pinecone**: Для облачных решений

## Обслуживание базы знаний

### Регулярная очистка

```kotlin
fun cleanupDatabase() {
    // Удалить старые документы
    repository.deleteOldDocuments(olderThan = 90.days)
    
    // Удалить дубликаты
    repository.removeDuplicates()
    
    // Оптимизировать БД
    repository.vacuum()
}
```

### Резервное копирование

```kotlin
fun backupDatabase() {
    val backupPath = "backups/knowledge_base_${System.currentTimeMillis()}.db"
    File(databasePath).copyTo(File(backupPath))
}
```

## Заключение

Следуя лучшим практикам, можно создать эффективную и масштабируемую систему базы знаний. Помни про оптимизацию, мониторинг и безопасность.


