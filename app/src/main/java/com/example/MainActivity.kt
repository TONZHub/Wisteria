package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.example.ui.screens.ArchitectureScreen
import com.example.ui.screens.DailyCheckInAgentScreen
import com.example.ui.screens.DailySummaryScreen
import com.example.ui.screens.RhythmMemoryScreen
import com.example.ui.screens.VoiceCallScreen
import com.example.ui.components.FullScreenCheckInDialog
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OffCoral
import com.example.ui.viewmodel.DailyCheckInViewModel
import com.example.ui.viewmodel.ThemeMode
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

enum class WisteriaTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DAILY_PULSE("Check-In", Icons.Default.Spa),
    INSIGHTS("Insights", Icons.Default.AutoAwesome),
    RHYTHM_CARE("Rhythm & Care", Icons.Default.CalendarMonth)
}

private enum class VoiceStartAction {
    DICTATION,
    CALL
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: DailyCheckInViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Wisteria", "MainActivity: onCreate")
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        configureFirebaseAppCheck(this)
        enableEdgeToEdge()
        
        viewModel = androidx.lifecycle.ViewModelProvider(this)[DailyCheckInViewModel::class.java]
        
        val triggerTakeover = intent.getBooleanExtra("trigger_takeover", false)
        Log.d("Wisteria", "MainActivity: triggerTakeover=$triggerTakeover")
        if (triggerTakeover) {
            viewModel.openFullScreenTakeover()
        }
        
        setContent {
            WisteriaMainApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val triggerTakeover = intent.getBooleanExtra("trigger_takeover", false)
        Log.d("Wisteria", "MainActivity: onNewIntent, triggerTakeover=$triggerTakeover")
        if (triggerTakeover) {
            viewModel.openFullScreenTakeover()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WisteriaMainApp(
    viewModel: DailyCheckInViewModel,
    startWithTakeover: Boolean = false
) {
    val context = LocalContext.current
    
    // Use startWithTakeover only for initial launch if provided via parameter
    // otherwise the ViewModel's state handles it via openFullScreenTakeover() calls.
    LaunchedEffect(startWithTakeover) {
        if (startWithTakeover) {
            viewModel.openFullScreenTakeover()
        }
    }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    
    val isDark = when (uiState.themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MyApplicationTheme(darkTheme = isDark) {
        val latestCheckIn by viewModel.latestSavedCheckIn.collectAsState()
        val careActions by viewModel.careActions.collectAsState()
        val memories by viewModel.agentMemories.collectAsState()

        // Full-screen takeover at the top level so it shows over any tab
        FullScreenCheckInDialog(
            isOpen = uiState.isFullScreenTakeoverActive,
            isOffDay = uiState.latestPulse.isOffDay,
            onDismiss = { viewModel.closeFullScreenTakeover() },
            onSubmitInput = viewModel::submitSingleInputCheckIn
        )

        var currentTab by remember { mutableStateOf(WisteriaTab.DAILY_PULSE) }
        var showInfoDialog by remember { mutableStateOf(false) }
        var showArchitecture by remember { mutableStateOf(false) }

        val healthLauncher = rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) {
            viewModel.onHealthPermissionsResult()
        }

        val notificationLauncher = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            // Handle notification permission result if needed
        }

        var pendingVoiceAction by remember { mutableStateOf<VoiceStartAction?>(null) }
        val performVoiceAction: (VoiceStartAction) -> Unit = { action ->
            when (action) {
                VoiceStartAction.DICTATION -> viewModel.startVoiceInput()
                VoiceStartAction.CALL -> viewModel.startVoiceCall()
            }
        }
        val microphonePermissionLauncher = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            val action = pendingVoiceAction
            pendingVoiceAction = null
            if (granted && action != null) {
                performVoiceAction(action)
            } else if (!granted) {
                viewModel.onMicrophonePermissionDenied()
            }
        }
        val requestVoiceAction: (VoiceStartAction) -> Unit = { action ->
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                performVoiceAction(action)
            } else {
                pendingVoiceAction = action
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        LaunchedEffect(Unit) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val onConnectHealth = {
            if (uiState.isHealthAvailable) {
                healthLauncher.launch(viewModel.getHealthPermissions())
            } else {
                context.startActivity(viewModel.getHealthInstallIntent())
            }
        }

        val onSignInWithGoogle: () -> Unit = {
            scope.launch {
                val credentialManager = CredentialManager.create(context)
                val webClientId = "635342872362-5i3f5hjvtogukt0l3f27cv981r1f3hvo.apps.googleusercontent.com"
                
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()

                try {
                    val result = credentialManager.getCredential(
                        request = request,
                        context = context,
                    )
                    val credential = result.credential
                    
                    if (credential is androidx.credentials.CustomCredential && 
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        viewModel.signInWithGoogle(googleIdTokenCredential.idToken)
                    } else {
                        viewModel.setStatusMessage("Unexpected credential type: ${credential.type}")
                    }
                } catch (e: GetCredentialException) {
                    Log.e("Wisteria", "Google Sign-In failed", e)
                    viewModel.setStatusMessage("Sign-in error: ${e.message ?: "check Web Client ID and SHA-1"}")
                } catch (e: Exception) {
                    Log.e("Wisteria", "Unexpected Sign-In error", e)
                    viewModel.setStatusMessage("Error: ${e.message ?: "unknown"}")
                }
            }
        }

        if (showArchitecture) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("How Wisteria Works", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
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
                ArchitectureScreen(
                    uiState = uiState,
                    memories = memories,
                    onLoadDemoHistory = { viewModel.loadDemoHistory() },
                    onRunNightShift = { viewModel.runNightShift() },
                    onTriggerFirestoreSync = { viewModel.triggerFirestoreSync() },
                    onSignInWithGoogle = onSignInWithGoogle,
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
                            Text("About Wisteria")
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Built for low-bandwidth days.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "• Check in with a number or one everyday word\n" +
                                        "• Speak a turn or open an in-app voice call\n" +
                                        "• Keep bright, steady, heavy, and off days separate\n" +
                                        "• Notice only patterns that appear in your own history\n" +
                                        "• Store locally first; Firebase features are optional\n\n" +
                                        "Wisteria reflects what you enter. It never assigns a body phase or cause.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Appearance",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeChip(
                                    label = "Auto",
                                    isSelected = uiState.themeMode == ThemeMode.AUTO,
                                    onClick = { viewModel.setThemeMode(ThemeMode.AUTO) }
                                )
                                ThemeChip(
                                    label = "Light",
                                    isSelected = uiState.themeMode == ThemeMode.LIGHT,
                                    onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                                )
                                ThemeChip(
                                    label = "Dark",
                                    isSelected = uiState.themeMode == ThemeMode.DARK,
                                    onClick = { viewModel.setThemeMode(ThemeMode.DARK) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    showInfoDialog = false
                                    showArchitecture = true
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("See How It Works", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
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
                            val isOffDay = latestCheckIn?.isOffDay ?: uiState.latestPulse.isOffDay
                            val hasLearnedPattern = (uiState.morningBrief?.learnedTransitionCount ?: 0) > 0
                            val badgeColor = if (isOffDay) OffCoral else ForestGreenMint

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
                                        text = when {
                                            isOffDay -> "Feels off"
                                            hasLearnedPattern -> "Pattern noticed"
                                            else -> "Learning"
                                        },
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
                },
                snackbarHost = {
                    if (uiState.agentStatusMessage.isNotEmpty() && uiState.agentStatusMessage != "Ready for a check-in") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable { viewModel.setStatusMessage("Ready for a check-in") },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (uiState.agentStatusMessage.contains("failed", ignoreCase = true) || 
                                                     uiState.agentStatusMessage.contains("error", ignoreCase = true)) 
                                                     Icons.Default.Info else Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = uiState.agentStatusMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
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
                            onCloseTakeover = { viewModel.closeFullScreenTakeover() },
                            voiceState = voiceState,
                            onStartVoiceInput = { requestVoiceAction(VoiceStartAction.DICTATION) },
                            onStopVoiceInput = { viewModel.stopVoiceInput() },
                            onStartCall = { requestVoiceAction(VoiceStartAction.CALL) }
                        )
                        WisteriaTab.INSIGHTS -> DailySummaryScreen(
                        uiState = uiState,
                        latestCheckIn = latestCheckIn,
                        careActions = careActions,
                        onToggleCareAction = { id, done -> viewModel.toggleCareAction(id, done) },
                        onRunNightShift = { viewModel.runNightShift() },
                        onTriggerFirestoreSync = { viewModel.triggerFirestoreSync() },
                        onSignInWithGoogle = onSignInWithGoogle,
                        onSignOut = { viewModel.signOut() },
                        onConnectHealth = onConnectHealth,
                        onSetReminder = { h, m -> viewModel.setReminder(h, m) },
                        onDisableReminder = { viewModel.disableReminder() }
                    )
                        WisteriaTab.RHYTHM_CARE -> RhythmMemoryScreen(
                            uiState = uiState,
                            memories = memories
                        )
                    }
                }
            }
        }

        if (voiceState.isCallActive) {
            VoiceCallScreen(
                state = voiceState,
                onToggleListening = { viewModel.toggleVoiceListening() },
                onToggleHandsFree = { viewModel.toggleVoiceHandsFree() },
                onToggleMicMuted = { viewModel.toggleVoiceMicMuted() },
                onToggleSpeaker = { viewModel.toggleVoiceSpeaker() },
                onEndCall = { viewModel.endVoiceCall() }
            )
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
