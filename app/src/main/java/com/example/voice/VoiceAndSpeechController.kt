package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

private const val TAG = "VoiceAndSpeech"

data class VoiceUiState(
    val isAvailable: Boolean = false,
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val rmsLevel: Float = 0f,
    val errorMessage: String? = null,
    val isSpeaking: Boolean = false,
    val speakingMessageId: String? = null
)

class VoiceAndSpeechController(context: Context) {

    private val appContext = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false
    private var activeResultCallback: ((String) -> Unit)? = null

    private val _state = MutableStateFlow(
        VoiceUiState(isAvailable = SpeechRecognizer.isRecognitionAvailable(appContext))
    )
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.getDefault())
                    ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    tts?.setSpeechRate(1.02f)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _state.update { it.copy(isSpeaking = true) }
                        }

                        override fun onDone(utteranceId: String?) {
                            _state.update {
                                it.copy(isSpeaking = false, speakingMessageId = null)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _state.update {
                                it.copy(isSpeaking = false, speakingMessageId = null)
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech init unavailable: ${e.message}")
        }
    }

    fun startListening(onFinalResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.update {
                it.copy(
                    isAvailable = false,
                    errorMessage = "Speech recognition service is not installed on this device."
                )
            }
            return
        }

        stopSpeaking()
        stopListeningInternal()
        activeResultCallback = onFinalResult

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.update {
                        it.copy(
                            isAvailable = true,
                            isListening = true,
                            partialTranscript = "",
                            errorMessage = null
                        )
                    }
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _state.update { it.copy(rmsLevel = normalized) }
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _state.update { it.copy(rmsLevel = 0f) }
                }

                override fun onError(error: Int) {
                    val fallbackPartial = _state.value.partialTranscript.trim()
                    if ((error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                        fallbackPartial.isNotEmpty()
                    ) {
                        _state.update {
                            it.copy(
                                isListening = false,
                                partialTranscript = "",
                                rmsLevel = 0f,
                                errorMessage = null
                            )
                        }
                        activeResultCallback?.invoke(fallbackPartial)
                        activeResultCallback = null
                        return
                    }

                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check microphone access."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during voice recognition."
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try speaking again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic to try again."
                        else -> "Voice recognition stopped (code $error)."
                    }
                    _state.update {
                        it.copy(
                            isListening = false,
                            partialTranscript = "",
                            rmsLevel = 0f,
                            errorMessage = if (error == SpeechRecognizer.ERROR_CLIENT) null else message
                        )
                    }
                    activeResultCallback = null
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()?.trim()
                        ?.ifEmpty { _state.value.partialTranscript.trim() }
                        .orEmpty()
                    _state.update {
                        it.copy(
                            isListening = false,
                            partialTranscript = "",
                            rmsLevel = 0f,
                            errorMessage = null
                        )
                    }
                    if (spokenText.isNotEmpty()) {
                        activeResultCallback?.invoke(spokenText)
                    }
                    activeResultCallback = null
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _state.update { it.copy(partialTranscript = partial) }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            activeResultCallback = null
            _state.update {
                it.copy(
                    isListening = false,
                    errorMessage = e.localizedMessage ?: "Unable to start voice recognition."
                )
            }
        }
    }

    fun stopListening() {
        val pendingPartial = _state.value.partialTranscript.trim()
        stopListeningInternal()
        _state.update {
            it.copy(
                isListening = false,
                partialTranscript = "",
                rmsLevel = 0f
            )
        }
        if (pendingPartial.isNotEmpty()) {
            activeResultCallback?.invoke(pendingPartial)
        }
        activeResultCallback = null
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    fun speak(messageId: String, rawMarkdownText: String) {
        if (!ttsReady) {
            _state.update {
                it.copy(errorMessage = "Text-to-speech engine is not ready on this device.")
            }
            return
        }
        val cleanText = stripMarkdownForSpeech(rawMarkdownText)
        if (cleanText.isBlank()) return

        _state.update {
            it.copy(isSpeaking = true, speakingMessageId = messageId)
        }
        tts?.speak(
            cleanText.take(3900),
            TextToSpeech.QUEUE_FLUSH,
            null,
            UUID.randomUUID().toString()
        )
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        }
        _state.update {
            it.copy(isSpeaking = false, speakingMessageId = null)
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun release() {
        activeResultCallback = null
        stopListeningInternal()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
        tts = null
    }

    private fun stripMarkdownForSpeech(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("[*_~#>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
