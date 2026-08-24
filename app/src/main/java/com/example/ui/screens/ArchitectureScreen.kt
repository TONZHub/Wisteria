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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
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
import com.example.data.local.entity.AgentMemoryEntity
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.ForestGreenSage
import com.example.ui.theme.HeavyRose
import com.example.ui.theme.WisteriaLavender
import com.example.ui.theme.WisteriaSoftLilac
import com.example.ui.viewmodel.CheckInUiState

@Composable
fun ArchitectureScreen(
    uiState: CheckInUiState,
    memories: List<AgentMemoryEntity>,
    onLoadDemoHistory: () -> Unit,
    onRunNightShift: () -> Unit,
    onTriggerFirestoreSync: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("architecture_screen"),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Spa,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "How Wisteria Works",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Local-first Android · ADK + Firebase optional",
                                style = MaterialTheme.typography.bodySmall.copy(color = ForestGreenMint)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Type, tap, or speak a check-in to the same agent loop. Room saves the everyday texture on this device, while Night Shift learns only from heavy-to-off stretches that actually appear in your history.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }

        item {
            ArchitectureCard(title = "Submission architecture") {
                val rows = listOf(
                    Triple("Record check-in", "Room stores the rating and everyday texture you chose.", ForestGreenMint),
                    Triple("Talk to Wisteria", "Tap-to-speak and in-app calls route each transcript through the same agent, tools, and local record.", WisteriaLavender),
                    Triple("Offer care ideas", "Suggestions stay inside Wisteria; no alerts, tasks, settings, or contacts are changed.", WisteriaLavender),
                    Triple("Run Night Shift", "A user-triggered on-device analyzer learns from local history with sample-based confidence.", HeavyRose),
                    Triple("Keep the conversation", "Google ADK Kotlin holds the in-memory dialogue session and asks Firebase AI Logic for concise wording.", ForestGreenSage),
                    Triple("Gate every action", "The deterministic local router and allowlist remain final; ADK cannot authorize a write by itself.", WisteriaLavender),
                    Triple("Sync timeline", "A separate button opts into Firestore with Google Sign-In for persistence.", ForestGreenMint)
                )
                rows.forEach { (name, description, color) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 5.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }
            }
        }

        item {
            ArchitectureCard(title = "Live prototype state") {
                OutlinedButton(
                    onClick = onLoadDemoHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("load_demo_history_button")
                ) {
                    Text("Load 10 clearly labeled demo days", fontSize = 11.sp)
                }
                Text(
                    text = "Samples stay on this device and are never synced automatically.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = if (uiState.isUserLoggedIn) onTriggerFirestoreSync else onSignInWithGoogle,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_firestore_architecture_button")
                    ) {
                        Icon(
                            if (uiState.isUserLoggedIn) Icons.Default.CloudDone else Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (uiState.isUserLoggedIn) "Sync Firestore" else "Sign in & Sync",
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onRunNightShift,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("run_night_shift_architecture_button")
                    ) {
                        Icon(Icons.Default.NightsStay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run locally", fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = WisteriaSoftLilac,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Room: on-device\n" +
                                "Night Shift: on-device (${uiState.nightShiftRuns.size} run(s))\n" +
                                "Agent runtime: Google ADK Kotlin 0.8.0\n" +
                                "ADK session: in-memory, reset with conversation\n" +
                                "Firestore: optional (${uiState.firestoreSyncLogs.size} sync(s))\n" +
                                "Pattern memories: ${memories.size}\n" +
                                "Firebase AI Logic: Gemini 3.5 Flash, App Check protected",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = WisteriaSoftLilac,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchitectureCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
