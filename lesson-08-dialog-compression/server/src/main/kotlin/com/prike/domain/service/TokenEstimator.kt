package com.prike.domain.service

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import com.knuddels.jtokkit.api.ModelType
import com.prike.data.dto.MessageDto

class TokenEstimator(
    encodingType: EncodingType = EncodingType.CL100K_BASE
) {
    private val encodingRegistry: EncodingRegistry = Encodings.newLazyEncodingRegistry()
    private val defaultEncoding: Encoding = encodingRegistry.getEncoding(encodingType)

    fun countTokens(messages: List<MessageDto>): Int =
        countWithEncoding(messages, defaultEncoding)

    fun approximateForModel(messages: List<MessageDto>, model: String?): Int =
        if (model == null) countTokens(messages) else {
            val modelEncoding = runCatching { ModelType.fromName(model) }
                .getOrNull()
                ?.encodingType
                ?.let { encodingRegistry.getEncoding(it) }
                ?: defaultEncoding
            countWithEncoding(messages, modelEncoding)
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
}
