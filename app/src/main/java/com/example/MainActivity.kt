package com.example

import android.Manifest
import android.os.Build
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.material.icons.filled.SupportAgent
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
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.example.ui.screens.ArchitectureScreen
import com.example.ui.screens.DailyCheckInAgentScreen
import com.example.ui.screens.DailySummaryScreen
import com.example.ui.screens.RhythmMemoryScreen
import com.example.ui.screens.SupportScreen
import com.example.ui.screens.VoiceCallScreen
import com.example.data.reminder.CheckInReminderManager
import com.example.ui.components.CheckInTakeoverContent
import com.example.ui.components.FullScreenCheckInDialog
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OffCoral
import com.example.ui.viewmodel.DailyCheckInViewModel
import com.example.ui.viewmodel.ThemeMode
import com.example.domain.export.CheckInExportFormatter
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

private fun Context.configuredGoogleWebClientId(): String? {
    val resourceId = resources.getIdentifier(
        "default_web_client_id",
        "string",
        packageName
    )
    if (resourceId == 0) return null

    return getString(resourceId)
        .trim()
        .takeIf { it.endsWith(".apps.googleusercontent.com") }
}

private fun googleCredentialFailureMessage(error: GetCredentialException): String = when (error) {
    is NoCredentialException ->
        "Google couldn't offer an account. Check your Google account in Android Settings, then try again."
    is GetCredentialCancellationException ->
        "Google sign-in didn't complete. Nothing changed."
    is GetCredentialProviderConfigurationException ->
        "Google's credential provider isn't available in this build. Continue privately still works."
    is GetCredentialUnsupportedException ->
        "Google sign-in isn't supported by this device's credential provider. Continue privately still works."
    else -> {
        val diagnostic = error.message
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: error.type.substringAfterLast('.')
        "Google sign-in couldn't complete: $diagnostic"
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: DailyCheckInViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Wisteria", "MainActivity: onCreate pre-super")
        
        // This MUST be called before super.onCreate and before any content is set
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // Ensure the activity can show over the keyguard and wake the screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        super.onCreate(savedInstanceState)
        Log.d("Wisteria", "MainActivity: onCreate post-super")

        configureFirebaseAppCheck(this)
        enableEdgeToEdge()
        
        viewModel = androidx.lifecycle.ViewModelProvider(this)[DailyCheckInViewModel::class.java]
        
        val triggerTakeover = intent.getBooleanExtra(EXTRA_TRIGGER_TAKEOVER, false)
        Log.d("Wisteria", "MainActivity: triggerTakeover=$triggerTakeover, action=${intent.action}")
        
        if (triggerTakeover) {
            CheckInReminderManager(this).dismissActiveAlarm()
            viewModel.openFullScreenTakeover()
        }
        
        setContent {
            WisteriaMainApp(
                viewModel = viewModel,
                startWithTakeover = triggerTakeover
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val triggerTakeover = intent.getBooleanExtra(EXTRA_TRIGGER_TAKEOVER, false)
        Log.d("Wisteria", "MainActivity: onNewIntent, triggerTakeover=$triggerTakeover, action=${intent.action}")
        if (triggerTakeover) {
            CheckInReminderManager(this).dismissActiveAlarm()
            viewModel.openFullScreenTakeover()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("Wisteria", "MainActivity: onResume, triggerTakeover=${intent.getBooleanExtra(EXTRA_TRIGGER_TAKEOVER, false)}")
        if (::viewModel.isInitialized) {
            if (intent.getBooleanExtra(EXTRA_TRIGGER_TAKEOVER, false)) {
                viewModel.openFullScreenTakeover()
            }
            // Reschedule with exact timing as soon as the person returns from system settings.
            viewModel.refreshReminderState(rescheduleIfEnabled = true)
        }
    }

    companion object {
        const val EXTRA_TRIGGER_TAKEOVER = "trigger_takeover"
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
    Log.d("Wisteria", "WisteriaMainApp: isFullScreenTakeoverActive=${uiState.isFullScreenTakeoverActive}")
    val voiceState by viewModel.voiceState.collectAsState()

    // Replace Snackbars with Toasts for a cleaner UI
    LaunchedEffect(uiState.agentStatusMessage) {
        val msg = uiState.agentStatusMessage
        val ignored = listOf(
            "Ready for a check-in",
            "Understanding this turn…",
            "Understanding what you need…"
        )
        if (msg.isNotEmpty() && !ignored.contains(msg)) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    
    val isDark = when (uiState.themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MyApplicationTheme(darkTheme = isDark) {
        val latestCheckIn by viewModel.latestSavedCheckIn.collectAsState()
        val allCheckIns by viewModel.allCheckIns.collectAsState()
        val careActions by viewModel.careActions.collectAsState()
        val memories by viewModel.agentMemories.collectAsState()

        var currentTab by remember { mutableStateOf(WisteriaTab.DAILY_PULSE) }
        var showInfoDialog by remember { mutableStateOf(false) }
        var showArchitecture by remember { mutableStateOf(false) }
        var showSupport by remember { mutableStateOf(false) }

        val healthLauncher = rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) {
            viewModel.onHealthPermissionsResult()
        }

        val notificationLauncher = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) {
            viewModel.refreshReminderState(rescheduleIfEnabled = true)
        }

        val requestNotificationAccess: () -> Unit = {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.refreshReminderState()
            }
        }

        val setCheckInAlarm: (Int, Int) -> Unit = { hour, minute ->
            viewModel.setReminder(hour, minute)
            if (
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
                val webClientId = context.configuredGoogleWebClientId()
                if (webClientId == null) {
                    viewModel.setStatusMessage(
                        "Google sign-in isn't configured in this APK. Continue privately still works."
                    )
                    return@launch
                }
                
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
                    .build()

                val buttonRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()

                try {
                    // This is an explicit button flow. Unlike the bottom-sheet option, it can
                    // offer account reauthentication or adding an account when needed. Do not
                    // clear state and silently replace its real error with a second failure.
                    val result = credentialManager.getCredential(
                        request = buttonRequest,
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
                    Log.e(
                        "Wisteria",
                        "Google Sign-In failed: type=${e.type}, message=${e.message}",
                        e
                    )
                    viewModel.setStatusMessage(googleCredentialFailureMessage(e))
                } catch (e: Exception) {
                    Log.e("Wisteria", "Unexpected Sign-In error", e)
                    viewModel.setStatusMessage("Error: ${e.message ?: "unknown"}")
                }
            }
        }

        val openIntentOrExplain: (Intent, String) -> Unit = { intent, fallback ->
            runCatching { context.startActivity(intent) }
                .onFailure { viewModel.setStatusMessage(fallback) }
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
        } else if (showSupport) {
            SupportScreen(
                onBack = { showSupport = false },
                onDial988 = {
                    openIntentOrExplain(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:988")),
                        "No phone app is available on this device."
                    )
                },
                onText988 = {
                    openIntentOrExplain(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:988")),
                        "No messaging app is available on this device."
                    )
                },
                onFindLocalHelp = {
                    openIntentOrExplain(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://findahelpline.com")),
                        "A browser is not available on this device."
                    )
                }
            )
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

                            IconButton(onClick = { showSupport = true }) {
                                Icon(
                                    imageVector = Icons.Default.SupportAgent,
                                    contentDescription = "Need support now?",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
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
                            onCloseTakeover = { viewModel.closeFullScreenTakeover() },
                            voiceState = voiceState,
                            onStartVoiceInput = { requestVoiceAction(VoiceStartAction.DICTATION) },
                            onStopVoiceInput = { viewModel.stopVoiceInput() },
                            onStartCall = { requestVoiceAction(VoiceStartAction.CALL) }
                        )
                        WisteriaTab.INSIGHTS -> DailySummaryScreen(
                            uiState = uiState,
                            latestCheckIn = latestCheckIn,
                            savedCheckInCount = allCheckIns.size,
                            careActions = careActions,
                            onToggleCareAction = { id, done -> viewModel.toggleCareAction(id, done) },
                            onRunNightShift = { viewModel.runNightShift() },
                            onTriggerFirestoreSync = { viewModel.triggerFirestoreSync() },
                            onSignInWithGoogle = onSignInWithGoogle,
                            onContinuePrivately = { viewModel.signInAnonymously() },
                            onSignOut = { viewModel.signOut() },
                            onConnectHealth = onConnectHealth,
                            onSetReminder = setCheckInAlarm,
                            onDisableReminder = { viewModel.disableReminder() },
                            onRequestNotificationAccess = requestNotificationAccess,
                            onRequestExactAlarmAccess = {
                                viewModel.getExactAlarmSettingsIntent()?.let { intent ->
                                    context.startActivity(intent)
                                }
                            },
                            onRequestFullScreenAlarmAccess = {
                                viewModel.getFullScreenAlarmSettingsIntent()?.let { intent ->
                                    context.startActivity(intent)
                                }
                            },
                            onShareHistory = {
                                if (allCheckIns.isEmpty()) {
                                    viewModel.setStatusMessage("Save a check-in before sharing your history.")
                                } else {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Wisteria check-in history")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            CheckInExportFormatter.clinicianSummary(allCheckIns)
                                        )
                                    }
                                    openIntentOrExplain(
                                        Intent.createChooser(shareIntent, "Share check-in history"),
                                        "No sharing app is available on this device."
                                    )
                                }
                            },
                            onOpenSupport = { showSupport = true }
                        )
                        WisteriaTab.RHYTHM_CARE -> RhythmMemoryScreen(
                            uiState = uiState,
                            memories = memories,
                            onConversationMemoryChanged = viewModel::setConversationMemoryEnabled,
                            onDeleteMemory = viewModel::deleteMemory,
                            onForgetConversationMemories = viewModel::forgetConversationMemories
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

        // Full-screen takeover as the absolute top-most layer
        if (uiState.isFullScreenTakeoverActive) {
            CheckInTakeoverContent(
                isOffDay = uiState.latestPulse.isOffDay,
                onDismiss = { viewModel.closeFullScreenTakeover() },
                onSubmitInput = viewModel::submitSingleInputCheckIn
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
