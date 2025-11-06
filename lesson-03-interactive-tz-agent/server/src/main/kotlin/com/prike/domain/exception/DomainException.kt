package com.prike.domain.exception

/**
 * Базовое исключение доменного слоя
 */
sealed class DomainException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Исключение для ошибок валидации
 */
class ValidationException(message: String, cause: Throwable? = null) : DomainException(message, cause)

/**
 * Исключение для ошибок AI сервиса
 */
class AIServiceException(message: String, cause: Throwable? = null) : DomainException(message, cause)

