package com.prike.presentation.dto

import com.prike.data.dto.AnimalEncyclopediaResponse
import kotlinx.serialization.Serializable

/**
 * DTO для HTTP ответа энциклопедии животных
 */
@Serializable
data class AnimalEncyclopediaResponseDto(
    val response: AnimalEncyclopediaResponse
)

