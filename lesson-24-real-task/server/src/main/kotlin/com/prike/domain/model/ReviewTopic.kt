package com.prike.domain.model

/**
 * Категории тем отзывов
 */
enum class ReviewTopic(val displayName: String) {
    AUTO_UPLOAD("Автозагрузка"),
    ALBUMS("Альбомы"),
    UNLIMITED("Безлимит"),
    CRASHES("Вылеты"),
    DOCUMENTS("Документы"),
    OTHER("Другое"),
    DOWNLOAD_MANAGER("Менеджер загрузок"),
    LOW_SPEED("Низкая скорость загрузки/скачивания"),
    OPERATIONS("Операции"),
    FILE_OPERATIONS("Операции с файлами и папками"),
    STORAGE_DISPLAY("Отображение места"),
    OFFLINE("Офлайн"),
    UI_ERRORS("Ошибки интерфейса"),
    RESOURCE_USAGE("Потребление ресурсов"),
    LINK_SETTINGS("Проблема с настройками ссылки"),
    MISSING_FILES("Пропажа файлов"),
    PHOTO_VIDEO_VIEWER("Просмотр фото, видео"),
    VIEWER("Просмотрщик"),
    MANUAL_UPLOAD("Ручная загрузка"),
    SCANNER("Сканер"),
    DOWNLOAD("Скачивание"),
    INSTALLATION("Установка"),
    PHOTO_SLICE("Фотосрез");

    companion object {
        /**
         * Находит категорию по названию (регистронезависимо)
         */
        fun fromName(name: String): ReviewTopic? {
            return values().find { 
                it.displayName.equals(name, ignoreCase = true) ||
                it.name.equals(name, ignoreCase = true)
            }
        }

        /**
         * Возвращает список всех названий категорий
         */
        fun allDisplayNames(): List<String> {
            return values().map { it.displayName }
        }
    }
}

