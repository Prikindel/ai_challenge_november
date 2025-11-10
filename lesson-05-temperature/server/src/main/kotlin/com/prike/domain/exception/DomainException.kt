package com.prike.domain.exception

/**
 * Базовое исключение
 */
sealed class DomainException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Ошибка валидации
 */
class ValidationException(message: String) : DomainException(message)

/**
 * Ошибка при обращении к AI сервису
 */
class AIServiceException(message: String, cause: Throwable? = null) : DomainException(message, cause)

