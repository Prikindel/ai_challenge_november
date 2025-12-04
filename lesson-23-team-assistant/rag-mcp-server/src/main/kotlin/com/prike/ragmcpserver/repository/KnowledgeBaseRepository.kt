package com.prike.ragmcpserver.data.repository

import com.prike.ragmcpserver.data.model.Document
import com.prike.ragmcpserver.data.model.DocumentChunk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Репозиторий для работы с базой знаний (SQLite)
 */
class KnowledgeBaseRepository(
    private val databasePath: String
) {
    private val logger = LoggerFactory.getLogger(KnowledgeBaseRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        // Создаём директорию для базы данных, если её нет
        val dbFile = File(databasePath)
        dbFile.parentFile?.mkdirs()
        
        // Инициализируем схему БД
        initializeDatabase()
    }
    
    /**
     * Инициализирует схему базы данных
     */
    fun initializeDatabase() {
        withConnection { connection ->
            // Таблица документов
            connection.createStatement().executeUpdate("""
                CREATE TABLE IF NOT EXISTS documents (
                    id TEXT PRIMARY KEY,
                    file_path TEXT NOT NULL,
                    title TEXT,
                    content TEXT NOT NULL,
                    indexed_at INTEGER NOT NULL,
                    chunk_count INTEGER NOT NULL
                )
            """.trimIndent())
            
            // Таблица чанков с эмбеддингами
            connection.createStatement().executeUpdate("""
                CREATE TABLE IF NOT EXISTS document_chunks (
                    id TEXT PRIMARY KEY,
                    document_id TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    start_index INTEGER NOT NULL,
                    end_index INTEGER NOT NULL,
                    token_count INTEGER NOT NULL,
                    embedding TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (document_id) REFERENCES documents(id)
                )
            """.trimIndent())
            
            // Индексы для быстрого поиска
            connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_chunks_document 
                ON document_chunks(document_id)
            """.trimIndent())
            
            connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_chunks_created 
                ON document_chunks(created_at)
            """.trimIndent())
            
            logger.info("Database schema initialized")
        }
    }
    
    /**
     * Сохраняет документ в базу данных
     */
    fun saveDocument(document: Document) {
        withConnection { connection ->
            val sql = """
                INSERT OR REPLACE INTO documents 
                (id, file_path, title, content, indexed_at, chunk_count)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, document.id)
                stmt.setString(2, document.filePath)
                stmt.setString(3, document.title)
                stmt.setString(4, document.content)
                stmt.setLong(5, document.indexedAt)
                stmt.setInt(6, document.chunkCount)
                stmt.executeUpdate()
            }
            
            logger.debug("Document saved: ${document.id}")
        }
    }
    
    /**
     * Сохраняет чанки документа в базу данных
     */
    fun saveChunks(chunks: List<DocumentChunk>) {
        if (chunks.isEmpty()) return
        
        withConnection { connection ->
            val sql = """
                INSERT OR REPLACE INTO document_chunks 
                (id, document_id, chunk_index, content, start_index, end_index, 
                 token_count, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            connection.prepareStatement(sql).use { stmt ->
                chunks.forEach { chunk ->
                    // Сериализуем эмбеддинг в JSON
                    val embeddingJson = json.encodeToString(chunk.embedding)
                    
                    stmt.setString(1, chunk.id)
                    stmt.setString(2, chunk.documentId)
                    stmt.setInt(3, chunk.chunkIndex)
                    stmt.setString(4, chunk.content)
                    stmt.setInt(5, chunk.startIndex)
                    stmt.setInt(6, chunk.endIndex)
                    stmt.setInt(7, chunk.tokenCount)
                    stmt.setString(8, embeddingJson)
                    stmt.setLong(9, chunk.createdAt)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            
            logger.debug("Saved ${chunks.size} chunks")
        }
    }
    
    /**
     * Получает документ по ID
     */
    fun getDocument(documentId: String): Document? {
        return withConnection { connection ->
            val sql = """
                SELECT id, file_path, title, content, indexed_at, chunk_count
                FROM documents
                WHERE id = ?
            """.trimIndent()
            
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, documentId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        mapRowToDocument(rs)
                    } else {
                        null
                    }
                }
            }
        }
    }
    
    /**
     * Получает все документы
     */
    fun getAllDocuments(): List<Document> {
        return withConnection { connection ->
            val sql = """
                SELECT id, file_path, title, content, indexed_at, chunk_count
                FROM documents
                ORDER BY indexed_at DESC
            """.trimIndent()
            
            connection.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(mapRowToDocument(rs))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Получает документы по списку ID (для оптимизации поиска)
     */
    fun getDocumentsByIds(ids: List<String>): List<Document> {
        if (ids.isEmpty()) return emptyList()
        
        return withConnection { connection ->
            val placeholders = ids.joinToString(",") { "?" }
            val sql = """
                SELECT id, file_path, title, content, indexed_at, chunk_count
                FROM documents
                WHERE id IN ($placeholders)
            """.trimIndent()
            
            connection.prepareStatement(sql).use { stmt ->
                ids.forEachIndexed { index, id ->
                    stmt.setString(index + 1, id)
                }
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(mapRowToDocument(rs))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Получает документ по пути к файлу
     */
    fun getDocumentByPath(filePath: String): Document? {
        // Нормализуем путь (убираем лишние слэши, нормализуем разделители)
        val normalizedPath = normalizePath(filePath)
        
        return withConnection { connection ->
            // Сначала пробуем точное совпадение
            var sql = """
                SELECT id, file_path, title, content, indexed_at, chunk_count
                FROM documents
                WHERE file_path = ?
            """.trimIndent()
            
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, normalizedPath)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return@withConnection mapRowToDocument(rs)
                    }
                }
            }
            
            // Если не нашли, пробуем поиск с нормализацией путей в БД
            sql = """
                SELECT id, file_path, title, content, indexed_at, chunk_count
                FROM documents
            """.trimIndent()
            
            connection.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        val dbPath = rs.getString("file_path")
                        val normalizedDbPath = normalizePath(dbPath)
                        if (normalizedPath == normalizedDbPath) {
                            return@withConnection mapRowToDocument(rs)
                        }
                    }
                }
            }
            
            null
        }
    }
    
    /**
     * Нормализует путь к документу для сравнения
     */
    private fun normalizePath(path: String): String {
        return path
            .replace("\\", "/")
            .replace(Regex("/+"), "/")
            .trim('/')
    }
    
    /**
     * Получает все чанки из базы данных
     */
    fun getAllChunks(): List<DocumentChunk> {
        return withConnection { connection ->
            val sql = """
                SELECT id, document_id, chunk_index, content, start_index, end_index,
                       token_count, embedding, created_at
                FROM document_chunks
                ORDER BY created_at DESC
            """.trimIndent()
            
            connection.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(mapRowToDocumentChunk(rs))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Получает чанки для конкретного документа
     */
    fun getChunksByDocument(documentId: String): List<DocumentChunk> {
        return withConnection { connection ->
            val sql = """
                SELECT id, document_id, chunk_index, content, start_index, end_index,
                       token_count, embedding, created_at
                FROM document_chunks
                WHERE document_id = ?
                ORDER BY chunk_index ASC
            """.trimIndent()
            
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, documentId)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(mapRowToDocumentChunk(rs))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Удаляет документ и все его чанки
     */
    fun deleteDocument(documentId: String) {
        withConnection { connection ->
            // Сначала удаляем чанки
            connection.prepareStatement("DELETE FROM document_chunks WHERE document_id = ?").use { stmt ->
                stmt.setString(1, documentId)
                stmt.executeUpdate()
            }
            
            // Затем удаляем документ
            connection.prepareStatement("DELETE FROM documents WHERE id = ?").use { stmt ->
                stmt.setString(1, documentId)
                stmt.executeUpdate()
            }
            
            logger.debug("Document deleted: $documentId")
        }
    }
    
    /**
     * Получает статистику базы знаний
     */
    fun getStatistics(): KnowledgeBaseStatistics {
        return try {
            withConnection { connection ->
                // Убеждаемся, что база данных инициализирована
                initializeDatabase()
                
                val documentsCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM documents")
                    .use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                
                val chunksCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM document_chunks")
                    .use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                
                KnowledgeBaseStatistics(
                    documentsCount = documentsCount,
                    chunksCount = chunksCount
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to get statistics, database may not be initialized: ${e.message}")
            // Возвращаем пустую статистику, если база данных не инициализирована
            KnowledgeBaseStatistics(
                documentsCount = 0,
                chunksCount = 0
            )
        }
    }
    
    /**
     * Маппинг строки ResultSet в Document
     */
    private fun mapRowToDocument(rs: ResultSet): Document {
        return Document(
            id = rs.getString("id"),
            filePath = rs.getString("file_path"),
            title = rs.getString("title"),
            content = rs.getString("content"),
            indexedAt = rs.getLong("indexed_at"),
            chunkCount = rs.getInt("chunk_count")
        )
    }
    
    /**
     * Маппинг строки ResultSet в DocumentChunk
     */
    private fun mapRowToDocumentChunk(rs: ResultSet): DocumentChunk {
        val embeddingJson = rs.getString("embedding")
        val embedding = json.decodeFromString<List<Float>>(embeddingJson)
        
        return DocumentChunk(
            id = rs.getString("id"),
            documentId = rs.getString("document_id"),
            chunkIndex = rs.getInt("chunk_index"),
            content = rs.getString("content"),
            startIndex = rs.getInt("start_index"),
            endIndex = rs.getInt("end_index"),
            tokenCount = rs.getInt("token_count"),
            embedding = embedding,
            createdAt = rs.getLong("created_at")
        )
    }
    
    /**
     * Выполняет операцию с подключением к БД
     */
    private fun <T> withConnection(block: (Connection) -> T): T {
        DriverManager.getConnection("jdbc:sqlite:$databasePath").use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                return result
            } catch (e: Exception) {
                connection.rollback()
                logger.error("Database operation failed: ${e.message}", e)
                throw e
            }
        }
    }
}

/**
 * Статистика базы знаний
 */
data class KnowledgeBaseStatistics(
    val documentsCount: Int,
    val chunksCount: Int
)

