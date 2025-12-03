package com.prike.crmmcpserver.storage

import com.prike.crmmcpserver.model.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory хранилище для CRM данных
 * Используется для демонстрации работы MCP сервера
 */
class InMemoryCRMStorage {
    private val logger = LoggerFactory.getLogger(InMemoryCRMStorage::class.java)
    
    private val users = ConcurrentHashMap<String, User>()
    private val tickets = ConcurrentHashMap<String, Ticket>()
    private val ticketMessages = ConcurrentHashMap<String, MutableList<TicketMessage>>()
    
    private val userIdCounter = AtomicLong(1)
    private val ticketIdCounter = AtomicLong(1)
    private val messageIdCounter = AtomicLong(1)
    
    init {
        initializeTestData()
    }
    
    /**
     * Инициализация тестовых данных
     */
    private fun initializeTestData() {
        logger.info("Инициализация тестовых данных CRM")
        
        // Создаём тестовых пользователей
        val user1 = User(
            id = "user-1",
            email = "john.doe@example.com",
            name = "John Doe",
            status = UserStatus.ACTIVE,
            subscription = Subscription(
                plan = "premium",
                expiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000 // через 30 дней
            ),
            createdAt = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000 // 90 дней назад
        )
        
        val user2 = User(
            id = "user-2",
            email = "jane.smith@example.com",
            name = "Jane Smith",
            status = UserStatus.ACTIVE,
            subscription = Subscription(
                plan = "basic",
                expiresAt = null // бессрочная
            ),
            createdAt = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000 // 60 дней назад
        )
        
        val user3 = User(
            id = "user-3",
            email = "bob.wilson@example.com",
            name = "Bob Wilson",
            status = UserStatus.SUSPENDED,
            subscription = null,
            createdAt = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 // 30 дней назад
        )
        
        users[user1.id] = user1
        users[user2.id] = user2
        users[user3.id] = user3
        
        // Создаём тестовые тикеты
        val ticket1 = Ticket(
            id = "ticket-1",
            userId = user1.id,
            subject = "Проблема с авторизацией",
            description = "Не могу войти в аккаунт, выдает ошибку",
            status = TicketStatus.OPEN,
            priority = Priority.HIGH,
            messages = emptyList(),
            createdAt = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000, // 2 дня назад
            updatedAt = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
        )
        
        val ticket2 = Ticket(
            id = "ticket-2",
            userId = user2.id,
            subject = "Вопрос о подписке",
            description = "Как отменить подписку?",
            status = TicketStatus.IN_PROGRESS,
            priority = Priority.MEDIUM,
            messages = emptyList(),
            createdAt = System.currentTimeMillis() - 1L * 24 * 60 * 60 * 1000, // 1 день назад
            updatedAt = System.currentTimeMillis() - 12L * 60 * 60 * 1000 // 12 часов назад
        )
        
        val ticket3 = Ticket(
            id = "ticket-3",
            userId = user1.id,
            subject = "Восстановление пароля",
            description = "Забыл пароль, нужна помощь",
            status = TicketStatus.RESOLVED,
            priority = Priority.LOW,
            messages = emptyList(),
            createdAt = System.currentTimeMillis() - 5L * 24 * 60 * 60 * 1000, // 5 дней назад
            updatedAt = System.currentTimeMillis() - 4L * 24 * 60 * 60 * 1000 // 4 дня назад
        )
        
        tickets[ticket1.id] = ticket1
        tickets[ticket2.id] = ticket2
        tickets[ticket3.id] = ticket3
        
        // Создаём сообщения для тикетов
        val message1 = TicketMessage(
            id = "msg-1",
            ticketId = ticket1.id,
            author = "user",
            content = "Не могу войти в аккаунт, выдает ошибку",
            timestamp = ticket1.createdAt
        )
        
        val message2 = TicketMessage(
            id = "msg-2",
            ticketId = ticket1.id,
            author = "support",
            content = "Проверьте правильность email и пароля. Если проблема сохраняется, попробуйте восстановить пароль.",
            timestamp = ticket1.createdAt + 60 * 60 * 1000 // через час
        )
        
        val message3 = TicketMessage(
            id = "msg-3",
            ticketId = ticket2.id,
            author = "user",
            content = "Как отменить подписку?",
            timestamp = ticket2.createdAt
        )
        
        val message4 = TicketMessage(
            id = "msg-4",
            ticketId = ticket2.id,
            author = "support",
            content = "Перейдите в настройки аккаунта → Управление подпиской → Отменить подписку",
            timestamp = ticket2.createdAt + 30 * 60 * 1000 // через 30 минут
        )
        
        ticketMessages[ticket1.id] = mutableListOf(message1, message2)
        ticketMessages[ticket2.id] = mutableListOf(message3, message4)
        ticketMessages[ticket3.id] = mutableListOf()
        
        logger.info("Инициализировано ${users.size} пользователей и ${tickets.size} тикетов")
    }
    
    /**
     * Получить пользователя по ID
     */
    fun getUser(userId: String): User? {
        return users[userId]
    }
    
    /**
     * Получить пользователя по email
     */
    fun getUserByEmail(email: String): User? {
        return users.values.find { it.email.equals(email, ignoreCase = true) }
    }
    
    /**
     * Получить тикет по ID
     */
    fun getTicket(ticketId: String): Ticket? {
        val ticket = tickets[ticketId] ?: return null
        val messages = ticketMessages[ticketId] ?: emptyList()
        return ticket.copy(messages = messages)
    }
    
    /**
     * Получить все тикеты пользователя
     */
    fun getUserTickets(userId: String): List<Ticket> {
        return tickets.values
            .filter { it.userId == userId }
            .map { ticket ->
                val messages = ticketMessages[ticket.id] ?: emptyList()
                ticket.copy(messages = messages)
            }
            .sortedByDescending { it.createdAt }
    }
    
    /**
     * Создать новый тикет
     */
    fun createTicket(userId: String, subject: String, description: String): Ticket {
        val ticketId = "ticket-${ticketIdCounter.getAndIncrement()}"
        val now = System.currentTimeMillis()
        
        val ticket = Ticket(
            id = ticketId,
            userId = userId,
            subject = subject,
            description = description,
            status = TicketStatus.OPEN,
            priority = Priority.MEDIUM,
            messages = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        
        tickets[ticketId] = ticket
        ticketMessages[ticketId] = mutableListOf()
        
        // Создаём первое сообщение от пользователя
        val message = TicketMessage(
            id = "msg-${messageIdCounter.getAndIncrement()}",
            ticketId = ticketId,
            author = "user",
            content = description,
            timestamp = now
        )
        
        ticketMessages[ticketId]?.add(message)
        
        logger.info("Создан тикет: $ticketId для пользователя: $userId")
        return ticket.copy(messages = listOf(message))
    }
    
    /**
     * Добавить сообщение в тикет
     */
    fun addMessage(ticketId: String, author: String, content: String): TicketMessage {
        val ticket = tickets[ticketId] ?: throw IllegalArgumentException("Тикет не найден: $ticketId")
        
        val message = TicketMessage(
            id = "msg-${messageIdCounter.getAndIncrement()}",
            ticketId = ticketId,
            author = author,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        
        ticketMessages[ticketId]?.add(message)
        
        // Обновляем время изменения тикета
        val updatedTicket = ticket.copy(
            updatedAt = System.currentTimeMillis()
        )
        tickets[ticketId] = updatedTicket
        
        logger.info("Добавлено сообщение в тикет: $ticketId от: $author")
        return message
    }
}

