package com.prike

import io.github.cdimascio.dotenv.dotenv
import java.io.File

object Config {
    private val dotenv = run {
        // .env файл находится в корне проекта (ai_challenge_november), а не в уроке
        val projectRoot = findProjectRoot()
        try {
            dotenv {
                directory = projectRoot
                filename = ".env"
                ignoreIfMissing = true
            }
        } catch (e: Exception) {
            dotenv { ignoreIfMissing = true }
        }
    }
    
    val serverHost: String = dotenv["SERVER_HOST"] ?: System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val serverPort: Int = (dotenv["SERVER_PORT"] ?: System.getenv("SERVER_PORT") ?: "8080").toInt()
    
    /**
     * Находит корень проекта (ai_challenge_november)
     * Ищет директорию, содержащую папки lesson-XX-*
     */
    private fun findProjectRoot(): String {
        var currentDir = File(System.getProperty("user.dir"))

        // Если мы в mcp-server, идем на уровень выше
        if (currentDir.name == "mcp-server") {
            currentDir = currentDir.parentFile
        }

        // Ищем корень проекта (ai_challenge_november)
        // Идем вверх по дереву, пока не найдем директорию с .env файлом
        var searchDir = currentDir
        while (searchDir.parentFile != null) {
            val envFile = File(searchDir, ".env")
            if (envFile.exists()) {
                return searchDir.absolutePath
            }

            // Проверяем, может быть мы уже в корне проекта
            // (если есть несколько уроков в директории)
            val parent = searchDir.parentFile
            if (parent == searchDir) {
                break
            }
            searchDir = parent
        }

        // Если не нашли, возвращаем текущую директорию
        // (fallback на системные переменные окружения)
        return currentDir.absolutePath
    }
    
    /**
     * Находит корень урока (папку lesson-11-first-mcp-tool)
     * Ищет папку, соответствующую паттерну lesson-XX-*, идя вверх по директориям
     */
    fun getLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)
        
        // Если мы в папке server/, то корень урока - это родительская директория
        if (dir.name == "server") {
            val parent = dir.parentFile
            if (parent != null && parent.name.matches(Regex("lesson-\\d+.*"))) {
                return parent.absolutePath
            }
        }
        
        // Если текущая директория - это корень урока
        if (dir.name.matches(Regex("lesson-\\d+.*")) && dir.isDirectory) {
            return dir.absolutePath
        }
        
        // Идем вверх по директориям, ищем папку lesson-XX-*
        while (true) {
            if (dir.name.matches(Regex("lesson-\\d+.*")) && dir.isDirectory) {
                val clientDir = File(dir, "client")
                if (clientDir.exists()) {
                    return dir.absolutePath
                }
                return dir.absolutePath
            }
            
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                break
            }
            dir = parent
        }
        
        // Ищем lesson-11-first-mcp-tool специально (приоритет)
        val lesson11Path = File(currentDir, "lesson-11-first-mcp-tool")
        if (lesson11Path.exists() && lesson11Path.isDirectory) {
            val clientDir = File(lesson11Path, "client")
            if (clientDir.exists()) {
                return lesson11Path.absolutePath
            }
        }
        
        // Ищем любую папку lesson-XX-* с client/ внутри в текущей директории
        val currentDirFile = File(currentDir)
        currentDirFile.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name.matches(Regex("lesson-\\d+.*"))) {
                val clientDir = File(file, "client")
                if (clientDir.exists()) {
                    return file.absolutePath
                }
            }
        }
        
        // Если не нашли, возвращаем текущую директорию
        return currentDir
    }
}

