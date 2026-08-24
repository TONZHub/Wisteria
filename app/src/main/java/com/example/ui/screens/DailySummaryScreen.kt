package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.CircularProgressIndicator
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.DailyCheckInEntity
import com.example.domain.agent.MorningBrief
import com.example.domain.agent.model.DailyTexture
import com.example.ui.components.CareActionRow
import com.example.ui.components.DailyTextureGauge
import com.example.ui.theme.OffCoral
import com.example.ui.theme.ForestGreenAccent
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.ForestGreenSage
import com.example.ui.theme.HeavyRose
import com.example.ui.theme.WisteriaLavender
import com.example.ui.viewmodel.CheckInUiState

@Composable
fun DailySummaryScreen(
    uiState: CheckInUiState,
    latestCheckIn: DailyCheckInEntity?,
    careActions: List<CareActionEntity>,
    onToggleCareAction: (String, Boolean) -> Unit,
    onRunNightShift: () -> Unit,
    onTriggerFirestoreSync: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onConnectHealth: () -> Unit,
    onSetReminder: (Int, Int) -> Unit,
    onDisableReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse = uiState.latestPulse
    val rating = latestCheckIn?.ratingValue ?: pulse.ratingValue
    val detectedTexture = try {
        DailyTexture.valueOf(latestCheckIn?.detectedTexture ?: pulse.texture.name)
    } catch (e: Exception) {
        pulse.texture
    }
    val isOffDay = latestCheckIn?.isOffDay ?: pulse.isOffDay

    val context = LocalContext.current
    
    val openTimePicker = {
        val initialHour = uiState.reminderHour ?: 9
        val initialMinute = uiState.reminderMinute ?: 0
        TimePickerDialog(
            context,
            { _, hour, minute -> onSetReminder(hour, minute) },
            initialHour,
            initialMinute,
            false // 12-hour view with AM/PM
        ).show()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("daily_summary_screen"),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Night Shift Morning Brief
        item {
            MorningBriefCard(
                brief = uiState.morningBrief,
                isRunning = uiState.isNightShiftRunning,
                onRunNightShift = onRunNightShift
            )
        }

        // Today's everyday texture.
        item {
            DailyTextureGauge(
                texture = detectedTexture,
                rating = rating,
                isOffDay = isOffDay
            )
        }

        // Everyday textures saved from the person's own check-ins.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("everyday_textures_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Spa,
                                contentDescription = null,
                                tint = ForestGreenMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Your Everyday Textures",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "Your words only",
                            style = MaterialTheme.typography.labelSmall.copy(color = ForestGreenSage, fontSize = 10.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.textureSummary.isNotEmpty()) {
                            uiState.textureSummary.forEach { entry ->
                                val title = entry["textureTitle"] as? String ?: "Everyday texture"
                                val desc = entry["patternSignal"] as? String ?: ""
                                
                                val textureColor = when {
                                    title.contains("Heavy") -> HeavyRose
                                    title.contains("Off") -> OffCoral
                                    title.contains("Bright") -> ForestGreenMint
                                    else -> WisteriaLavender
                                }
                                
                                val isActive = when {
                                    title.contains("Heavy") -> detectedTexture == DailyTexture.HEAVY
                                    title.contains("Steady") -> detectedTexture == DailyTexture.STEADY
                                    title.contains("Bright") -> detectedTexture == DailyTexture.BRIGHT
                                    title.contains("Off") -> detectedTexture == DailyTexture.OFF || isOffDay
                                    else -> detectedTexture == DailyTexture.UNKNOWN
                                }

                                TextureRow(title, desc, textureColor, isActive)
                            }
                        } else {
                            TextureRow("Bright", "Clear, good, alive, or bright", ForestGreenMint, detectedTexture == DailyTexture.BRIGHT)
                            TextureRow("Steady", "Okay, fine, managing, or steady", ForestGreenSage, detectedTexture == DailyTexture.STEADY)
                            TextureRow("Heavy", "Tired, foggy, hard, or heavy", HeavyRose, detectedTexture == DailyTexture.HEAVY)
                            TextureRow("Off", "Off, awful, or crashed", WisteriaLavender, detectedTexture == DailyTexture.OFF || isOffDay)
                        }
                    }
                }
            }
        }

        // Optional ideas remain inside Wisteria until the person chooses one.
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = ForestGreenMint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Low-Effort Ideas",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = "${careActions.count { it.isCompleted }} / ${careActions.size}",
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        if (careActions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Daily Reminder",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.isReminderEnabled && uiState.reminderHour != null && uiState.reminderMinute != null) 
                                "Scheduled for ${formatTime(uiState.reminderHour, uiState.reminderMinute)}"
                            else 
                                "Not scheduled. Tap to set a daily check-in time.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.clickable { openTimePicker() }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(9 to 0, 12 to 0, 18 to 0).forEach { (h, m) ->
                                OutlinedButton(
                                    onClick = { onSetReminder(h, m) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (uiState.reminderHour == h && uiState.reminderMinute == m)
                                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    else
                                        null
                                ) {
                                    Text(formatTime(h, m), fontSize = 10.sp)
                                }
                            }
                            
                            OutlinedButton(
                                onClick = { openTimePicker() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(4.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = if (uiState.reminderHour != null && listOf(9, 12, 18).none { it == uiState.reminderHour } || 
                                          (uiState.reminderHour != null && uiState.reminderMinute != 0))
                                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                else
                                    null
                            ) {
                                Text("Set...", fontSize = 10.sp)
                            }
                        }
                        
                        if (uiState.isReminderEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onDisableReminder,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Disable Reminders", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else {
            items(careActions, key = { it.id }) { action ->
                CareActionRow(
                    item = action,
                    onToggle = { isCompleted -> onToggleCareAction(action.id, isCompleted) }
                )
            }
        }

        // Local storage and explicit, optional Firestore sync.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_integration_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Storage & Optional Sync",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Firestore Row
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Firestore",
                                    tint = ForestGreenMint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (uiState.isUserLoggedIn) "Google Account" else "Google Cloud Firestore",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    if (uiState.isUserLoggedIn && uiState.userEmail != null) {
                                        Text(
                                            text = uiState.userEmail,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = ForestGreenMint,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }

                            if (uiState.isUserLoggedIn) {
                                OutlinedButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.testTag("sign_out_button"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Sign Out", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (uiState.isUserLoggedIn) {
                            Button(
                                onClick = onTriggerFirestoreSync,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sync_firestore_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Daily Timeline", fontSize = 13.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onSignInWithGoogle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sign_in_google_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Simple representation of Google "G" using a colored Box if no asset is available
                                    Surface(
                                        modifier = Modifier.size(18.dp),
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Sign in with Google",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }
                        
                        if (uiState.isUserLoggedIn) {
                            Text(
                                text = latestCheckIn?.firestoreDocPath ?: "Not synced today · persistent storage",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Text(
                                text = "Not signed in · Google sign-in required for sync",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Night Shift Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NightsStay,
                                contentDescription = "Night Shift Worker",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Night Shift Worker",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "on-device · patterns only",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Health Connect Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Health Connect",
                                tint = OffCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Health Connect integration",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = uiState.healthConnectionSummary,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.isHealthConnected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Connected",
                                    tint = ForestGreenMint,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            OutlinedButton(
                                onClick = onConnectHealth,
                                modifier = Modifier.testTag("manage_health_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (uiState.isHealthConnected) "Manage" else "Connect", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextureRow(
    title: String,
    desc: String,
    color: Color,
    isActive: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) color else MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MorningBriefCard(
    brief: MorningBrief?,
    isRunning: Boolean,
    onRunNightShift: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("morning_brief_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Night Shift",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = onRunNightShift,
                    enabled = !isRunning,
                    modifier = Modifier.testTag("run_night_shift_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isRunning) "Running..." else "Run Night Shift",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isRunning -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Reading check-in history and detecting your pattern...",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
                brief == null -> {
                    Text(
                        text = "Night Shift hasn't run yet. It reads local check-ins and looks for repeating heavy-to-off stretches.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                else -> {
                    Text(
                        text = brief.headline,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = brief.body,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${brief.sampleSize} day(s) read" +
                            (brief.daysUntilOff?.let { " · off may be ~${it}d away" } ?: " · still learning"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ForestGreenSage,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    if (brief.traces.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                brief.traces.forEach { trace ->
                                    Text(
                                        text = "${trace.toolName}(${trace.args.entries.joinToString { "${it.key}=${it.value}" }}) → ${trace.result}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, amPm)
}
