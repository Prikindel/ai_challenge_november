package com.prike.domain.service

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

/**
 * Конвертация аудио в формат для Vosk: WAV, 16kHz, mono, 16-bit PCM.
 * Использует установленный ffmpeg.
 */
class AudioConversionService {
    private val logger = LoggerFactory.getLogger(AudioConversionService::class.java)

    fun convertToVoskWav(inputAudio: ByteArray, inputExtension: String = "webm"): ByteArray {
        if (inputAudio.isEmpty()) return inputAudio

        val inputFile = Files.createTempFile("voice_input_", ".$inputExtension").toFile()
        val outputFile = Files.createTempFile("voice_output_", ".wav").toFile()

        return try {
            inputFile.writeBytes(inputAudio)

            val process = ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", inputFile.absolutePath,
                "-ar", "16000",
                "-ac", "1",
                "-sample_fmt", "s16",
                outputFile.absolutePath
            )
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val errorOutput = process.inputStream.bufferedReader().readText()
                logger.error("ffmpeg failed: {}", errorOutput)
                throw IllegalStateException("FFmpeg conversion failed with code $exitCode")
            }

            outputFile.readBytes()
        } finally {
            runCatching { inputFile.delete() }
            runCatching { outputFile.delete() }
        }
    }
}

