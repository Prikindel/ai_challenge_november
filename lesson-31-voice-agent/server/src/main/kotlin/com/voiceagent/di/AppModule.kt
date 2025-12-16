package com.voiceagent.di

import com.voiceagent.Config
import com.voiceagent.data.client.OpenAIClient
import com.voiceagent.data.repository.AIRepositoryImpl
import com.voiceagent.domain.usecase.ChatUseCase
import com.voiceagent.presentation.controller.ServerController
import com.voiceagent.presentation.controller.VoiceController
import com.voiceagent.domain.service.SpeechRecognitionService
import com.voiceagent.domain.service.AudioConversionService
import java.io.File

/**
 * Dependency Injection модуль
 * Создает и связывает все зависимости приложения
 */
object AppModule {
    
    // Храним клиент для закрытия ресурсов
    private var aiClient: OpenAIClient? = null
    private var chatUseCase: ChatUseCase? = null
    private var speechService: SpeechRecognitionService? = null
    private var audioConversionService: AudioConversionService? = null
    
    /**
     * Создать контроллер чата со всеми зависимостями
     */
    fun createChatController(): ServerController {
        val useCase = getOrCreateChatUseCase()
        return ServerController(useCase)
    }

    fun createVoiceController(): VoiceController {
        val voiceConfig = Config.voiceConfig
        val useCase = getOrCreateChatUseCase()

        val modelPath = resolvePath(voiceConfig.speechRecognition.modelPath)

        val speech = speechService ?: SpeechRecognitionService(
            modelPath = modelPath,
            sampleRate = voiceConfig.speechRecognition.sampleRate.toFloat()
        ).also { speechService = it }

        val conversion = audioConversionService ?: AudioConversionService().also { audioConversionService = it }

        return VoiceController(
            speechRecognitionService = speech,
            audioConversionService = conversion,
            chatUseCase = useCase
        )
    }

    private fun resolvePath(path: String): String {
        val file = File(path)
        return if (file.isAbsolute) path else File(Config.lessonRoot, path).absolutePath
    }

    private fun getOrCreateChatUseCase(): ChatUseCase {
        chatUseCase?.let { return it }

        val aiConfig = Config.aiConfig
        val client = aiClient ?: OpenAIClient(
            apiKey = aiConfig.apiKey,
            apiUrl = aiConfig.apiUrl,
            model = aiConfig.model,
            temperature = aiConfig.temperature,
            maxTokens = aiConfig.maxTokens,
            requestTimeoutSeconds = aiConfig.requestTimeout,
            systemPrompt = aiConfig.systemPrompt,
            authType = aiConfig.authType,
            authUser = aiConfig.authUser,
            authPassword = aiConfig.authPassword
        ).also { aiClient = it }

        val repository = AIRepositoryImpl(client)
        return ChatUseCase(repository).also { chatUseCase = it }
    }
    
    /**
     * Получить директорию с клиентом
     */
    fun getClientDirectory(): File {
        val lessonRoot = findLessonRoot()
        return File(lessonRoot, "client")
    }
    
    /**
     * Находит корень урока (папку lesson-31-voice-agent)
     */
    private fun findLessonRoot(): String {
        val currentDir = System.getProperty("user.dir")
        var dir = File(currentDir)
        
        while (dir != null) {
            val lessonDir = File(dir, "lesson-31-voice-agent")
            if (lessonDir.exists() && lessonDir.isDirectory) {
                return lessonDir.absolutePath
            }
            
            if (dir.name == "lesson-31-voice-agent") {
                return dir.absolutePath
            }
            
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                break
            }
            dir = parent
        }
        
        return currentDir
    }
    
    /**
     * Закрыть ресурсы (HTTP клиент)
     */
    fun close() {
        aiClient?.close()
        aiClient = null
        chatUseCase = null
        speechService?.close()
        speechService = null
        audioConversionService = null
    }
}
