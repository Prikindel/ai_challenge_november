package com.prike.domain.exception

/**
 * Исключение для ошибок работы с AI сервисом
 */
class AIServiceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

