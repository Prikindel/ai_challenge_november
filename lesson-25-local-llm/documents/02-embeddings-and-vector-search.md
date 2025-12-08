# Эмбеддинги и векторный поиск

## Что такое эмбеддинги?

Эмбеддинги (embeddings) — это числовые представления текста в виде векторов. Похожие по смыслу тексты имеют похожие эмбеддинги, что позволяет искать информацию по смыслу, а не по ключевым словам.

## Генерация эмбеддингов

### Использование Ollama

Ollama — это инструмент для запуска LLM моделей локально. Для генерации эмбеддингов можно использовать модель `nomic-embed-text`:

```bash
# Установка Ollama
curl -fsSL https://ollama.ai/install.sh | sh

# Загрузка модели
ollama pull nomic-embed-text
```

### API запрос

```kotlin
val response = httpClient.post("http://localhost:11434/api/embeddings") {
    contentType(ContentType.Application.Json)
    setBody(json {
        "model" to "nomic-embed-text"
        "prompt" to "текст для эмбеддинга"
    })
}

val embedding = response.body<EmbeddingResponse>().embedding
// Результат: массив из 768 чисел
```

## Размерность эмбеддингов

Разные модели возвращают эмбеддинги разной размерности:

- **nomic-embed-text**: 768 чисел
- **text-embedding-ada-002** (OpenAI): 1536 чисел
- **all-MiniLM-L6-v2**: 384 числа

Чем больше размерность, тем точнее представление, но больше места для хранения.

## Нормализация векторов

### Зачем нужна нормализация?

Нормализация приводит векторы к определённому диапазону, что улучшает точность поиска и сравнимость результатов.

### Методы нормализации

#### 1. Деление на размерность

```kotlin
fun normalizeTo01(embedding: List<Float>): List<Float> {
    val dimension = embedding.size  // 768 для nomic-embed-text
    return embedding.map { it / dimension }
}
```

#### 2. L2 нормализация

```kotlin
fun normalizeL2(embedding: List<Float>): List<Float> {
    val magnitude = sqrt(embedding.sumOf { it * it }.toDouble())
    return embedding.map { (it / magnitude).toFloat() }
}
```

## Векторный поиск

### Косинусное сходство

Косинусное сходство (cosine similarity) — это мера схожести между двумя векторами:

```kotlin
fun cosineSimilarity(vector1: List<Float>, vector2: List<Float>): Float {
    require(vector1.size == vector2.size)
    
    val dotProduct = vector1.zip(vector2).sumOf { (a, b) -> a * b }
    val magnitude1 = sqrt(vector1.sumOf { it * it }.toDouble())
    val magnitude2 = sqrt(vector2.sumOf { it * it }.toDouble())
    
    return (dotProduct / (magnitude1 * magnitude2)).toFloat()
}
```

### Диапазон значений

- **1.0**: Векторы полностью совпадают по направлению
- **0.0**: Векторы перпендикулярны (не связаны)
- **-1.0**: Векторы противоположны

### Поиск в базе знаний

```kotlin
fun search(query: String, limit: Int = 10): List<SearchResult> {
    // 1. Генерация эмбеддинга для запроса
    val queryEmbedding = generateEmbedding(query)
    val normalizedQuery = normalizeTo01(queryEmbedding)
    
    // 2. Получение всех чанков из БД
    val allChunks = repository.getAllChunks()
    
    // 3. Вычисление сходства
    val results = allChunks.map { chunk ->
        val similarity = cosineSimilarity(normalizedQuery, chunk.embedding)
        SearchResult(chunk, similarity)
    }
    
    // 4. Сортировка и возврат топ-N
    return results
        .sortedByDescending { it.similarity }
        .take(limit)
}
```

## Хранение эмбеддингов

### SQLite с JSON

```sql
CREATE TABLE document_chunks (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    embedding TEXT NOT NULL  -- JSON массив чисел
);
```

### SQLite с BLOB

```sql
CREATE TABLE document_chunks (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    embedding BLOB NOT NULL  -- Бинарные данные
);
```

## Оптимизация поиска

### Для малых баз (до 1000 чанков)

Простой поиск по всем записям достаточен:

```kotlin
val results = allChunks
    .map { cosineSimilarity(queryEmbedding, it.embedding) to it }
    .sortedByDescending { it.first }
    .take(limit)
```

### Для больших баз (1000+ чанков)

Используй векторные БД:

- **FAISS** (Facebook AI Similarity Search)
- **Qdrant** (векторная БД)
- **Pinecone** (облачная векторная БД)

## Практические советы

1. **Всегда нормализуй векторы** перед сравнением
2. **Используй косинусное сходство** для семантического поиска
3. **Кэшируй эмбеддинги** — не генерируй повторно для одинаковых текстов
4. **Оптимизируй поиск** для больших баз знаний

## Заключение

Эмбеддинги и векторный поиск — это мощный инструмент для семантического поиска. Используй их для создания интеллектуальных систем поиска по базе знаний.

