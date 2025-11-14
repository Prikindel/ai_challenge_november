package com.prike.data.repository

import com.prike.domain.repository.MemoryRepository

/**
 * Интерфейс репозитория на уровне data layer
 * Переиспользует интерфейс из domain layer для соблюдения Clean Architecture
 * 
 * Реализации (SqliteMemoryRepository, JsonMemoryRepository) находятся в этом пакете
 */
typealias DataMemoryRepository = MemoryRepository

