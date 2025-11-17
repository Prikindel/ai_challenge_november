package com.prike.domain.exception

class MCPException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

