package com.prike.ragmcpserver.domain.service

import com.prike.ragmcpserver.data.model.Document
import com.prike.ragmcpserver.data.model.DocumentChunk as DataDocumentChunk
import com.prike.ragmcpserver.data.repository.KnowledgeBaseRepository
import com.prike.ragmcpserver.domain.indexing.TextChunker
import com.prike.ragmcpserver.domain.indexing.VectorNormalizer
import com.prike.ragmcpserver.domain.model.TextChunk
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Сервис для индексации документов
 * 
 * Пайплайн индексации:
 * 1. Загрузка документа
 * 2. Разбивка на чанки
 * 3. Генерация эмбеддингов
 * 4. Нормализация векторов
 * 5. Сохранение в БД
 */
class DocumentIndexer(
    private val documentLoader: DocumentLoader,
    private val textChunker: TextChunker,
    private val embeddingService: EmbeddingService,
    private val vectorNormalizer: VectorNormalizer,
    private val knowledgeBaseRepository: KnowledgeBaseRepository
) {
    private val logger = LoggerFactory.getLogger(DocumentIndexer::class.java)
    
    /**
     * Индексирует документ из файла
     * 
     * @param filePath путь к файлу
     * @return результат индексации
     */
    suspend fun indexDocument(filePath: String): IndexingResult {
        logger.info("Starting indexing: $filePath")
        
        try {
            // 1. Загрузка документа
            val loadedDocument = documentLoader.loadDocument(filePath)
            
            // Используем путь из загруженного документа (может быть относительным)
            val documentPath = loadedDocument.filePath
            
            // Проверяем, не индексирован ли уже этот документ
            val existingDocument = knowledgeBaseRepository.getAllDocuments()
                .firstOrNull { it.filePath == documentPath }
            
            val documentId = existingDocument?.id ?: UUID.randomUUID().toString()
            
            // 2. Разбивка на чанки
            val textChunks = textChunker.chunk(loadedDocument.content, documentId)
            logger.info("Document split into ${textChunks.size} chunks")
            
            if (textChunks.isEmpty()) {
                return IndexingResult(
                    documentId = documentId,
                    chunksCount = 0,
                    success = false,
                    error = "Document has no content to index"
                )
            }
            
            // 3. Генерация эмбеддингов для каждого чанка
            val indexedChunks = textChunks.mapNotNull { textChunk ->
                runCatching {
                    // Генерируем эмбеддинг
                    val embedding = embeddingService.generateEmbedding(textChunk.content)

                    // Нормализуем вектор
                    val normalizedEmbedding = vectorNormalizer.normalizeTo01(embedding)

                    // Создаём чанк с эмбеддингом
                    DataDocumentChunk(
                        id = textChunk.id,
                        documentId = documentId,
                        chunkIndex = textChunk.chunkIndex,
                        content = textChunk.content,
                        startIndex = textChunk.startIndex,
                        endIndex = textChunk.endIndex,
                        tokenCount = textChunk.tokenCount,
                        embedding = normalizedEmbedding,
                        createdAt = System.currentTimeMillis()
                    )
                }.onFailure { e ->
                    logger.error("Failed to generate embedding for chunk ${textChunk.id}: ${e.message}", e)
                }.getOrNull()
            }

            val successCount = indexedChunks.size
            val errorCount = textChunks.size - successCount
            
            // Логируем прогресс
            if (textChunks.size > 10) {
                logger.debug("Processed ${indexedChunks.size}/${textChunks.size} chunks successfully")
            }
            
            if (indexedChunks.isEmpty()) {
                return IndexingResult(
                    documentId = documentId,
                    chunksCount = 0,
                    success = false,
                    error = "Failed to generate embeddings for all chunks"
                )
            }
            
            // 4. Сохранение в БД
            val document = Document(
                id = documentId,
                filePath = documentPath,
                title = loadedDocument.title,
                content = loadedDocument.content,
                indexedAt = System.currentTimeMillis(),
                chunkCount = indexedChunks.size
            )
            
            knowledgeBaseRepository.saveDocument(document)
            knowledgeBaseRepository.saveChunks(indexedChunks)
            
            logger.info("Indexing completed: $filePath (${indexedChunks.size} chunks, $successCount success, $errorCount errors)")
            
            return IndexingResult(
                documentId = documentId,
                chunksCount = indexedChunks.size,
                success = true,
                errorsCount = errorCount
            )
        } catch (e: Exception) {
            logger.error("Indexing failed: $filePath", e)
            return IndexingResult(
                documentId = "",
                chunksCount = 0,
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Индексирует все документы из директории
     * 
     * @param directoryPath путь к директории
     * @return список результатов индексации
     */
    suspend fun indexDirectory(directoryPath: String): List<IndexingResult> {
        logger.info("Starting directory indexing: $directoryPath")
        
        val loadedDocuments = documentLoader.loadDocumentsFromDirectory(directoryPath)
        val results = mutableListOf<IndexingResult>()
        
        loadedDocuments.forEach { loadedDocument ->
            val result = indexDocument(loadedDocument.filePath)
            results.add(result)
        }
        
        logger.info("Directory indexing completed: ${results.size} documents processed")
        return results
    }
    
    /**
     * Индексирует документацию проекта (project/docs/ и project/README.md)
     * 
     * @param projectDocsPath путь к папке с документацией проекта
     * @param projectReadmePath путь к корневому README проекта
     * @return список результатов индексации
     */
    suspend fun indexProjectDocs(
        projectDocsPath: String?,
        projectReadmePath: String?
    ): List<IndexingResult> {
        logger.info("Starting project documentation indexing")
        val results = mutableListOf<IndexingResult>()
        
        // Индексируем документацию из project/docs/
        if (!projectDocsPath.isNullOrBlank()) {
            try {
                val docsResults = indexDirectory(projectDocsPath)
                results.addAll(docsResults)
                logger.info("Indexed ${docsResults.size} documents from $projectDocsPath")
            } catch (e: Exception) {
                logger.error("Failed to index project docs from $projectDocsPath", e)
                results.add(IndexingResult(
                    documentId = "",
                    chunksCount = 0,
                    success = false,
                    error = "Failed to index project docs: ${e.message}"
                ))
            }
        }
        
        // Индексируем корневой README
        if (!projectReadmePath.isNullOrBlank()) {
            try {
                val readmeResult = indexDocument(projectReadmePath)
                results.add(readmeResult)
                logger.info("Indexed README: $projectReadmePath (success: ${readmeResult.success})")
            } catch (e: Exception) {
                logger.error("Failed to index project README from $projectReadmePath", e)
                results.add(IndexingResult(
                    documentId = "",
                    chunksCount = 0,
                    success = false,
                    error = "Failed to index project README: ${e.message}"
                ))
            }
        }
        
        val successCount = results.count { it.success }
        logger.info("Project documentation indexing completed: ${results.size} documents processed, $successCount succeeded")
        
        return results
    }
}

/**
 * Результат индексации документа
 */
data class IndexingResult(
    val documentId: String,
    val chunksCount: Int,
    val success: Boolean,
    val error: String? = null,
    val errorsCount: Int = 0
)

