package com.prike.presentation.controller

import com.prike.data.parser.CsvParser
import com.prike.data.parser.JsonParser
import com.prike.data.parser.LogParser
import com.prike.data.repository.DataRepository
import com.prike.presentation.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для загрузки данных
 */
class DataController(
    private val dataRepository: DataRepository
) {
    private val logger = LoggerFactory.getLogger(DataController::class.java)
    private val csvParser = CsvParser()
    private val jsonParser = JsonParser()
    private val logParser = LogParser()
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Загрузка CSV файла
            post("/api/data/upload/csv") {
                try {
                    val multipart = call.receiveMultipart()
                    var filename: String? = null
                    var fileBytes: ByteArray? = null
                    
                    multipart.forEachPart { part ->
                        when (part) {
                            is io.ktor.server.request.ApplicationRequestPart.FileItem -> {
                                filename = part.originalFileName ?: "unknown.csv"
                                fileBytes = part.streamProvider().readBytes()
                            }
                            else -> {
                                part.dispose()
                            }
                        }
                    }
                    
                    if (filename == null || fileBytes == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("CSV file is required")
                        )
                        return@post
                    }
                    
                    logger.info("Uploading CSV file: $filename (${fileBytes.size} bytes)")
                    
                    // Парсим CSV
                    val records = csvParser.parse(fileBytes, filename)
                    
                    if (records.isEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("CSV file is empty or invalid")
                        )
                        return@post
                    }
                    
                    // Сохраняем в БД
                    val saved = dataRepository.saveRecords(records)
                    
                    if (!saved) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to save records to database")
                        )
                        return@post
                    }
                    
                    call.respond(
                        HttpStatusCode.OK,
                        UploadResponse(
                            filename = filename,
                            recordsCount = records.size,
                            source = "csv"
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error uploading CSV file: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Error uploading CSV file: ${e.message}")
                    )
                }
            }
            
            // Загрузка JSON файла
            post("/api/data/upload/json") {
                try {
                    val multipart = call.receiveMultipart()
                    var filename: String? = null
                    var fileBytes: ByteArray? = null
                    
                    multipart.forEachPart { part ->
                        when (part) {
                            is io.ktor.server.request.ApplicationRequestPart.FileItem -> {
                                filename = part.originalFileName ?: "unknown.json"
                                fileBytes = part.streamProvider().readBytes()
                            }
                            else -> {
                                part.dispose()
                            }
                        }
                    }
                    
                    if (filename == null || fileBytes == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("JSON file is required")
                        )
                        return@post
                    }
                    
                    logger.info("Uploading JSON file: $filename (${fileBytes.size} bytes)")
                    
                    // Парсим JSON
                    val records = jsonParser.parse(fileBytes, filename)
                    
                    if (records.isEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("JSON file is empty or invalid")
                        )
                        return@post
                    }
                    
                    // Сохраняем в БД
                    val saved = dataRepository.saveRecords(records)
                    
                    if (!saved) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to save records to database")
                        )
                        return@post
                    }
                    
                    call.respond(
                        HttpStatusCode.OK,
                        UploadResponse(
                            filename = filename,
                            recordsCount = records.size,
                            source = "json"
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error uploading JSON file: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Error uploading JSON file: ${e.message}")
                    )
                }
            }
            
            // Загрузка файла логов
            post("/api/data/upload/logs") {
                try {
                    val multipart = call.receiveMultipart()
                    var filename: String? = null
                    var fileBytes: ByteArray? = null
                    
                    multipart.forEachPart { part ->
                        when (part) {
                            is io.ktor.server.request.ApplicationRequestPart.FileItem -> {
                                filename = part.originalFileName ?: "unknown.log"
                                fileBytes = part.streamProvider().readBytes()
                            }
                            else -> {
                                part.dispose()
                            }
                        }
                    }
                    
                    if (filename == null || fileBytes == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Log file is required")
                        )
                        return@post
                    }
                    
                    logger.info("Uploading log file: $filename (${fileBytes.size} bytes)")
                    
                    // Парсим логи
                    val records = logParser.parse(fileBytes, filename)
                    
                    if (records.isEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Log file is empty or invalid")
                        )
                        return@post
                    }
                    
                    // Сохраняем в БД
                    val saved = dataRepository.saveRecords(records)
                    
                    if (!saved) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to save records to database")
                        )
                        return@post
                    }
                    
                    call.respond(
                        HttpStatusCode.OK,
                        UploadResponse(
                            filename = filename,
                            recordsCount = records.size,
                            source = "logs"
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error uploading log file: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Error uploading log file: ${e.message}")
                    )
                }
            }
            
            // Получение статистики по данным
            get("/api/data/stats") {
                try {
                    val totalCount = dataRepository.getTotalRecordsCount()
                    val csvCount = dataRepository.getRecordsCountBySource("csv")
                    val jsonCount = dataRepository.getRecordsCountBySource("json")
                    val logsCount = dataRepository.getRecordsCountBySource("logs")
                    
                    call.respond(
                        HttpStatusCode.OK,
                        DataStatsResponse(
                            total = totalCount,
                            bySource = mapOf(
                                "csv" to csvCount,
                                "json" to jsonCount,
                                "logs" to logsCount
                            )
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error getting data stats: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Error getting data stats: ${e.message}")
                    )
                }
            }
        }
    }
}

@Serializable
data class UploadResponse(
    val filename: String,
    val recordsCount: Int,
    val source: String
)

@Serializable
data class DataStatsResponse(
    val total: Int,
    val bySource: Map<String, Int>
)
