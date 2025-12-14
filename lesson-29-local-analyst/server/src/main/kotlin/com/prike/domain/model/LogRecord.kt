package com.prike.domain.model

import kotlinx.serialization.Serializable

/**
 * Модель записи лога
 */
@Serializable
data class LogRecord(
    val timestamp: String,
    val level: String,  // ERROR, WARN, INFO, DEBUG
    val message: String,
    val error: String? = null,
    val stackTrace: String? = null,
    val source: String? = null,  // источник лога (компонент, класс и т.д.)
    val thread: String? = null
)
