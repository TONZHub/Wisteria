package com.example.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceConversationStateTest {
    @Test
    fun `call status follows the listen think speak turn`() {
        val call = VoiceConversationState(isCallActive = true)

        assertEquals("Ready for the next turn", call.statusLabel)
        assertEquals("Listening…", call.copy(phase = VoicePhase.LISTENING).statusLabel)
        assertEquals("Wisteria is thinking…", call.copy(phase = VoicePhase.PROCESSING).statusLabel)
        assertEquals("Wisteria is speaking…", call.copy(phase = VoicePhase.SPEAKING).statusLabel)
    }

    @Test
    fun `partial speech takes precedence in live captions`() {
        val state = VoiceConversationState(
            partialTranscript = "today feels",
            lastHeardText = "an older turn"
        )

        assertEquals("today feels", state.heardCaption)
        assertEquals("an older turn", state.copy(partialTranscript = "").heardCaption)
    }

    @Test
    fun `muted microphone is always visible in status`() {
        val state = VoiceConversationState(
            isCallActive = true,
            phase = VoicePhase.LISTENING,
            isMicMuted = true
        )

        assertEquals("Microphone muted", state.statusLabel)
    }
}
