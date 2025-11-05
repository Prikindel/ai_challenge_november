package com.prike.data.dto

import kotlinx.serialization.Serializable

/**
 * Коды ошибок валидации темы
 */
object TopicValidationErrorCode {
    const val TOPIC_MISMATCH = "TOPIC_MISMATCH"
}

/**
 * Ошибка валидации темы
 */
@Serializable
data class TopicValidationError(
    val errorCode: String,
    val message: String
)

