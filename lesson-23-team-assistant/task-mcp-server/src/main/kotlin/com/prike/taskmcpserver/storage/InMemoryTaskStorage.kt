package com.prike.taskmcpserver.storage

import com.prike.taskmcpserver.model.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory хранилище для задач команды
 * Используется для демонстрации работы MCP сервера
 */
class InMemoryTaskStorage {
    private val logger = LoggerFactory.getLogger(InMemoryTaskStorage::class.java)
    
    private val tasks = ConcurrentHashMap<String, Task>()
    private val taskIdCounter = AtomicLong(1)
    
    init {
        initializeTestData()
    }
    
    /**
     * Инициализация тестовых данных
     */
    private fun initializeTestData() {
        logger.info("Инициализация тестовых данных Task Management")
        
        val now = System.currentTimeMillis()
        
        // Создаём тестовые задачи
        val task1 = Task(
            id = "task-1",
            title = "Исправить критический баг в авторизации",
            description = "Пользователи не могут войти в систему. Нужно срочно исправить.",
            status = TaskStatus.BLOCKED,
            priority = Priority.HIGH,
            assignee = "user-1",
            dueDate = now + 2L * 24 * 60 * 60 * 1000, // через 2 дня
            blockedBy = listOf("task-5"), // блокируется задачей 5
            blocks = listOf("task-2", "task-3"),
            createdAt = now - 5L * 24 * 60 * 60 * 1000, // 5 дней назад
            updatedAt = now - 1L * 24 * 60 * 60 * 1000 // 1 день назад
        )
        
        val task2 = Task(
            id = "task-2",
            title = "Добавить двухфакторную аутентификацию",
            description = "Реализовать 2FA для повышения безопасности",
            status = TaskStatus.TODO,
            priority = Priority.HIGH,
            assignee = "user-2",
            dueDate = now + 7L * 24 * 60 * 60 * 1000, // через 7 дней
            blockedBy = listOf("task-1"), // блокируется задачей 1
            blocks = emptyList(),
            createdAt = now - 4L * 24 * 60 * 60 * 1000, // 4 дня назад
            updatedAt = now - 4L * 24 * 60 * 60 * 1000
        )
        
        val task3 = Task(
            id = "task-3",
            title = "Обновить документацию API",
            description = "Обновить документацию с новыми endpoints",
            status = TaskStatus.IN_PROGRESS,
            priority = Priority.MEDIUM,
            assignee = "user-3",
            dueDate = now + 5L * 24 * 60 * 60 * 1000, // через 5 дней
            blockedBy = listOf("task-1"), // блокируется задачей 1
            blocks = emptyList(),
            createdAt = now - 3L * 24 * 60 * 60 * 1000, // 3 дня назад
            updatedAt = now - 12L * 60 * 60 * 1000 // 12 часов назад
        )
        
        val task4 = Task(
            id = "task-4",
            title = "Оптимизировать запросы к базе данных",
            description = "Улучшить производительность запросов",
            status = TaskStatus.IN_REVIEW,
            priority = Priority.MEDIUM,
            assignee = "user-1",
            dueDate = null,
            blockedBy = emptyList(),
            blocks = emptyList(),
            createdAt = now - 2L * 24 * 60 * 60 * 1000, // 2 дня назад
            updatedAt = now - 6L * 60 * 60 * 1000 // 6 часов назад
        )
        
        val task5 = Task(
            id = "task-5",
            title = "Настроить CI/CD pipeline",
            description = "Настроить автоматическую сборку и деплой",
            status = TaskStatus.IN_PROGRESS,
            priority = Priority.URGENT,
            assignee = "user-2",
            dueDate = now + 1L * 24 * 60 * 60 * 1000, // через 1 день
            blockedBy = emptyList(),
            blocks = listOf("task-1"), // блокирует задачу 1
            createdAt = now - 6L * 24 * 60 * 60 * 1000, // 6 дней назад
            updatedAt = now - 2L * 60 * 60 * 1000 // 2 часа назад
        )
        
        val task6 = Task(
            id = "task-6",
            title = "Добавить unit тесты",
            description = "Покрыть тестами критичные модули",
            status = TaskStatus.TODO,
            priority = Priority.LOW,
            assignee = "user-3",
            dueDate = now + 14L * 24 * 60 * 60 * 1000, // через 14 дней
            blockedBy = emptyList(),
            blocks = emptyList(),
            createdAt = now - 1L * 24 * 60 * 60 * 1000, // 1 день назад
            updatedAt = now - 1L * 24 * 60 * 60 * 1000
        )
        
        val task7 = Task(
            id = "task-7",
            title = "Рефакторинг кода",
            description = "Улучшить структуру кода и читаемость",
            status = TaskStatus.DONE,
            priority = Priority.MEDIUM,
            assignee = "user-1",
            dueDate = null,
            blockedBy = emptyList(),
            blocks = emptyList(),
            createdAt = now - 10L * 24 * 60 * 60 * 1000, // 10 дней назад
            updatedAt = now - 3L * 24 * 60 * 60 * 1000 // 3 дня назад
        )
        
        tasks[task1.id] = task1
        tasks[task2.id] = task2
        tasks[task3.id] = task3
        tasks[task4.id] = task4
        tasks[task5.id] = task5
        tasks[task6.id] = task6
        tasks[task7.id] = task7
        
        logger.info("Инициализировано ${tasks.size} задач")
    }
    
    /**
     * Получить задачу по ID
     */
    fun getTask(taskId: String): Task? {
        return tasks[taskId]
    }
    
    /**
     * Получить все задачи с фильтрами
     */
    fun getTasks(
        status: TaskStatus? = null,
        priority: Priority? = null,
        assignee: String? = null
    ): List<Task> {
        return tasks.values
            .filter { task ->
                (status == null || task.status == status) &&
                (priority == null || task.priority == priority) &&
                (assignee == null || task.assignee == assignee)
            }
            .sortedByDescending { it.createdAt }
    }
    
    /**
     * Получить задачи по приоритету
     */
    fun getTasksByPriority(priority: Priority): List<Task> {
        return tasks.values
            .filter { it.priority == priority }
            .sortedByDescending { it.createdAt }
    }
    
    /**
     * Создать новую задачу
     */
    fun createTask(
        title: String,
        description: String,
        priority: Priority,
        assignee: String?,
        dueDate: Long?
    ): Task {
        val taskId = "task-${taskIdCounter.getAndIncrement()}"
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
        
        tasks[taskId] = task
        logger.info("Создана задача: $taskId")
        return task
    }
    
    /**
     * Обновить задачу
     */
    fun updateTask(
        taskId: String,
        title: String? = null,
        description: String? = null,
        status: TaskStatus? = null,
        priority: Priority? = null,
        assignee: String? = null,
        dueDate: Long? = null
    ): Task? {
        val task = tasks[taskId] ?: return null
        
        val updatedTask = task.copy(
            title = title ?: task.title,
            description = description ?: task.description,
            status = status ?: task.status,
            priority = priority ?: task.priority,
            assignee = assignee ?: task.assignee,
            dueDate = dueDate ?: task.dueDate,
            updatedAt = System.currentTimeMillis()
        )
        
        tasks[taskId] = updatedTask
        logger.info("Обновлена задача: $taskId")
        return updatedTask
    }
    
    /**
     * Получить статус проекта
     */
    fun getProjectStatus(): Project {
        val allTasks = tasks.values.toList()
        val tasksByStatus = allTasks.groupBy { it.status }.mapValues { it.value.size }
        val tasksByPriority = allTasks.groupBy { it.priority }.mapValues { it.value.size }
        val blockedTasks = allTasks.count { it.status == TaskStatus.BLOCKED }
        val tasksInProgress = allTasks.count { it.status == TaskStatus.IN_PROGRESS }
        val tasksDone = allTasks.count { it.status == TaskStatus.DONE }
        
        return Project(
            totalTasks = allTasks.size,
            tasksByStatus = tasksByStatus,
            tasksByPriority = tasksByPriority,
            blockedTasks = blockedTasks,
            tasksInProgress = tasksInProgress,
            tasksDone = tasksDone
        )
    }
}

