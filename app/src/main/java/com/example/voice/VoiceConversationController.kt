package com.example.voice

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Owns one-turn speech recognition and Wisteria voice playback.
 *
 * Inworld speech is fetched through Wisteria's authenticated backend bridge. Android's local
 * TextToSpeech remains a fallback so voice mode can keep working if the provider is unavailable.
 * Hands-free mode deliberately alternates between finite listen and speak turns.
 */
class VoiceConversationController(
    private val applicationContext: android.content.Context,
    private val remoteTts: InworldTtsService = InworldTtsService(),
    private val onTranscriptReady: (String) -> Unit
) : RecognitionListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(applicationContext)
    private val onDeviceRecognitionAvailable =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(applicationContext)

    private val _state = MutableStateFlow(
        VoiceConversationState(
            isRecognitionAvailable = recognitionAvailable,
            usesOnDeviceRecognition = onDeviceRecognitionAvailable
        )
    )
    val state: StateFlow<VoiceConversationState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechInitializing = false
    private var mediaPlayer: MediaPlayer? = null
    private var remoteSpeechJob: Job? = null
    private var remoteAudioFile: File? = null
    private var recognitionInProgress = false
    private var suppressNextRecognitionError = false
    private var voiceTurnAwaitingResponse = false
    private var pendingFallbackSpeech: String? = null
    private var shouldEndCallAfterSpeech = false

    fun startDictation() = onMain {
        if (_state.value.isCallActive) return@onMain
        voiceTurnAwaitingResponse = false
        shouldEndCallAfterSpeech = false
        stopRemoteSpeech()
        textToSpeech?.stop()
        _state.update {
            it.copy(
                phase = VoicePhase.IDLE,
                partialTranscript = "",
                errorMessage = null,
                isMicMuted = false
            )
        }
        ensureTextToSpeech()
        startListeningInternal()
    }

    fun startCall(handsFree: Boolean = true) = onMain {
        cancelListeningInternal()
        stopRemoteSpeech()
        textToSpeech?.stop()
        voiceTurnAwaitingResponse = false
        shouldEndCallAfterSpeech = false
        _state.update {
            it.copy(
                isCallActive = true,
                phase = VoicePhase.IDLE,
                partialTranscript = "",
                errorMessage = null,
                handsFreeEnabled = handsFree,
                isMicMuted = false
            )
        }
        ensureTextToSpeech()
        startListeningInternal()
    }

    fun endCall() = onMain {
        cancelListeningInternal()
        stopRemoteSpeech()
        textToSpeech?.stop()
        pendingFallbackSpeech = null
        voiceTurnAwaitingResponse = false
        shouldEndCallAfterSpeech = false
        _state.update {
            it.copy(
                isCallActive = false,
                phase = VoicePhase.IDLE,
                partialTranscript = "",
                errorMessage = null,
                isMicMuted = false,
                audioLevel = 0f
            )
        }
    }

    fun toggleListening() = onMain {
        when (_state.value.phase) {
            VoicePhase.LISTENING -> stopListeningInternal()
            VoicePhase.PROCESSING -> Unit
            VoicePhase.SPEAKING -> {
                stopRemoteSpeech()
                textToSpeech?.stop()
                pendingFallbackSpeech = null
                finishAgentSpeech(resumeHandsFree = false)
                startListeningInternal()
            }
            VoicePhase.IDLE,
            VoicePhase.ERROR -> startListeningInternal()
        }
    }

    fun stopListening() = onMain {
        if (_state.value.phase == VoicePhase.LISTENING) {
            stopListeningInternal()
        }
    }

    fun toggleHandsFree() = onMain {
        val enabled = !_state.value.handsFreeEnabled
        _state.update { it.copy(handsFreeEnabled = enabled, errorMessage = null) }

        if (!enabled && _state.value.phase == VoicePhase.LISTENING) {
            cancelListeningInternal()
        } else if (
            enabled &&
            _state.value.isCallActive &&
            !_state.value.isMicMuted &&
            _state.value.phase == VoicePhase.IDLE
        ) {
            startListeningInternal()
        }
    }

    fun toggleMicMuted() = onMain {
        val muted = !_state.value.isMicMuted
        _state.update { it.copy(isMicMuted = muted, errorMessage = null) }
        if (muted) {
            cancelListeningInternal()
        } else if (
            _state.value.isCallActive &&
            _state.value.handsFreeEnabled &&
            _state.value.phase == VoicePhase.IDLE
        ) {
            startListeningInternal()
        }
    }

    fun toggleSpeaker() = onMain {
        val enabled = !_state.value.isSpeakerEnabled
        _state.update { it.copy(isSpeakerEnabled = enabled) }
        if (!enabled && _state.value.phase == VoicePhase.SPEAKING) {
            stopRemoteSpeech()
            textToSpeech?.stop()
            pendingFallbackSpeech = null
            finishAgentSpeech(resumeHandsFree = true)
        }
    }

    fun onAgentResponse(text: String, endCallAfterSpeech: Boolean = false) = onMain {
        if (!voiceTurnAwaitingResponse) return@onMain
        voiceTurnAwaitingResponse = false
        shouldEndCallAfterSpeech = endCallAfterSpeech && _state.value.isCallActive
        _state.update { it.copy(lastAgentText = text, errorMessage = null) }

        if (_state.value.isSpeakerEnabled) {
            speakWithWisteriaVoice(text)
        } else {
            finishAgentSpeech(resumeHandsFree = true)
        }
    }

    fun onMicrophonePermissionDenied() = onMain {
        cancelListeningInternal()
        _state.update {
            it.copy(
                phase = VoicePhase.ERROR,
                errorMessage = "Microphone permission is needed for voice mode",
                audioLevel = 0f
            )
        }
    }

    fun destroy() = onMain {
        mainHandler.removeCallbacksAndMessages(null)
        cancelListeningInternal()
        stopRemoteSpeech()
        voiceScope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    @MainThread
    private fun startListeningInternal() {
        if (_state.value.isMicMuted || recognitionInProgress) return
        if (!recognitionAvailable) {
            _state.update {
                it.copy(
                    phase = VoicePhase.ERROR,
                    errorMessage = "No speech recognition service is available on this device"
                )
            }
            return
        }

        if (_state.value.phase == VoicePhase.SPEAKING) {
            stopRemoteSpeech()
            textToSpeech?.stop()
            pendingFallbackSpeech = null
        }

        val recognizer = speechRecognizer ?: createSpeechRecognizer() ?: return
        suppressNextRecognitionError = false
        recognitionInProgress = true
        _state.update {
            it.copy(
                phase = VoicePhase.LISTENING,
                partialTranscript = "",
                errorMessage = null,
                audioLevel = 0f
            )
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, applicationContext.packageName)
        }

        runCatching { recognizer.startListening(intent) }
            .onFailure { error ->
                recognitionInProgress = false
                _state.update {
                    it.copy(
                        phase = VoicePhase.ERROR,
                        errorMessage = error.message ?: "Voice recognition could not start",
                        audioLevel = 0f
                    )
                }
            }
    }

    @MainThread
    private fun createSpeechRecognizer(): SpeechRecognizer? {
        return runCatching {
            val recognizer = if (onDeviceRecognitionAvailable) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(applicationContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(applicationContext)
            }
            recognizer.setRecognitionListener(this)
            recognizer
        }.onFailure { error ->
            _state.update {
                it.copy(
                    phase = VoicePhase.ERROR,
                    errorMessage = error.message ?: "Voice recognition is unavailable"
                )
            }
        }.getOrNull()?.also { speechRecognizer = it }
    }

    @MainThread
    private fun stopListeningInternal() {
        if (!recognitionInProgress) return
        _state.update { it.copy(phase = VoicePhase.PROCESSING, audioLevel = 0f) }
        runCatching { speechRecognizer?.stopListening() }
    }

    @MainThread
    private fun cancelListeningInternal() {
        if (recognitionInProgress) {
            suppressNextRecognitionError = true
            runCatching { speechRecognizer?.cancel() }
        }
        recognitionInProgress = false
        _state.update {
            it.copy(
                phase = VoicePhase.IDLE,
                partialTranscript = "",
                audioLevel = 0f
            )
        }
    }

    @MainThread
    private fun speakWithWisteriaVoice(text: String) {
        val normalized = text.trim().take(2_000)
        if (normalized.isBlank()) {
            finishAgentSpeech(resumeHandsFree = true)
            return
        }

        stopRemoteSpeech()
        textToSpeech?.stop()
        pendingFallbackSpeech = null
        _state.update { it.copy(phase = VoicePhase.PROCESSING, errorMessage = null) }

        remoteSpeechJob = voiceScope.launch {
            val audio = runCatching { remoteTts.synthesize(normalized) }.getOrNull()
            if (!_state.value.isSpeakerEnabled) {
                finishAgentSpeech(resumeHandsFree = true)
                return@launch
            }

            if (audio.isNullOrEmpty()) {
                speakWithDeviceTts(normalized)
                return@launch
            }

            val file = runCatching {
                withContext(Dispatchers.IO) {
                    File.createTempFile("wisteria-voice-", ".wav", applicationContext.cacheDir)
                        .also { it.writeBytes(audio) }
                }
            }.getOrNull()

            if (file == null) {
                speakWithDeviceTts(normalized)
                return@launch
            }

            playRemoteAudio(file, normalized)
        }
    }

    @MainThread
    private fun playRemoteAudio(file: File, fallbackText: String) {
        releaseRemotePlayer()
        remoteAudioFile = file

        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { prepared ->
                onMain {
                    if (!_state.value.isSpeakerEnabled) {
                        releaseRemotePlayer()
                        finishAgentSpeech(resumeHandsFree = true)
                    } else {
                        _state.update { it.copy(phase = VoicePhase.SPEAKING, errorMessage = null) }
                        prepared.start()
                    }
                }
            }
            player.setOnCompletionListener {
                onMain {
                    releaseRemotePlayer()
                    finishAgentSpeech(resumeHandsFree = true)
                }
            }
            player.setOnErrorListener { _, _, _ ->
                onMain {
                    releaseRemotePlayer()
                    speakWithDeviceTts(fallbackText)
                }
                true
            }
            player.prepareAsync()
        }.onFailure {
            releaseRemotePlayer()
            speakWithDeviceTts(fallbackText)
        }
    }

    @MainThread
    private fun stopRemoteSpeech() {
        remoteSpeechJob?.cancel()
        remoteSpeechJob = null
        releaseRemotePlayer()
    }

    @MainThread
    private fun releaseRemotePlayer() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        remoteAudioFile?.let { file -> runCatching { file.delete() } }
        remoteAudioFile = null
    }

    @MainThread
    private fun ensureTextToSpeech() {
        if (_state.value.isTextToSpeechReady || textToSpeechInitializing) return
        textToSpeechInitializing = true
        textToSpeech = TextToSpeech(applicationContext) { status ->
            onMain {
                textToSpeechInitializing = false
                if (status != TextToSpeech.SUCCESS) {
                    pendingFallbackSpeech = null
                    _state.update {
                        it.copy(
                            phase = VoicePhase.ERROR,
                            errorMessage = "Text-to-speech is unavailable on this device"
                        )
                    }
                    return@onMain
                }

                val engine = textToSpeech ?: return@onMain
                val languageResult = engine.setLanguage(Locale.getDefault())
                val languageSupported =
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED

                if (!languageSupported) {
                    pendingFallbackSpeech = null
                    _state.update {
                        it.copy(
                            phase = VoicePhase.ERROR,
                            errorMessage = "The device voice does not support this language"
                        )
                    }
                    return@onMain
                }

                engine.setSpeechRate(0.94f)
                engine.setPitch(1.0f)
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = onMain {
                        _state.update { it.copy(phase = VoicePhase.SPEAKING, errorMessage = null) }
                    }

                    override fun onDone(utteranceId: String?) = onMain {
                        finishAgentSpeech(resumeHandsFree = true)
                    }

                    override fun onError(utteranceId: String?) = onMain {
                        _state.update {
                            it.copy(
                                phase = VoicePhase.ERROR,
                                errorMessage = "Wisteria could not play that response"
                            )
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
                })

                _state.update { it.copy(isTextToSpeechReady = true) }
                pendingFallbackSpeech?.let(::speakWithDeviceTts)
            }
        }
    }

    @MainThread
    private fun speakWithDeviceTts(text: String) {
        val engine = textToSpeech
        if (!_state.value.isTextToSpeechReady || engine == null) {
            pendingFallbackSpeech = text
            ensureTextToSpeech()
            return
        }

        pendingFallbackSpeech = null
        _state.update { it.copy(phase = VoicePhase.SPEAKING, errorMessage = null) }
        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "wisteria-${UUID.randomUUID()}"
        )
        if (result == TextToSpeech.ERROR) {
            _state.update {
                it.copy(
                    phase = VoicePhase.ERROR,
                    errorMessage = "Wisteria could not start voice playback"
                )
            }
        }
    }

    @MainThread
    private fun finishAgentSpeech(resumeHandsFree: Boolean) {
        if (shouldEndCallAfterSpeech) {
            shouldEndCallAfterSpeech = false
            endCall()
            return
        }
        _state.update { it.copy(phase = VoicePhase.IDLE, audioLevel = 0f) }
        if (
            resumeHandsFree &&
            _state.value.isCallActive &&
            _state.value.handsFreeEnabled &&
            !_state.value.isMicMuted
        ) {
            mainHandler.postDelayed(
                { if (_state.value.isCallActive) startListeningInternal() },
                450L
            )
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _state.update { it.copy(phase = VoicePhase.LISTENING, errorMessage = null) }
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _state.update { it.copy(audioLevel = normalized) }
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        _state.update { it.copy(phase = VoicePhase.PROCESSING, audioLevel = 0f) }
    }

    override fun onError(error: Int) {
        recognitionInProgress = false
        if (suppressNextRecognitionError) {
            suppressNextRecognitionError = false
            return
        }

        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that—tap the mic when you're ready"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice mode"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy—try again in a moment"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition could not reach its service"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Speech recognition is unavailable for this language"
            else -> "Voice recognition paused—tap the mic to try again"
        }
        val phase = if (
            error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        ) {
            VoicePhase.IDLE
        } else {
            VoicePhase.ERROR
        }
        _state.update {
            it.copy(
                phase = phase,
                errorMessage = message,
                partialTranscript = "",
                audioLevel = 0f
            )
        }
    }

    override fun onResults(results: Bundle?) {
        recognitionInProgress = false
        val transcript = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (transcript.isBlank()) {
            onError(SpeechRecognizer.ERROR_NO_MATCH)
            return
        }

        voiceTurnAwaitingResponse = true
        _state.update {
            it.copy(
                phase = VoicePhase.PROCESSING,
                partialTranscript = "",
                lastHeardText = transcript,
                errorMessage = null,
                audioLevel = 0f
            )
        }
        onTranscriptReady(transcript)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val partial = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (partial.isNotBlank()) {
            _state.update { it.copy(partialTranscript = partial) }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
