package com.prike.presentation.controller

import com.prike.domain.model.SupportRequest
import com.prike.domain.model.Source
import com.prike.domain.service.SupportService
import com.prike.domain.service.CRMMCPService
import com.prike.presentation.dto.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Контроллер для API поддержки пользователей
 */
class SupportController(
    private val supportService: SupportService?,
    private val crmMCPService: CRMMCPService?
) {
    private val logger = LoggerFactory.getLogger(SupportController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Задать вопрос поддержке
            post("/api/support/ask") {
                try {
                    if (supportService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("Support service is not available")
                        )
                        return@post
                    }
                    
                    val request = call.receive<SupportQuestionRequest>()
                    
                    // Валидация
                    if (request.question.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Question cannot be blank")
                        )
                        return@post
                    }
                    
                    // Создаём SupportRequest
                    val supportRequest = SupportRequest(
                        ticketId = request.ticketId,
                        userId = request.userId,
                        question = request.question
                    )
                    
                    // Получаем ответ от SupportService
                    val response = runBlocking {
                        supportService.answerQuestion(supportRequest)
                    }
                    
                    // Преобразуем источники в DTO
                    val sourcesDto = response.sources.map { source ->
                        SourceDto(
                            title = source.title,
                            content = source.content,
                            url = source.url
                        )
                    }
                    
                    // Определяем, нужно ли создавать тикет
                    val shouldCreateTicket = request.ticketId == null && 
                        request.userId != null && 
                        request.question.length > 50
                    
                    call.respond(
                        SupportQuestionResponse(
                            answer = response.answer,
                            sources = sourcesDto,
                            suggestions = response.suggestions,
                            ticketId = response.ticketId ?: request.ticketId,
                            shouldCreateTicket = shouldCreateTicket
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to process support question", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to process support question: ${e.message}")
                    )
                }
            }
            
            // Получить информацию о тикете
            get("/api/support/ticket/{ticketId}") {
                try {
                    if (crmMCPService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("CRM MCP service is not available")
                        )
                        return@get
                    }
                    
                    val ticketId = call.parameters["ticketId"]
                        ?: throw IllegalArgumentException("ticketId is required")
                    
                    val ticket = runBlocking {
                        crmMCPService.getTicket(ticketId)
                    }
                    
                    if (ticket == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Ticket not found: $ticketId")
                        )
                        return@get
                    }
                    
                    val messagesDto = ticket.messages.map { message ->
                        TicketMessageDto(
                            id = message.id,
                            ticketId = message.ticketId,
                            author = message.author,
                            content = message.content,
                            timestamp = message.timestamp
                        )
                    }
                    
                    call.respond(
                        TicketWithMessagesDto(
                            id = ticket.id,
                            userId = ticket.userId,
                            subject = ticket.subject,
                            description = ticket.description,
                            status = ticket.status.name,
                            priority = ticket.priority.name,
                            messages = messagesDto,
                            createdAt = ticket.createdAt,
                            updatedAt = ticket.updatedAt
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to get ticket", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get ticket: ${e.message}")
                    )
                }
            }
            
            // Получить тикеты пользователя
            get("/api/support/user/{userId}/tickets") {
                try {
                    if (crmMCPService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("CRM MCP service is not available")
                        )
                        return@get
                    }
                    
                    val userId = call.parameters["userId"]
                        ?: throw IllegalArgumentException("userId is required")
                    
                    val tickets = runBlocking {
                        crmMCPService.getUserTickets(userId)
                    }
                    
                    val ticketsDto = tickets.map { ticket ->
                        TicketDto(
                            id = ticket.id,
                            userId = ticket.userId,
                            subject = ticket.subject,
                            description = ticket.description,
                            status = ticket.status.name,
                            priority = ticket.priority.name,
                            messageCount = ticket.messages.size,
                            createdAt = ticket.createdAt,
                            updatedAt = ticket.updatedAt
                        )
                    }
                    
                    call.respond(TicketsListResponse(tickets = ticketsDto))
                } catch (e: Exception) {
                    logger.error("Failed to get user tickets", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get user tickets: ${e.message}")
                    )
                }
            }
            
            // Создать новый тикет
            post("/api/support/ticket") {
                try {
                    if (crmMCPService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("CRM MCP service is not available")
                        )
                        return@post
                    }
                    
                    val request = call.receive<CreateTicketRequest>()
                    
                    // Валидация
                    if (request.userId.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("userId cannot be blank")
                        )
                        return@post
                    }
                    
                    if (request.subject.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("subject cannot be blank")
                        )
                        return@post
                    }
                    
                    if (request.description.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("description cannot be blank")
                        )
                        return@post
                    }
                    
                    val ticket = runBlocking {
                        crmMCPService.createTicket(
                            userId = request.userId,
                            subject = request.subject,
                            description = request.description
                        )
                    }
                    
                    if (ticket == null) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to create ticket")
                        )
                        return@post
                    }
                    
                    val messagesDto = ticket.messages.map { message ->
                        TicketMessageDto(
                            id = message.id,
                            ticketId = message.ticketId,
                            author = message.author,
                            content = message.content,
                            timestamp = message.timestamp
                        )
                    }
                    
                    call.respond(
                        HttpStatusCode.Created,
                        TicketWithMessagesDto(
                            id = ticket.id,
                            userId = ticket.userId,
                            subject = ticket.subject,
                            description = ticket.description,
                            status = ticket.status.name,
                            priority = ticket.priority.name,
                            messages = messagesDto,
                            createdAt = ticket.createdAt,
                            updatedAt = ticket.updatedAt
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to create ticket", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to create ticket: ${e.message}")
                    )
                }
            }
            
            // Добавить сообщение в тикет
            post("/api/support/ticket/{ticketId}/message") {
                try {
                    if (crmMCPService == null) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("CRM MCP service is not available")
                        )
                        return@post
                    }
                    
                    val ticketId = call.parameters["ticketId"]
                        ?: throw IllegalArgumentException("ticketId is required")
                    
                    val request = call.receive<AddTicketMessageRequest>()
                    
                    // Валидация
                    if (request.author.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("author cannot be blank")
                        )
                        return@post
                    }
                    
                    if (request.content.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("content cannot be blank")
                        )
                        return@post
                    }
                    
                    if (request.author != "user" && request.author != "support") {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("author must be 'user' or 'support'")
                        )
                        return@post
                    }
                    
                    val message = runBlocking {
                        crmMCPService.addMessage(
                            ticketId = ticketId,
                            author = request.author,
                            content = request.content
                        )
                    }
                    
                    if (message == null) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to add message")
                        )
                        return@post
                    }
                    
                    call.respond(
                        HttpStatusCode.Created,
                        TicketMessageDto(
                            id = message.id,
                            ticketId = message.ticketId,
                            author = message.author,
                            content = message.content,
                            timestamp = message.timestamp
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to add message", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to add message: ${e.message}")
                    )
                }
            }
        }
    }
}

