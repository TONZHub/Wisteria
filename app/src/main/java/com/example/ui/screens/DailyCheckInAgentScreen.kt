package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CareActionEntity
import com.example.domain.agent.model.AgentMessage
import com.example.domain.agent.model.MessageSender
import com.example.ui.components.FullScreenCheckInDialog
import com.example.ui.theme.OffCoral
import com.example.ui.theme.ForestGreenAccent
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.ForestGreenSage
import com.example.ui.theme.HeavyRose
import com.example.ui.theme.WisteriaLavender
import com.example.ui.viewmodel.CheckInUiState
import com.example.voice.VoiceConversationState
import com.example.voice.VoicePhase

@Composable
fun DailyCheckInAgentScreen(
    uiState: CheckInUiState,
    careActions: List<CareActionEntity>,
    onSendMessage: (String) -> Unit,
    onQuickOption: (Int, String) -> Unit,
    onToggleCareAction: (String, Boolean) -> Unit,
    onOpenTakeover: () -> Unit,
    onCloseTakeover: () -> Unit,
    voiceState: VoiceConversationState,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onStartCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("daily_checkin_agent_screen")
    ) {
        // Fast 3-Second Tap Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "How are you feeling today?",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Tap once (1–5)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ForestGreenMint,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = onStartCall,
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.testTag("start_voice_call_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call Wisteria",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Call",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5 Big Inviting Tactile Buttons for 3-Second Logging
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickPulseButton(
                        rating = 1,
                        icon = Icons.Default.Bolt,
                        label = "Off",
                        bgColor = OffCoral.copy(alpha = 0.22f),
                        activeColor = OffCoral,
                        modifier = Modifier.weight(1f).testTag("chip_rating_1"),
                        onClick = { onQuickOption(1, "Off") }
                    )
                    QuickPulseButton(
                        rating = 2,
                        icon = Icons.Default.Cloud,
                        label = "Heavy",
                        bgColor = HeavyRose.copy(alpha = 0.22f),
                        activeColor = HeavyRose,
                        modifier = Modifier.weight(1f).testTag("chip_rating_2"),
                        onClick = { onQuickOption(2, "Heavy") }
                    )
                    QuickPulseButton(
                        rating = 3,
                        icon = Icons.Default.Eco,
                        label = "Steady",
                        bgColor = ForestGreenAccent.copy(alpha = 0.22f),
                        activeColor = ForestGreenMint,
                        modifier = Modifier.weight(1f).testTag("chip_rating_3"),
                        onClick = { onQuickOption(3, "Steady") }
                    )
                    QuickPulseButton(
                        rating = 4,
                        icon = Icons.Default.AutoAwesome,
                        label = "Clear",
                        bgColor = ForestGreenSage.copy(alpha = 0.22f),
                        activeColor = ForestGreenSage,
                        modifier = Modifier.weight(1f).testTag("chip_rating_4"),
                        onClick = { onQuickOption(4, "Clear") }
                    )
                    QuickPulseButton(
                        rating = 5,
                        icon = Icons.Default.Favorite,
                        label = "Bright",
                        bgColor = WisteriaLavender.copy(alpha = 0.25f),
                        activeColor = WisteriaLavender,
                        modifier = Modifier.weight(1f).testTag("chip_rating_5"),
                        onClick = { onQuickOption(5, "Bright") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick everyday texture chips.
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        QuickPill(
                            icon = Icons.Default.Spa,
                            label = "Rested",
                            onClick = { onSendMessage("Feeling rested") },
                            testTag = "chip_rest_hydration"
                        )
                    }
                    item {
                        QuickPill(
                            icon = Icons.Default.WaterDrop,
                            label = "Feeling Off",
                            onClick = { onSendMessage("I feel off") },
                            testTag = "chip_feeling_off"
                        )
                    }
                    item {
                        QuickPill(
                            icon = Icons.Default.Shield,
                            label = "Need Quiet",
                            onClick = { onSendMessage("Today feels heavy; I need quiet") },
                            testTag = "chip_shield"
                        )
                    }
                }
            }
        }

        // Active Care Actions Tray (when active)
        if (careActions.isNotEmpty()) {
            val pendingCare = careActions.filter { !it.isCompleted }
            if (pendingCare.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = ForestGreenMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Optional Ideas Saved for You",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        pendingCare.take(2).forEach { action ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleCareAction(action.id, !action.isCompleted) }
                                    .padding(vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = if (action.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (action.isCompleted) ForestGreenMint else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = action.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Serene Conversation Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                CalmMessageBubble(message = message)
            }

            if (uiState.isAgentActive) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.agentStatusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !voiceState.isCallActive &&
                (voiceState.phase != VoicePhase.IDLE || voiceState.errorMessage != null),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            VoiceInputBanner(state = voiceState)
        }

        // Bottom Input Field (For typing or tap-to-speak)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isListening =
                    !voiceState.isCallActive && voiceState.phase == VoicePhase.LISTENING
                val voiceInputEnabled =
                    !uiState.isAgentActive &&
                        voiceState.phase != VoicePhase.PROCESSING &&
                        voiceState.phase != VoicePhase.SPEAKING
                IconButton(
                    onClick = if (isListening) onStopVoiceInput else onStartVoiceInput,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening) ForestGreenMint else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("voice_input_button"),
                    enabled = voiceInputEnabled
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isListening) "Finish voice input" else "Speak to Wisteria",
                        tint = if (isListening) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Type a single word, emoji, or note...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("checkin_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("send_message_button"),
                    enabled = inputText.isNotBlank() && !uiState.isAgentActive
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceInputBanner(state: VoiceConversationState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("voice_input_status"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.phase == VoicePhase.LISTENING) Icons.Default.Mic else Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.statusLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                val caption = state.heardCaption
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPulseButton(
    rating: Int,
    icon: ImageVector,
    label: String,
    bgColor: Color,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        modifier = modifier.height(58.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = rating.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Composable
private fun QuickPill(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
fun CalmMessageBubble(
    message: AgentMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == MessageSender.USER
    var showTrace by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 3.dp)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Wisteria",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .testTag(if (isUser) "user_message_card" else "agent_message_card")
                .clickable(enabled = !isUser && !message.thoughtTrace.isNullOrEmpty()) { 
                    showTrace = !showTrace 
                }
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                        fontSize = 14.sp
                    )
                )

                if (!isUser && (message.runtimeTrace != null || message.toolInvocations.isNotEmpty())) {
                    val checkInSaved = message.toolInvocations.any {
                        it.toolName == "RecordSingleInputCheckInTool" && it.status == "SUCCESS"
                    }
                    val careIdeasSaved = message.toolInvocations.count {
                        it.toolName == "TriggerProactiveCareActionTool" && it.status == "SUCCESS"
                    }
                    val checkInFailed = message.toolInvocations.any {
                        it.toolName == "RecordSingleInputCheckInTool" && it.status != "SUCCESS"
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.runtimeTrace?.let { runtime ->
                            TurnReceipt(text = "✓ ${runtime.framework} · ${runtime.model}")
                        }
                        if (checkInSaved) {
                            TurnReceipt(text = "✓ Check-in saved once")
                        }
                        if (careIdeasSaved > 0) {
                            TurnReceipt(text = "✓ $careIdeasSaved optional ideas saved locally")
                        }
                        if (checkInFailed) {
                            TurnReceipt(text = "Check-in could not be saved")
                        }
                    }
                }

                if (!isUser && showTrace && !message.thoughtTrace.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color.Black.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = message.thoughtTrace!!,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnReceipt(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
