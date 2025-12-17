package com.prike.domain.model

/**
 * Категории документов в базе знаний
 */
enum class DocumentCategory(val displayName: String, val path: String) {
    PROJECTS("Проекты", "projects"),
    LEARNING("Обучение", "learning"),
    PERSONAL("Личное", "personal"),
    REFERENCES("Справочники", "references");
    
    companion object {
        /**
         * Получить категорию по имени пути
         */
        fun fromPath(path: String): DocumentCategory? {
            return values().find { it.path == path.lowercase() }
        }
        
        /**
         * Получить категорию по имени
         */
        fun fromName(name: String): DocumentCategory? {
            return values().find { 
                it.name.equals(name, ignoreCase = true) || 
                it.displayName.equals(name, ignoreCase = true)
            }
        }
    }
}

