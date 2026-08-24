package com.example.voice

enum class VoicePhase {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

data class VoiceConversationState(
    val isCallActive: Boolean = false,
    val phase: VoicePhase = VoicePhase.IDLE,
    val partialTranscript: String = "",
    val lastHeardText: String = "",
    val lastAgentText: String = "",
    val errorMessage: String? = null,
    val handsFreeEnabled: Boolean = true,
    val isMicMuted: Boolean = false,
    val isSpeakerEnabled: Boolean = true,
    val audioLevel: Float = 0f,
    val isRecognitionAvailable: Boolean = true,
    val isTextToSpeechReady: Boolean = false,
    val usesOnDeviceRecognition: Boolean = false
) {
    val statusLabel: String
        get() = when {
            isMicMuted -> "Microphone muted"
            errorMessage != null -> errorMessage
            phase == VoicePhase.LISTENING -> "Listening…"
            phase == VoicePhase.PROCESSING -> "Wisteria is thinking…"
            phase == VoicePhase.SPEAKING -> "Wisteria is speaking…"
            isCallActive && handsFreeEnabled -> "Ready for the next turn"
            isCallActive -> "Tap the center button to speak"
            else -> "Voice ready"
        }

    val heardCaption: String
        get() = partialTranscript.ifBlank { lastHeardText }
}
