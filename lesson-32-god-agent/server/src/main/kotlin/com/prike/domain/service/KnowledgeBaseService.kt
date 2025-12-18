package com.prike.domain.service

import com.prike.data.repository.KnowledgeBaseChunksTable
import com.prike.domain.model.DocumentCategory
import com.prike.domain.model.DocumentChunk
import com.prike.domain.model.RetrievedChunk
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

/**
 * Сервис для работы с базой знаний (RAG система с категориями)
 */
class KnowledgeBaseService(
    private val embeddingService: EmbeddingService,
    private val database: org.jetbrains.exposed.sql.Database,
    private val basePath: String = "knowledge-base"
) {
    private val logger = LoggerFactory.getLogger(KnowledgeBaseService::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    companion object {
        private const val CHUNK_SIZE = 500 // Размер чанка в символах
        private const val CHUNK_OVERLAP = 50 // Перекрытие между чанками
        private const val MAX_FILE_SIZE_MB = 10 // Максимальный размер файла в МБ
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024
    }
    
    /**
     * Индексировать всю базу знаний
     */
    suspend fun indexKnowledgeBase(lessonRoot: File) {
        logger.info("Starting knowledge base indexing from: $basePath")
        
        val categories = DocumentCategory.values()
        var totalIndexed = 0
        
        categories.forEach { category ->
            val categoryPath = File(lessonRoot, "$basePath/${category.path}")
            if (categoryPath.exists() && categoryPath.isDirectory) {
                val indexed = indexCategory(category, categoryPath)
                totalIndexed += indexed
                logger.info("Indexed $indexed documents in category: ${category.displayName}")
            } else {
                logger.debug("Category directory not found: ${categoryPath.absolutePath}")
            }
        }
        
        logger.info("Knowledge base indexing completed. Total documents indexed: $totalIndexed")
    }
    
    /**
     * Индексировать категорию документов
     */
    suspend fun indexCategory(category: DocumentCategory, categoryPath: File): Int {
        logger.info("Indexing category: ${category.displayName} from ${categoryPath.absolutePath}")
        
        val markdownFiles = findMarkdownFiles(categoryPath)
        var indexedCount = 0
        
        markdownFiles.forEach { file ->
            try {
                // Проверяем размер файла перед обработкой
                val fileSize = file.length()
                if (fileSize > MAX_FILE_SIZE_BYTES) {
                    logger.warn("Skipping large file: ${file.absolutePath} (${fileSize / 1024 / 1024}MB)")
                    return@forEach
                }
                
                indexDocument(file, category, categoryPath)
                indexedCount++
                delay(100) // Небольшая задержка между документами
                
                // Периодически предлагаем сборщику мусора очистить память
                if (indexedCount % 10 == 0) {
                    System.gc()
                }
            } catch (e: OutOfMemoryError) {
                logger.error("OutOfMemoryError indexing document ${file.absolutePath}: ${e.message}. File size: ${file.length() / 1024 / 1024}MB")
                // Пытаемся освободить память
                System.gc()
                // Продолжаем со следующим файлом
            } catch (e: Exception) {
                logger.error("Failed to index document ${file.absolutePath}: ${e.message}", e)
            }
        }
        
        return indexedCount
    }
    
    /**
     * Индексировать отдельный документ
     */
    suspend fun indexDocument(
        file: File,
        category: DocumentCategory,
        baseDir: File
    ) {
        // Проверка размера файла
        val fileSize = file.length()
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            logger.warn("Document ${file.absolutePath} is too large (${fileSize / 1024 / 1024}MB), skipping. Max size: ${MAX_FILE_SIZE_MB}MB")
            return
        }
        
        val relativePath = baseDir.toPath().relativize(file.toPath()).toString()
        val documentId = UUID.randomUUID().toString()
        
        // Читаем файл с ограничением размера
        val content = try {
            file.readText(Charsets.UTF_8)
        } catch (e: OutOfMemoryError) {
            logger.error("OutOfMemoryError reading file ${file.absolutePath}: ${e.message}. File size: ${fileSize / 1024 / 1024}MB")
            return
        } catch (e: Exception) {
            logger.error("Error reading file ${file.absolutePath}: ${e.message}", e)
            return
        }
        
        if (content.isBlank()) {
            logger.warn("Document ${file.absolutePath} is empty, skipping")
            return
        }
        
        // Разбиваем документ на чанки
        val chunks = try {
            chunkText(content, documentId, relativePath, category)
        } catch (e: OutOfMemoryError) {
            logger.error("OutOfMemoryError chunking file ${file.absolutePath}: ${e.message}. Content length: ${content.length}")
            return
        }
        
        // Удаляем старые чанки для этого документа
        deleteChunksForDocument(documentId)
        
        // Индексируем каждый чанк с обработкой ошибок памяти
        chunks.forEachIndexed { index, chunk ->
            try {
                val embedding = embeddingService.generateEmbedding(chunk.content)
                
                transaction(database) {
                    val chunkId = UUID.randomUUID().toString()
                    KnowledgeBaseChunksTable.insert {
                        it[KnowledgeBaseChunksTable.id] = chunkId
                        it[KnowledgeBaseChunksTable.documentId] = chunk.documentId
                        it[KnowledgeBaseChunksTable.chunkIndex] = chunk.chunkIndex
                        it[KnowledgeBaseChunksTable.content] = chunk.content
                        it[KnowledgeBaseChunksTable.embedding] = json.encodeToString(embedding)
                        it[KnowledgeBaseChunksTable.category] = category.name
                        it[KnowledgeBaseChunksTable.sourcePath] = chunk.source
                        it[KnowledgeBaseChunksTable.startIndex] = chunk.startIndex
                        it[KnowledgeBaseChunksTable.endIndex] = chunk.endIndex
                        it[KnowledgeBaseChunksTable.indexedAt] = Instant.now().toString()
                    }
                }
                
                delay(50) // Небольшая задержка между чанками
                
                // Периодически предлагаем сборщику мусора очистить память
                if (index % 100 == 0) {
                    System.gc()
                }
            } catch (e: OutOfMemoryError) {
                logger.error("OutOfMemoryError indexing chunk $index of document ${file.absolutePath}: ${e.message}")
                // Пытаемся освободить память и пропускаем этот чанк
                System.gc()
                return@forEachIndexed
            } catch (e: Exception) {
                logger.error("Failed to index chunk $index of document ${file.absolutePath}: ${e.message}", e)
            }
        }
        
        logger.debug("Indexed document: ${file.absolutePath} (${chunks.size} chunks)")
    }
    
    /**
     * Поиск в категории
     */
    suspend fun searchInCategory(
        query: String,
        category: String? = null,
        limit: Int = 5
    ): List<RetrievedChunk> {
        if (query.isBlank()) {
            return emptyList()
        }
        
        logger.debug("Searching in knowledge base: query='$query', category=$category, limit=$limit")
        
        // Генерируем эмбеддинг для запроса
        val queryEmbedding = embeddingService.generateEmbedding(query)
        
            // Получаем чанки из БД с фильтрацией по категории
            val allChunks = transaction(database) {
                val query = if (category != null) {
                    val docCategory = DocumentCategory.fromName(category)
                    if (docCategory != null) {
                        KnowledgeBaseChunksTable.select {
                            KnowledgeBaseChunksTable.category eq docCategory.name
                        }
                    } else {
                        KnowledgeBaseChunksTable.selectAll()
                    }
                } else {
                    KnowledgeBaseChunksTable.selectAll()
                }
                
                query.map { row ->
                ChunkData(
                    id = row[KnowledgeBaseChunksTable.id],
                    documentId = row[KnowledgeBaseChunksTable.documentId],
                    chunkIndex = row[KnowledgeBaseChunksTable.chunkIndex],
                    content = row[KnowledgeBaseChunksTable.content],
                    embedding = json.decodeFromString<List<Float>>(
                        row[KnowledgeBaseChunksTable.embedding]
                    ),
                    category = DocumentCategory.valueOf(row[KnowledgeBaseChunksTable.category]),
                    source = row[KnowledgeBaseChunksTable.sourcePath]
                )
            }
        }
        
        if (allChunks.isEmpty()) {
            logger.warn("No chunks found in knowledge base")
            return emptyList()
        }
        
        logger.debug("Searching in ${allChunks.size} chunks")
        
        // Вычисляем сходство для каждого чанка
        val results = allChunks.map { chunk ->
            val similarity = calculateCosineSimilarity(queryEmbedding, chunk.embedding)
            
            RetrievedChunk(
                id = chunk.id,
                documentId = chunk.documentId,
                text = chunk.content,
                similarity = similarity,
                source = chunk.source,
                category = chunk.category,
                chunkIndex = chunk.chunkIndex
            )
        }
        
        // Сортируем по сходству и возвращаем топ-N
        return results
            .sortedByDescending { it.similarity }
            .take(limit)
    }
    
    /**
     * Разбить текст на чанки
     * Оптимизированная версия для больших файлов
     */
    private fun chunkText(
        text: String,
        documentId: String,
        source: String,
        category: DocumentCategory
    ): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()
        var startIndex = 0
        var chunkIndex = 0
        
        // Используем CharSequence для более эффективной работы с подстроками
        val textLength = text.length
        
        while (startIndex < textLength) {
            val endIndex = minOf(startIndex + CHUNK_SIZE, textLength)
            
            // Используем более эффективный способ извлечения подстроки
            // Создаем новую строку только при необходимости
            val chunkContent = if (startIndex == 0 && endIndex == textLength) {
                // Если весь текст помещается в один чанк, используем его напрямую
                text.trim()
            } else {
                // Для подстрок используем более эффективный метод
                text.substring(startIndex, endIndex).trim()
            }
            
            // Пропускаем пустые чанки
            if (chunkContent.isNotEmpty()) {
                chunks.add(
                    DocumentChunk(
                        id = "$documentId-chunk-$chunkIndex",
                        documentId = documentId,
                        chunkIndex = chunkIndex,
                        content = chunkContent,
                        embedding = emptyList(), // Будет заполнено при индексации
                        category = category,
                        source = source,
                        startIndex = startIndex,
                        endIndex = endIndex
                    )
                )
            }
            
            // Вычисляем следующий стартовый индекс с учетом перекрытия
            startIndex = if (endIndex >= textLength) {
                textLength // Достигли конца текста
            } else {
                maxOf(startIndex + 1, endIndex - CHUNK_OVERLAP) // Перекрытие, но не меньше чем +1
            }
            chunkIndex++
            
            // Защита от бесконечного цикла
            if (chunkIndex > 100000) {
                logger.error("Too many chunks generated for document $documentId, stopping")
                break
            }
        }
        
        return chunks
    }
    
    /**
     * Найти все markdown файлы в директории рекурсивно
     */
    private fun findMarkdownFiles(directory: File): List<File> {
        val files = mutableListOf<File>()
        
        if (!directory.exists() || !directory.isDirectory) {
            return files
        }
        
        directory.walkTopDown().forEach { file ->
            if (file.isFile && (file.extension == "md" || file.extension == "txt")) {
                files.add(file)
            }
        }
        
        return files
    }
    
    /**
     * Вычисляет косинусное сходство между двумя векторами
     */
    private fun calculateCosineSimilarity(vec1: List<Float>, vec2: List<Float>): Double {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vectors must have the same size")
        }
        
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        val denominator = Math.sqrt(norm1) * Math.sqrt(norm2)
        return if (denominator == 0.0) 0.0 else dotProduct / denominator
    }
    
    /**
     * Удалить все чанки для документа
     */
    private fun deleteChunksForDocument(documentId: String) {
        transaction(database) {
            KnowledgeBaseChunksTable.deleteWhere {
                KnowledgeBaseChunksTable.documentId eq documentId
            }
        }
    }
    
    /**
     * Получить статистику по базе знаний
     */
    fun getStatistics(): KnowledgeBaseStatistics {
        return transaction(database) {
            val allChunks = KnowledgeBaseChunksTable.selectAll().toList()
            val totalChunks = allChunks.size
            
            val chunksByCategory = allChunks
                .groupBy { it[KnowledgeBaseChunksTable.category] }
                .mapValues { it.value.size }
            
            KnowledgeBaseStatistics(
                totalChunks = totalChunks,
                chunksByCategory = chunksByCategory
            )
        }
    }
    
    /**
     * Данные чанка из БД
     */
    private data class ChunkData(
        val id: String,
        val documentId: String,
        val chunkIndex: Int,
        val content: String,
        val embedding: List<Float>,
        val category: DocumentCategory,
        val source: String
    )
}

/**
 * Статистика базы знаний
 */
data class KnowledgeBaseStatistics(
    val totalChunks: Int,
    val chunksByCategory: Map<String, Int>
)

