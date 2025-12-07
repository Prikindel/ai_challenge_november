package com.prike.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Категории тем отзывов
 */
@Serializable(with = ReviewTopicSerializer::class)
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

/**
 * Кастомный serializer для ReviewTopic, который автоматически заменяет неизвестные значения на OTHER
 */
object ReviewTopicSerializer : KSerializer<ReviewTopic> {
    override val descriptor: SerialDescriptor = serialDescriptor<String>()

    override fun serialize(encoder: Encoder, value: ReviewTopic) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): ReviewTopic {
        val value = decoder.decodeString()
        return try {
            ReviewTopic.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // Если значение не найдено, возвращаем OTHER
            ReviewTopic.OTHER
        }
    }
}

