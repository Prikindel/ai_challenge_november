package com.prike.di

import java.io.File

/**
 * Dependency Injection модуль
 * Создает и связывает все зависимости приложения
 */
object AppModule {
    
    /**
     * Получить директорию с клиентом
     */
    fun getClientDirectory(): File {
        val lessonRoot = findLessonRoot()
        return File(lessonRoot, "client")
    }
    
    /**
     * Находит корень урока (папку lesson-XX-...)
     * Ищет папку, начинающуюся с "lesson-", идя вверх по директориям
     */
    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)
        
        while (dir != null) {
            // Проверяем, является ли текущая директория корнем урока
            if (dir.name.startsWith("lesson-") && dir.isDirectory) {
                return dir.absolutePath
            }
            
            // Проверяем, есть ли папка lesson-XX-... в текущей директории
            dir.listFiles()?.firstOrNull { 
                it.name.startsWith("lesson-") && it.isDirectory 
            }?.let { 
                return it.absolutePath 
            }
            
            // Идем на уровень выше
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                break
            }
            dir = parent
        }
        
        // Если не нашли, возвращаем текущую директорию
        return currentDir
    }
    
    /**
     * Закрыть ресурсы (если нужно)
     */
    fun close() {
        // Закрываем ресурсы здесь при необходимости
    }
}

