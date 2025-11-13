package com.prike.domain.service

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import com.prike.data.dto.MessageDto

class TokenEstimator(
    encodingType: EncodingType = EncodingType.CL100K_BASE
) {
    private val encodingRegistry: EncodingRegistry = Encodings.newLazyEncodingRegistry()
    private val defaultEncoding: Encoding = encodingRegistry.getEncoding(encodingType)

    fun countTokens(messages: List<MessageDto>): Int =
        countWithEncoding(messages, defaultEncoding)

    fun approximateForModel(messages: List<MessageDto>, model: String?): Int {
        if (messages.isEmpty()) return 0

        val modelName = model?.lowercase()
        val encoding = modelName?.let { resolveEncodingForModel(it) } ?: defaultEncoding
        val encodedCount = countWithEncoding(messages, encoding)

        return when {
            modelName != null && modelName.contains("llama") -> scaleForLlama(encodedCount)
            else -> encodedCount
        }
    }

    private fun countWithEncoding(messages: List<MessageDto>, encoding: Encoding): Int {
        if (messages.isEmpty()) return 0
        var total = 0
        messages.forEach { message ->
            total += encoding.encode(message.role).size
            total += encoding.encode(message.content).size
            message.name?.let { total += encoding.encode(it).size }
        }
        return total
    }

    private fun resolveEncodingForModel(model: String): Encoding {
        val normalizedUpper = model.trim()
            .replace('-', '_')
            .replace('.', '_')
            .uppercase()

        val direct = EncodingType.values().firstOrNull { it.name == normalizedUpper }
        if (direct != null) {
            return encodingRegistry.getEncoding(direct)
        }

        val normalized = model.lowercase()
        val fallbackType = when {
            normalized.contains("gpt-4o") || normalized.contains("gpt4o") -> EncodingType.CL100K_BASE
            normalized.contains("gpt-4") || normalized.contains("gpt4") -> EncodingType.CL100K_BASE
            normalized.contains("gpt-3.5") || normalized.contains("gpt3.5") -> EncodingType.CL100K_BASE
            normalized.contains("gpt-3") || normalized.contains("gpt3") || normalized.contains("davinci") -> EncodingType.R50K_BASE
            normalized.contains("llama") -> EncodingType.CL100K_BASE
            normalized.contains("mistral") -> EncodingType.CL100K_BASE
            else -> EncodingType.CL100K_BASE
        }

        return encodingRegistry.getEncoding(fallbackType)
    }

    private fun scaleForLlama(rawCount: Int): Int {
        val scaled = (rawCount * LLAMA_SCALING_FACTOR).toInt()
        return if (scaled <= 0) 1 else scaled
    }

    companion object {
        private const val LLAMA_SCALING_FACTOR = 0.36
    }
}
