package com.prike.taskmcpserver.storage

import com.prike.taskmcpserver.model.*

/**
 * Интерфейс для хранилища задач
 */
interface TaskStorage {
    fun getTask(taskId: String): Task?
    fun getTasks(status: TaskStatus? = null, priority: Priority? = null, assignee: String? = null): List<Task>
    fun getTasksByPriority(priority: Priority): List<Task>
    fun createTask(title: String, description: String, priority: Priority, assignee: String?, dueDate: Long?): Task
    fun updateTask(taskId: String, title: String? = null, description: String? = null, 
                   status: TaskStatus? = null, priority: Priority? = null, 
                   assignee: String? = null, dueDate: Long? = null): Task?
    fun getProjectStatus(): ProjectStatus
}

