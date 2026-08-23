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
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.CircularProgressIndicator
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.DailyCheckInEntity
import com.example.domain.agent.MorningBrief
import com.example.domain.agent.model.CycleTexture
import com.example.ui.components.CareActionRow
import com.example.ui.components.CycleTextureGauge
import com.example.ui.theme.DropCoral
import com.example.ui.theme.ForestGreenAccent
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.ForestGreenSage
import com.example.ui.theme.SpottingRose
import com.example.ui.theme.WisteriaLavender
import com.example.ui.viewmodel.CheckInUiState

@Composable
fun DailySummaryScreen(
    uiState: CheckInUiState,
    latestCheckIn: DailyCheckInEntity?,
    careActions: List<CareActionEntity>,
    onToggleCareAction: (String, Boolean) -> Unit,
    onOpenTakeover: () -> Unit,
    onRunNightShift: () -> Unit,
    onTriggerFirestoreSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse = uiState.latestPulse
    val rating = latestCheckIn?.ratingValue ?: pulse.ratingValue
    val detectedTexture = try {
        CycleTexture.valueOf(latestCheckIn?.detectedTexture ?: pulse.texture.name)
    } catch (e: Exception) {
        pulse.texture
    }
    val isPmddActive = latestCheckIn?.isPmddWindowActive ?: pulse.isPmddWindowActive

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

        // Hero Cycle Texture Gauge
        item {
            CycleTextureGauge(
                texture = detectedTexture,
                rating = rating,
                isPmddImminent = isPmddActive
            )
        }

        // Learned 4-Phase Irregular Cycle Blueprint Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cycle_phases_card"),
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
                                text = "Learned Body Texture",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "Not 28-day math",
                            style = MaterialTheme.typography.labelSmall.copy(color = ForestGreenSage, fontSize = 10.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.cyclePhases.isNotEmpty()) {
                            uiState.cyclePhases.forEach { phase ->
                                val title = phase["phase"] as? String ?: "Phase"
                                val desc = phase["patternSignal"] as? String ?: ""
                                
                                val phaseColor = when {
                                    title.contains("Spotting") -> SpottingRose
                                    title.contains("Period") -> DropCoral
                                    title.contains("Alive") -> ForestGreenMint
                                    else -> WisteriaLavender
                                }
                                
                                val isActive = when {
                                    title.contains("Spotting") -> detectedTexture == CycleTexture.SPOTTING_PHASE
                                    title.contains("Period") -> detectedTexture == CycleTexture.ACTUAL_PERIOD
                                    title.contains("Alive") -> detectedTexture == CycleTexture.FEELING_GOOD
                                    else -> detectedTexture == CycleTexture.MEDS_DROP_WINDOW || isPmddActive
                                }

                                PhaseRow(title, desc, phaseColor, isActive)
                            }
                        } else {
                            PhaseRow("Spotting Window", "Irregular spotting before an actual period, length learned", SpottingRose, detectedTexture == CycleTexture.SPOTTING_PHASE)
                            PhaseRow("Actual Period Flow", "True bleeding phase, length learned", DropCoral, detectedTexture == CycleTexture.ACTUAL_PERIOD)
                            PhaseRow("Alive & Optimal Window", "Feeling alive, clear & energetic", ForestGreenMint, detectedTexture == CycleTexture.FEELING_GOOD)
                            PhaseRow("Drop Window (PMDD)", "Harder window: brain fog, meds efficacy dips", WisteriaLavender, detectedTexture == CycleTexture.MEDS_DROP_WINDOW || isPmddActive)
                        }
                    }
                }
            }
        }

        // Proactive Care Actions & Pre-Crash Interventions
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
                        text = "Proactive Care & Pre-Crash Actions",
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
                            text = "No care actions queued right now. Complete a single-input check-in to trigger pre-emptive rest & low-effort comfort items!",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenTakeover,
                            modifier = Modifier.testTag("open_checkin_banner_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Open 3-Second Check-In", color = MaterialTheme.colorScheme.onPrimary)
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

        // Cloud Run & Firestore Backend Scaffold
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
                        text = "Google Cloud & Firestore Architecture",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Firestore Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Firestore",
                                tint = ForestGreenMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Google Cloud Firestore",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = latestCheckIn?.firestoreDocPath ?: "users/{uid}/cycle_timeline/today",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onTriggerFirestoreSync,
                            modifier = Modifier.testTag("sync_firestore_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Sync Timeline", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Night Shift Worker Row (informational - the "Run Night Shift" button lives on
                    // the Morning Brief card above; this is honestly local, not a fake Cloud Run host)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                text = "local://night-shift · runs in-process",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhaseRow(
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
                        text = "ACTIVE",
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
                        text = "Night Shift hasn't run yet. It reads your check-in history and learns your own spotting and drop-window length - no fixed schedule assumed.",
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
                            (brief.daysUntilDrop?.let { " · ${it}d to drop" } ?: " · still calibrating"),
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
