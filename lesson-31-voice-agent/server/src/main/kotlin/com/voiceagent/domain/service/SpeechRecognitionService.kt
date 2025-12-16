package com.voiceagent.domain.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.Closeable

class SpeechRecognitionService(
    private val modelPath: String,
    private val sampleRate: Float = 16000f
) : Closeable {

    private val logger = LoggerFactory.getLogger(SpeechRecognitionService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val modelHandle: VoskLite.ModelHandle by lazy {
        logger.info("Loading Vosk model from {}", modelPath)
        VoskLite.setLogLevel(0)
        VoskLite.loadModel(modelPath)
    }

    fun recognize(audioData: ByteArray): String {
        if (audioData.isEmpty()) return ""

        VoskLite.newRecognizer(modelHandle, sampleRate).use { recognizer ->
            VoskLite.acceptWaveform(recognizer, audioData, audioData.size)
            val finalResult = VoskLite.finalResult(recognizer)
            val fallbackResult = VoskLite.partialResult(recognizer)
            return parseResult(finalResult.ifBlank { fallbackResult })
        }
    }

    private fun parseResult(jsonResult: String): String {
        return runCatching {
            val result = json.decodeFromString<VoskResult>(jsonResult)
            result.text.orEmpty().trim()
        }.getOrElse {
            logger.warn("Failed to parse Vosk result: {}", jsonResult, it)
            ""
        }
    }

    override fun close() {
        kotlin.runCatching { modelHandle.close() }
    }
}

@Serializable
private data class VoskResult(
    @SerialName("text")
    val text: String? = null
)

