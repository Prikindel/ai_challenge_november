package com.voiceagent.presentation.controller

import com.voiceagent.domain.service.AudioConversionService
import com.voiceagent.domain.service.SpeechRecognitionService
import com.voiceagent.domain.usecase.ChatUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.request.*
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import org.slf4j.LoggerFactory

class VoiceController(
    private val speechRecognitionService: SpeechRecognitionService,
    private val audioConversionService: AudioConversionService,
    private val chatUseCase: ChatUseCase
) {
    private val logger = LoggerFactory.getLogger(VoiceController::class.java)

    fun voiceRoutes(route: Route) {
        route.route("/api/voice") {
            post("/recognize") {
                val audioData = extractAudio(call.receiveMultipart())
                if (audioData == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No audio data provided"))
                    return@post
                }

                val converted = runCatching {
                    audioConversionService.convertToVoskWav(audioData)
                }.getOrElse {
                    logger.error("Audio conversion failed", it)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Audio conversion failed"))
                    return@post
                }

                val text = runCatching { speechRecognitionService.recognize(converted) }
                    .getOrElse {
                        logger.error("Speech recognition failed", it)
                        ""
                    }

                call.respond(mapOf("text" to text, "status" to "success"))
            }

            post("/process") {
                val audioData = extractAudio(call.receiveMultipart())
                if (audioData == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No audio data provided"))
                    return@post
                }

                val converted = runCatching {
                    audioConversionService.convertToVoskWav(audioData)
                }.getOrElse {
                    logger.error("Audio conversion failed", it)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Audio conversion failed"))
                    return@post
                }

                val recognizedText = runCatching { speechRecognitionService.recognize(converted) }
                    .getOrElse {
                        logger.error("Speech recognition failed", it)
                        ""
                    }

                if (recognizedText.isBlank()) {
                    call.respond(mapOf("error" to "Could not recognize speech", "text" to ""))
                    return@post
                }

                val llmResponse = runCatching { chatUseCase.processMessage(recognizedText) }
                    .onFailure { logger.error("LLM response failed", it) }
                    .getOrDefault("Ошибка при обращении к LLM")

                call.respond(
                    mapOf(
                        "recognizedText" to recognizedText,
                        "response" to llmResponse,
                        "status" to "success"
                    )
                )
            }
        }
    }

    private suspend fun extractAudio(multipart: io.ktor.http.content.MultiPartData): ByteArray? {
        var audioData: ByteArray? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    audioData = part.streamProvider().readBytes()
                }

                else -> {}
            }
            part.dispose()
        }

        return audioData
    }
}

