package com.voiceagent.config

data class SpeechRecognitionConfig(
    val enabled: Boolean = true,
    val provider: String = "vosk",
    val modelPath: String = "models/vosk-model-small-ru-0.22",
    val sampleRate: Int = 16000
)

data class LocalLLMAuthConfig(
    val type: String? = null,
    val user: String? = null,
    val password: String? = null,
    val token: String? = null
)

data class LocalLLMConfig(
    val enabled: Boolean = true,
    val provider: String = "ollama",
    val baseUrl: String = "http://localhost:11434",
    val model: String = "llama3.2",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val auth: LocalLLMAuthConfig? = null
)

data class VoiceConfig(
    val speechRecognition: SpeechRecognitionConfig = SpeechRecognitionConfig(),
    val localLLM: LocalLLMConfig = LocalLLMConfig()
)

