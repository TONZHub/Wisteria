package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.OffCoral
import com.example.ui.theme.WisteriaLavender
import com.example.voice.VoiceConversationState
import com.example.voice.VoicePhase

@Composable
fun VoiceCallScreen(
    state: VoiceConversationState,
    onToggleListening: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onToggleMicMuted: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onEndCall)

    val pulseTransition = rememberInfiniteTransition(label = "voice_orb")
    val activeScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(720),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice_orb_scale"
    )
    val orbScale = if (
        state.phase == VoicePhase.LISTENING || state.phase == VoicePhase.SPEAKING
    ) {
        activeScale
    } else {
        1f
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("voice_call_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            MaterialTheme.colorScheme.background,
                            WisteriaLavender.copy(alpha = 0.12f)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wisteria voice",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (state.usesOnDeviceRecognition) {
                    "On-device speech recognition"
                } else {
                    "Using your device's speech service"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.weight(0.7f))

            Box(
                modifier = Modifier
                    .size(184.dp)
                    .scale(orbScale),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = when (state.phase) {
                        VoicePhase.LISTENING -> ForestGreenMint.copy(alpha = 0.24f)
                        VoicePhase.SPEAKING -> WisteriaLavender.copy(alpha = 0.28f)
                        VoicePhase.ERROR -> OffCoral.copy(alpha = 0.20f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    }
                ) { }
                Surface(
                    modifier = Modifier.size(132.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 14.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = state.statusLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("voice_status_label")
            )
            Spacer(modifier = Modifier.height(12.dp))
            VoiceLevelBars(
                level = when (state.phase) {
                    VoicePhase.LISTENING -> state.audioLevel
                    VoicePhase.SPEAKING -> 0.66f
                    else -> 0.08f
                }
            )

            Spacer(modifier = Modifier.height(22.dp))
            CaptionCard(state = state)

            Spacer(modifier = Modifier.weight(1f))

            val canListen =
                !state.isMicMuted &&
                    state.phase != VoicePhase.PROCESSING
            Surface(
                onClick = onToggleListening,
                enabled = canListen,
                modifier = Modifier
                    .size(78.dp)
                    .testTag("voice_turn_button"),
                shape = CircleShape,
                color = if (state.phase == VoicePhase.LISTENING) {
                    ForestGreenMint
                } else {
                    MaterialTheme.colorScheme.primary
                },
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (state.phase == VoicePhase.LISTENING) {
                            "Finish speaking"
                        } else {
                            "Start speaking"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallControl(
                    icon = if (state.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (state.isMicMuted) "Unmute" else "Mute",
                    selected = state.isMicMuted,
                    onClick = onToggleMicMuted,
                    testTag = "voice_mute_button"
                )
                CallControl(
                    icon = if (state.isSpeakerEnabled) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.AutoMirrored.Filled.VolumeOff
                    },
                    label = "Speaker",
                    selected = state.isSpeakerEnabled,
                    onClick = onToggleSpeaker,
                    testTag = "voice_speaker_button"
                )
                CallControl(
                    icon = Icons.Default.Hearing,
                    label = "Hands-free",
                    selected = state.handsFreeEnabled,
                    onClick = onToggleHandsFree,
                    testTag = "voice_hands_free_button"
                )
                CallControl(
                    icon = Icons.Default.CallEnd,
                    label = "End",
                    selected = true,
                    selectedColor = OffCoral,
                    onClick = onEndCall,
                    testTag = "voice_end_call_button"
                )
            }
        }
    }
}

@Composable
private fun CaptionCard(state: VoiceConversationState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_captions"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Live captions",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenMint
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (state.heardCaption.isNotBlank()) {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = state.heardCaption,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 3
                )
            } else {
                Text(
                    text = "Your words will appear here while you speak.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            if (state.lastAgentText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Wisteria",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = state.lastAgentText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun VoiceLevelBars(level: Float) {
    Row(
        modifier = Modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val weights = listOf(0.55f, 0.82f, 1f, 0.74f, 0.48f)
        weights.forEach { weight ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((8f + (22f * level * weight)).dp)
                    .background(
                        color = ForestGreenMint,
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
private fun CallControl(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    selectedColor: Color? = null
) {
    val activeColor = selectedColor ?: MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(54.dp)
                .testTag(testTag),
            shape = CircleShape,
            color = if (selected) {
                activeColor
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            textAlign = TextAlign.Center
        )
    }
}
