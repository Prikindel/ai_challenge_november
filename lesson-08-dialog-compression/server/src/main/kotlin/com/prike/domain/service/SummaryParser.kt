package com.prike.domain.service

import com.prike.domain.model.SummaryContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SummaryParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): SummaryContent {
        val cleaned = raw.trim()
        val parsed = runCatching { json.decodeFromString(SummaryResponse.serializer(), cleaned) }
            .getOrNull()
            ?: return SummaryContent(
                summary = cleaned,
                facts = emptyList(),
                openQuestions = emptyList()
            )

        return SummaryContent(
            summary = parsed.summary.ifBlank { cleaned },
            facts = parsed.facts.filter { it.isNotBlank() },
            openQuestions = parsed.openQuestions.filter { it.isNotBlank() }
        )
    }

    @Serializable
    private data class SummaryResponse(
        val summary: String,
        val facts: List<String> = emptyList(),
        @SerialName("openQuestions")
        val openQuestions: List<String> = emptyList()
    )
}
