# Разбивка документов на чанки

## Зачем нужны чанки?

Длинные документы нужно разбивать на фрагменты (чанки) по нескольким причинам:

1. **Ограничение контекста LLM**: LLM не может обработать очень длинный текст за раз
2. **Точность поиска**: Легче найти конкретный фрагмент, чем весь документ
3. **Эффективность**: Можно индексировать и искать только нужные части

## Размер чанка

### Рекомендуемые размеры

- **Маленький (200-400 токенов)**: Высокая точность, много чанков
- **Средний (500-1000 токенов)**: Баланс между точностью и количеством (рекомендуется)
- **Большой (1500+ токенов)**: Меньше чанков, но ниже точность

### Выбор размера

Размер чанка зависит от:
- Типа документов (техническая документация vs статьи)
- Требований к точности поиска
- Ограничений LLM модели

## Перекрытия между чанками

### Что такое перекрытие?

Перекрытие (overlap) — это общая часть между соседними чанками. Соседние чанки имеют общий фрагмент текста.

### Зачем нужны перекрытия?

1. **Сохранение контекста**: Информация на границе чанков не теряется
2. **Улучшение поиска**: Если запрос попадает на границу, он найдётся в обоих чанках
3. **Связность**: Чанки лучше связаны между собой

### Размер перекрытия

- **Маленькое (20-50 токенов)**: Минимальное перекрытие, меньше дублирования
- **Среднее (50-100 токенов)**: Баланс между контекстом и дублированием (рекомендуется)
- **Большое (100-200 токенов)**: Больше контекста, но больше дублирования

### Пример перекрытия

```
Чанк 1 (токены 0-800):
"Как создать MCP сервер? Сначала нужно установить SDK..."

Чанк 2 (токены 700-1500):
"...установить SDK. Затем создать класс Server..."

Перекрытие: токены 700-800
"установить SDK"
```

## Реализация разбивки

### Подсчёт токенов

Простой способ — приблизительный подсчёт:

```kotlin
fun countTokens(text: String): Int {
    // Приблизительно: 1 токен ≈ 4 символа
    return text.length / 4
}
```

Более точный способ — использовать библиотеку для подсчёта токенов.

### Алгоритм разбивки

```kotlin
class TextChunker(
    private val chunkSize: Int = 800,
    private val overlapSize: Int = 100
) {
    fun chunk(text: String): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        var startIndex = 0
        
        while (startIndex < text.length) {
            val endIndex = minOf(
                startIndex + chunkSize * 4,  // примерный размер в символах
                text.length
            )
            
            val chunkText = text.substring(startIndex, endIndex)
            val tokenCount = countTokens(chunkText)
            
            chunks.add(TextChunk(
                content = chunkText,
                startIndex = startIndex,
                endIndex = endIndex,
                tokenCount = tokenCount
            ))
            
            // Следующий чанк начинается с перекрытием
            startIndex = endIndex - overlapSize * 4
        }
        
        return chunks
    }
}
```

## Сохранение структуры Markdown

### Проблемы при разбивке

При разбивке Markdown нужно учитывать:

1. **Не разрывать код-блоки**: Код должен оставаться целым
2. **Сохранять заголовки**: Заголовки должны быть в начале чанка
3. **Сохранять списки**: Списки не должны разрываться посередине

### Решение

```kotlin
fun chunkMarkdown(text: String): List<TextChunk> {
    // 1. Найти границы структурных элементов
    val codeBlocks = findCodeBlocks(text)
    val headers = findHeaders(text)
    
    // 2. Разбить на чанки с учётом границ
    val chunks = mutableListOf<TextChunk>()
    var currentIndex = 0
    
    while (currentIndex < text.length) {
        // Найти следующую безопасную границу
        val nextBoundary = findNextSafeBoundary(
            currentIndex,
            codeBlocks,
            headers
        )
        
        val chunkText = text.substring(currentIndex, nextBoundary)
        chunks.add(createChunk(chunkText, currentIndex))
        
        // Следующий чанк с перекрытием
        currentIndex = nextBoundary - overlapSize * 4
    }
    
    return chunks
}
```

## Тестирование

### Проверка покрытия

Все чанки должны покрывать весь документ:

```kotlin
fun testCoverage(chunks: List<TextChunk>, originalText: String) {
    val coveredLength = chunks.sumOf { it.endIndex - it.startIndex }
    val overlapLength = calculateOverlap(chunks)
    val actualLength = coveredLength - overlapLength
    
    assert(actualLength >= originalText.length * 0.95) {
        "Документ покрыт не полностью"
    }
}
```

### Проверка перекрытий

Соседние чанки должны иметь перекрытие:

```kotlin
fun testOverlaps(chunks: List<TextChunk>) {
    for (i in 0 until chunks.size - 1) {
        val current = chunks[i]
        val next = chunks[i + 1]
        
        val overlap = current.endIndex - next.startIndex
        assert(overlap >= overlapSize * 4 * 0.8) {
            "Перекрытие слишком маленькое"
        }
    }
}
```

## Практические советы

1. **Используй размер чанка 500-1000 токенов** для большинства задач
2. **Перекрытие 50-100 токенов** обеспечивает хороший баланс
3. **Сохраняй структуру Markdown** при разбивке
4. **Тестируй покрытие** — все чанки должны покрывать документ
5. **Логируй процесс** для отладки

## Заключение

Правильная разбивка документов на чанки — это основа эффективного поиска по базе знаний. Используй перекрытия, сохраняй структуру и тестируй результаты.

