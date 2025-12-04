package com.prike.taskmcpserver.model

/**
 * Модель статуса проекта
 * Содержит статистику по задачам проекта
 */
data class ProjectStatus(
    val totalTasks: Int,
    val tasksByStatus: Map<TaskStatus, Int>,
    val tasksByPriority: Map<Priority, Int>,
    val blockedTasks: Int
)

