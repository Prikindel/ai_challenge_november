package com.prike.taskmcpserver.storage

import com.prike.taskmcpserver.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Репозиторий для работы с задачами (SQLite)
 */
class TaskRepository(
    private val databasePath: String
) : TaskStorage {
    private val logger = LoggerFactory.getLogger(TaskRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        // Создаём директорию для базы данных, если её нет
        val dbFile = File(databasePath)
        dbFile.parentFile?.mkdirs()
        
        logger.info("Инициализация TaskRepository с базой данных: $databasePath")
        
        // Инициализируем схему БД
        initializeDatabase()
        
        // Инициализируем тестовые данные, если БД пустая
        val existingTasks = getAllTasks()
        logger.info("Найдено задач в БД: ${existingTasks.size}")
        if (existingTasks.isEmpty()) {
            logger.info("База данных пуста, инициализируем тестовые данные")
            initializeTestData()
        }
    }
    
    /**
     * Инициализирует схему базы данных
     */
    fun initializeDatabase() {
        withConnection { connection ->
            // Таблица задач
            connection.createStatement().executeUpdate("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    status TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    assignee TEXT,
                    due_date INTEGER,
                    blocked_by TEXT NOT NULL,
                    blocks TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            
            // Индексы для быстрого поиска
            connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_tasks_status 
                ON tasks(status)
            """.trimIndent())
            
            connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_tasks_priority 
                ON tasks(priority)
            """.trimIndent())
            
            connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_tasks_assignee 
                ON tasks(assignee)
            """.trimIndent())
            
            connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_tasks_created 
                ON tasks(created_at)
            """.trimIndent())
            
            logger.info("Task database schema initialized")
        }
    }
    
    /**
     * Инициализация тестовых данных
     */
    private fun initializeTestData() {
        logger.info("Инициализация тестовых данных Task Management")
        
        val now = System.currentTimeMillis()
        
        // Создаём тестовые задачи
        val testTasks = listOf(
            Task(
                id = "task-1",
                title = "Исправить критический баг в авторизации",
                description = "Пользователи не могут войти в систему. Нужно срочно исправить.",
                status = TaskStatus.BLOCKED,
                priority = Priority.HIGH,
                assignee = "user-1",
                dueDate = now + 2L * 24 * 60 * 60 * 1000,
                blockedBy = listOf("task-5"),
                blocks = listOf("task-2", "task-3"),
                createdAt = now - 5L * 24 * 60 * 60 * 1000,
                updatedAt = now - 1L * 24 * 60 * 60 * 1000
            ),
            Task(
                id = "task-2",
                title = "Добавить двухфакторную аутентификацию",
                description = "Реализовать 2FA для повышения безопасности",
                status = TaskStatus.TODO,
                priority = Priority.HIGH,
                assignee = "user-2",
                dueDate = now + 7L * 24 * 60 * 60 * 1000,
                blockedBy = listOf("task-1"),
                blocks = emptyList(),
                createdAt = now - 4L * 24 * 60 * 60 * 1000,
                updatedAt = now - 4L * 24 * 60 * 60 * 1000
            ),
            Task(
                id = "task-3",
                title = "Обновить документацию API",
                description = "Обновить документацию с новыми endpoints",
                status = TaskStatus.IN_PROGRESS,
                priority = Priority.MEDIUM,
                assignee = "user-3",
                dueDate = now + 5L * 24 * 60 * 60 * 1000,
                blockedBy = listOf("task-1"),
                blocks = emptyList(),
                createdAt = now - 3L * 24 * 60 * 60 * 1000,
                updatedAt = now - 12L * 60 * 60 * 1000
            ),
            Task(
                id = "task-4",
                title = "Оптимизировать запросы к базе данных",
                description = "Улучшить производительность запросов",
                status = TaskStatus.IN_REVIEW,
                priority = Priority.MEDIUM,
                assignee = "user-1",
                dueDate = null,
                blockedBy = emptyList(),
                blocks = emptyList(),
                createdAt = now - 2L * 24 * 60 * 60 * 1000,
                updatedAt = now - 6L * 60 * 60 * 1000
            ),
            Task(
                id = "task-5",
                title = "Настроить CI/CD pipeline",
                description = "Настроить автоматическую сборку и деплой",
                status = TaskStatus.IN_PROGRESS,
                priority = Priority.URGENT,
                assignee = "user-2",
                dueDate = now + 1L * 24 * 60 * 60 * 1000,
                blockedBy = emptyList(),
                blocks = listOf("task-1"),
                createdAt = now - 6L * 24 * 60 * 60 * 1000,
                updatedAt = now - 2L * 60 * 60 * 1000
            ),
            Task(
                id = "task-6",
                title = "Добавить unit тесты",
                description = "Покрыть тестами критичные модули",
                status = TaskStatus.TODO,
                priority = Priority.LOW,
                assignee = "user-3",
                dueDate = now + 14L * 24 * 60 * 60 * 1000,
                blockedBy = emptyList(),
                blocks = emptyList(),
                createdAt = now - 1L * 24 * 60 * 60 * 1000,
                updatedAt = now - 1L * 24 * 60 * 60 * 1000
            ),
            Task(
                id = "task-7",
                title = "Рефакторинг кода",
                description = "Улучшить структуру кода и читаемость",
                status = TaskStatus.DONE,
                priority = Priority.MEDIUM,
                assignee = "user-1",
                dueDate = null,
                blockedBy = emptyList(),
                blocks = emptyList(),
                createdAt = now - 10L * 24 * 60 * 60 * 1000,
                updatedAt = now - 3L * 24 * 60 * 60 * 1000
            )
        )
        
        testTasks.forEach { task ->
            saveTask(task)
        }
        
        logger.info("Инициализировано ${testTasks.size} тестовых задач")
    }
    
    /**
     * Получить задачу по ID
     */
    override fun getTask(taskId: String): Task? {
        return withConnection { connection ->
            val sql = "SELECT * FROM tasks WHERE id = ?"
            val stmt = connection.prepareStatement(sql)
            stmt.setString(1, taskId)
            val rs = stmt.executeQuery()
            
            if (rs.next()) {
                mapRowToTask(rs)
            } else {
                null
            }
        }
    }
    
    /**
     * Получить все задачи с фильтрами
     */
    override fun getTasks(
        status: TaskStatus?,
        priority: Priority?,
        assignee: String?
    ): List<Task> {
        return withConnection { connection ->
            val conditions = mutableListOf<String>()
            val params = mutableListOf<Any>()
            
            if (status != null) {
                conditions.add("status = ?")
                params.add(status.name)
            }
            
            if (priority != null) {
                conditions.add("priority = ?")
                params.add(priority.name)
            }
            
            if (assignee != null) {
                conditions.add("assignee = ?")
                params.add(assignee)
            }
            
            val whereClause = if (conditions.isNotEmpty()) {
                "WHERE ${conditions.joinToString(" AND ")}"
            } else {
                ""
            }
            
            val sql = "SELECT * FROM tasks $whereClause ORDER BY created_at DESC"
            val stmt = connection.prepareStatement(sql)
            
            params.forEachIndexed { index, param ->
                when (param) {
                    is String -> stmt.setString(index + 1, param)
                }
            }
            
            val rs = stmt.executeQuery()
            val tasks = mutableListOf<Task>()
            
            while (rs.next()) {
                tasks.add(mapRowToTask(rs))
            }
            
            tasks
        }
    }
    
    /**
     * Получить все задачи
     */
    fun getAllTasks(): List<Task> {
        return getTasks()
    }
    
    /**
     * Получить задачи по приоритету
     */
    override fun getTasksByPriority(priority: Priority): List<Task> {
        return getTasks(priority = priority)
    }
    
    /**
     * Создать новую задачу
     */
    override fun createTask(
        title: String,
        description: String,
        priority: Priority,
        assignee: String?,
        dueDate: Long?
    ): Task {
        val taskId = "task-${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        
        val task = Task(
            id = taskId,
            title = title,
            description = description,
            status = TaskStatus.TODO,
            priority = priority,
            assignee = assignee,
            dueDate = dueDate,
            blockedBy = emptyList(),
            blocks = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        
        saveTask(task)
        logger.info("Создана задача: $taskId")
        return task
    }
    
    /**
     * Сохранить задачу в БД
     */
    private fun saveTask(task: Task) {
        withConnection { connection ->
            val sql = """
                INSERT OR REPLACE INTO tasks 
                (id, title, description, status, priority, assignee, due_date, 
                 blocked_by, blocks, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            val stmt = connection.prepareStatement(sql)
            stmt.setString(1, task.id)
            stmt.setString(2, task.title)
            stmt.setString(3, task.description)
            stmt.setString(4, task.status.name)
            stmt.setString(5, task.priority.name)
            stmt.setString(6, task.assignee)
            if (task.dueDate != null) {
                stmt.setLong(7, task.dueDate)
            } else {
                stmt.setNull(7, java.sql.Types.BIGINT)
            }
            stmt.setString(8, json.encodeToString(task.blockedBy))
            stmt.setString(9, json.encodeToString(task.blocks))
            stmt.setLong(10, task.createdAt)
            stmt.setLong(11, task.updatedAt)
            
            val rowsAffected = stmt.executeUpdate()
            logger.debug("Сохранена задача ${task.id}, затронуто строк: $rowsAffected")
        }
    }
    
    /**
     * Обновить задачу
     */
    override fun updateTask(
        taskId: String,
        title: String?,
        description: String?,
        status: TaskStatus?,
        priority: Priority?,
        assignee: String?,
        dueDate: Long?
    ): Task? {
        val existingTask = getTask(taskId) ?: return null
        
        val updatedTask = existingTask.copy(
            title = title ?: existingTask.title,
            description = description ?: existingTask.description,
            status = status ?: existingTask.status,
            priority = priority ?: existingTask.priority,
            assignee = assignee ?: existingTask.assignee,
            dueDate = dueDate ?: existingTask.dueDate,
            updatedAt = System.currentTimeMillis()
        )
        
        saveTask(updatedTask)
        logger.info("Обновлена задача: $taskId")
        return updatedTask
    }
    
    /**
     * Получить статус проекта
     */
    override fun getProjectStatus(): ProjectStatus {
        return withConnection { connection ->
            val allTasks = getAllTasks()
            val tasksByStatus = allTasks.groupBy { it.status }.mapValues { it.value.size }
            val tasksByPriority = allTasks.groupBy { it.priority }.mapValues { it.value.size }
            val blockedTasks = allTasks.count { it.status == TaskStatus.BLOCKED }
            
            ProjectStatus(
                totalTasks = allTasks.size,
                tasksByStatus = tasksByStatus,
                tasksByPriority = tasksByPriority,
                blockedTasks = blockedTasks
            )
        }
    }
    
    /**
     * Маппинг строки БД в объект Task
     */
    private fun mapRowToTask(rs: ResultSet): Task {
        val blockedByJson = rs.getString("blocked_by") ?: "[]"
        val blocksJson = rs.getString("blocks") ?: "[]"
        
        return Task(
            id = rs.getString("id"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            status = TaskStatus.valueOf(rs.getString("status")),
            priority = Priority.valueOf(rs.getString("priority")),
            assignee = rs.getString("assignee"),
            dueDate = rs.getLong("due_date").takeIf { !rs.wasNull() },
            blockedBy = try {
                json.decodeFromString<List<String>>(blockedByJson)
            } catch (e: Exception) {
                emptyList()
            },
            blocks = try {
                json.decodeFromString<List<String>>(blocksJson)
            } catch (e: Exception) {
                emptyList()
            },
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at")
        )
    }
    
    /**
     * Выполнить операцию с подключением к БД
     */
    private fun <T> withConnection(block: (Connection) -> T): T {
        val connection = DriverManager.getConnection("jdbc:sqlite:$databasePath")
        try {
            return block(connection)
        } finally {
            connection.close()
        }
    }
}

