package com.prike.taskmcpserver.model

/**
 * Модель задачи команды
 */
data class Task(
    val id: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val priority: Priority,
    val assignee: String?,
    val dueDate: Long?,
    val blockedBy: List<String>, // ID задач, которые блокируют эту задачу
    val blocks: List<String>, // ID задач, которые блокируются этой задачей
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Статус задачи
 */
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,
    BLOCKED
}

/**
 * Приоритет задачи
 */
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

