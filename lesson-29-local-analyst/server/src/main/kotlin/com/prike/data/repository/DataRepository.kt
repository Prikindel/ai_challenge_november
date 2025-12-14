package com.prike.data.repository

import com.prike.domain.model.DataRecord
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Репозиторий для работы с загруженными данными
 */
class DataRepository(
    private val database: org.jetbrains.exposed.sql.Database
) {
    private val logger = LoggerFactory.getLogger(DataRepository::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Сохраняет записи данных в БД
     */
    fun saveRecords(records: List<DataRecord>): Boolean {
        return try {
            transaction(database) {
                records.forEach { record ->
                    DataRecordsTable.insertIgnore {
                        it[id] = record.id
                        it[source] = record.source
                        it[sourceFile] = record.sourceFile
                        it[data] = json.encodeToString(record.data)
                        it[timestamp] = record.timestamp
                        it[createdAt] = Instant.now().toString()
                    }
                }
            }
            logger.info("Saved ${records.size} data records")
            true
        } catch (e: Exception) {
            logger.error("Error saving data records: ${e.message}", e)
            false
        }
    }
    
    /**
     * Получает записи данных с ограничением
     */
    fun getRecords(limit: Int = 1000): List<DataRecord> {
        return try {
            transaction(database) {
                DataRecordsTable
                    .selectAll()
                    .orderBy(DataRecordsTable.timestamp to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        val dataMap = json.decodeFromString<Map<String, String>>(row[DataRecordsTable.data])
                        DataRecord(
                            id = row[DataRecordsTable.id],
                            source = row[DataRecordsTable.source],
                            sourceFile = row[DataRecordsTable.sourceFile],
                            data = dataMap,
                            timestamp = row[DataRecordsTable.timestamp]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting data records: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Получает записи данных по источнику
     */
    fun getRecordsBySource(source: String, limit: Int = 1000): List<DataRecord> {
        return try {
            transaction(database) {
                DataRecordsTable
                    .select { DataRecordsTable.source eq source }
                    .orderBy(DataRecordsTable.timestamp to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        val dataMap = json.decodeFromString<Map<String, String>>(row[DataRecordsTable.data])
                        DataRecord(
                            id = row[DataRecordsTable.id],
                            source = row[DataRecordsTable.source],
                            sourceFile = row[DataRecordsTable.sourceFile],
                            data = dataMap,
                            timestamp = row[DataRecordsTable.timestamp]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting data records by source: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Получает количество записей по источнику
     */
    fun getRecordsCountBySource(source: String): Int {
        return try {
            transaction(database) {
                DataRecordsTable
                    .select { DataRecordsTable.source eq source }
                    .count()
                    .toInt()
            }
        } catch (e: Exception) {
            logger.error("Error getting records count by source: ${e.message}", e)
            0
        }
    }
    
    /**
     * Получает общее количество записей
     */
    fun getTotalRecordsCount(): Int {
        return try {
            transaction(database) {
                DataRecordsTable
                    .selectAll()
                    .count()
                    .toInt()
            }
        } catch (e: Exception) {
            logger.error("Error getting total records count: ${e.message}", e)
            0
        }
    }
}
