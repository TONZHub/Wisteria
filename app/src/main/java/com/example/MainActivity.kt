package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AdkArchitectureScreen
import com.example.ui.screens.CyclePatternMemoryScreen
import com.example.ui.screens.DailyCheckInAgentScreen
import com.example.ui.screens.DailySummaryScreen
import com.example.ui.theme.DropCoral
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DailyCheckInViewModel

enum class WisteriaTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DAILY_PULSE("Check-In", Icons.Default.Spa),
    INSIGHTS("Insights", Icons.Default.AutoAwesome),
    CYCLE_RHYTHM("Cycle & Care", Icons.Default.CalendarMonth)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                WisteriaMainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WisteriaMainApp(
    viewModel: DailyCheckInViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val latestCheckIn by viewModel.latestSavedCheckIn.collectAsState()
    val careActions by viewModel.careActions.collectAsState()
    val memories by viewModel.agentMemories.collectAsState()

    var currentTab by remember { mutableStateOf(WisteriaTab.DAILY_PULSE) }
    var showInfoDialog by remember { mutableStateOf(value = false) }
    var showArchitecture by remember { mutableStateOf(false) }

    if (showArchitecture) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Agent Architecture", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                    navigationIcon = {
                        TextButton(onClick = { showArchitecture = false }) {
                            Text("Back", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            AdkArchitectureScreen(
                uiState = uiState,
                memories = memories,
                onTriggerCloudRun = { viewModel.triggerCloudRunWorkflow() },
                onTriggerFirestoreSync = { viewModel.triggerFirestoreSync() },
                modifier = Modifier.padding(innerPadding)
            )
        }
    } else {
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Spa,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wisteria Architecture")
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Built for people with PMDD and low bandwidth.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "• AI Model: Gemini 3.5 Flash via Google ADK\n" +
                                    "• Architecture: Local Room persistence + Cloud Firestore Sync\n" +
                                    "• Pattern Rule: Calibrates to irregular spotting & psych meds drop windows\n" +
                                    "• Zero Guilt: Done in 3 seconds every day.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        )
                        TextButton(
                            onClick = {
                                showInfoDialog = false
                                showArchitecture = true
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("View Full Architecture Spec", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("wisteria_main_scaffold"),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Spa,
                                    contentDescription = "Wisteria",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Wisteria",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    },
                    actions = {
                        val isPmdd = latestCheckIn?.isPmddWindowActive ?: uiState.latestPulse.isPmddWindowActive
                        val badgeColor = if (isPmdd) DropCoral else ForestGreenMint

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeColor.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isPmdd) "PMDD Pre-Warn" else "Calibrated",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About Wisteria",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.testTag("wisteria_bottom_nav")
                ) {
                    WisteriaTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = ForestGreenMint,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    WisteriaTab.DAILY_PULSE -> DailyCheckInAgentScreen(
                        uiState = uiState,
                        careActions = careActions,
                        onSendMessage = { prompt -> viewModel.sendMessage(prompt) },
                        onQuickOption = { rating, label -> viewModel.sendQuickOption(rating, label) },
                        onToggleCareAction = { id, done -> viewModel.toggleCareAction(id, done) },
                        onOpenTakeover = { viewModel.openFullScreenTakeover() },
                        onCloseTakeover = { viewModel.closeFullScreenTakeover() }
                    )
                    WisteriaTab.INSIGHTS -> DailySummaryScreen(
                        uiState = uiState,
                        latestCheckIn = latestCheckIn,
                        careActions = careActions,
                        onToggleCareAction = { id, done -> viewModel.toggleCareAction(id, done) },
                        onOpenTakeover = { viewModel.openFullScreenTakeover() },
                        onTriggerCloudRun = { viewModel.triggerCloudRunWorkflow() },
                        onTriggerFirestoreSync = { viewModel.triggerFirestoreSync() }
                    )
                    WisteriaTab.CYCLE_RHYTHM -> CyclePatternMemoryScreen(
                        uiState = uiState,
                        memories = memories
                    )
                }
            }
        }
    }
}
