package com.prike.domain.exception

/**
 * Исключение для ошибок AI сервиса
 */
class AIServiceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

