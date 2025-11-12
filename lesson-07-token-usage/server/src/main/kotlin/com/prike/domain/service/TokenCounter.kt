package com.prike.domain.service

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import org.slf4j.LoggerFactory
import kotlin.math.max

/**
 * Сервис для подсчёта токенов, использующий библиотеку jtokkit.
 */
class TokenCounter(encodingName: String) {

    private val logger = LoggerFactory.getLogger(TokenCounter::class.java)
    private val registry = Encodings.newLazyEncodingRegistry()
    private val encoding: Encoding = resolveEncoding(encodingName)

    fun count(text: String?): Int {
        if (text.isNullOrEmpty()) return 0

        return runCatching {
            encoding.countTokens(text)
        }.getOrElse { throwable ->
            logger.warn("Не удалось подсчитать токены корректно, используем эвристику: ${throwable.message}")
            approximateTokens(text)
        }
    }

    private fun resolveEncoding(name: String): Encoding {
        val normalized = name.trim().replace('-', '_').uppercase()
        val encodingType = EncodingType.values().firstOrNull { it.name == normalized }
            ?: when (normalized.lowercase()) {
                "gpt3", "gpt_3", "davinci" -> EncodingType.R50K_BASE
                "gpt4", "gpt_4", "gpt-4", "gpt_4o", "gpt-4o", "gpt-4o-mini", "gpt_4o_mini" -> EncodingType.CL100K_BASE
                else -> EncodingType.CL100K_BASE
            }

        return registry.getEncoding(encodingType)
    }

    private fun approximateTokens(text: String): Int =
        max(1, text.length / APPROX_CHARS_PER_TOKEN + 1)

    companion object {
        private const val APPROX_CHARS_PER_TOKEN = 4
    }
}

