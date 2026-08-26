package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.OffCoral
import com.example.ui.theme.ForestGreenAccent
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.HeavyRose
import com.example.ui.theme.WisteriaLavender
import com.example.ui.theme.WisteriaSoftLilac

@Composable
fun FullScreenCheckInDialog(
    isOpen: Boolean,
    isOffDay: Boolean = false,
    onDismiss: () -> Unit,
    onSubmitInput: (String) -> Unit
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        CheckInTakeoverContent(
            isOffDay = isOffDay,
            onDismiss = onDismiss,
            onSubmitInput = onSubmitInput
        )
    }
}

@Composable
fun CheckInTakeoverContent(
    isOffDay: Boolean = false,
    onDismiss: () -> Unit,
    onSubmitInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    android.util.Log.d("Wisteria", "CheckInTakeoverContent: composing, isOffDay=$isOffDay")
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.background,
                        colorScheme.primaryContainer.copy(alpha = 0.85f),
                        colorScheme.secondaryContainer.copy(alpha = 0.9f),
                        colorScheme.background
                    )
                )
            )
            .padding(24.dp)
            .testTag("fullscreen_checkin_dialog"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Cascading Wisteria flower motif header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(colorScheme.primary, colorScheme.secondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = "Wisteria Cascading Motif",
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Wisteria",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )

            Text(
                text = "Full-screen daily check-in • 3 seconds max",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = colorScheme.tertiary,
                    letterSpacing = 0.5.sp
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.94f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isOffDay)
                            "Last time felt off. How is today?"
                        else
                            "How are you feeling today?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Single tap 1-5 rating buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        data class RatingOption(val num: Int, val icon: ImageVector, val desc: String)
                        val options = listOf(
                            RatingOption(1, Icons.Default.Bolt, "Off"),
                            RatingOption(2, Icons.Default.Cloud, "Heavy"),
                            RatingOption(3, Icons.Default.Eco, "Steady"),
                            RatingOption(4, Icons.Default.AutoAwesome, "Clear"),
                            RatingOption(5, Icons.Default.Favorite, "Bright")
                        )

                        options.forEach { (num, icon, desc) ->
                            val optionColor = if (num <= 2) OffCoral else ForestGreenAccent
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onSubmitInput("$num ($desc)")
                                    }
                                    .background(optionColor.copy(alpha = if (num <= 2) 0.15f else 0.2f))
                                    .padding(vertical = 12.dp, horizontal = 10.dp)
                                    .testTag("tap_option_$num")
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = desc,
                                    tint = optionColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = num.toString(),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Quick single-word quick pill triggers
                    Text(
                        text = "Or tap a quick texture signal:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickPill(
                            text = "Heavy",
                            color = HeavyRose,
                            onClick = { onSubmitInput("Today feels heavy") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        QuickPill(
                            text = "Off",
                            color = OffCoral,
                            onClick = { onSubmitInput("I feel off") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        QuickPill(
                            text = "Bright",
                            color = ForestGreenMint,
                            onClick = { onSubmitInput("Feeling bright and clear") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Always-visible, honest way out — this is a nudge, not a trap.
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_takeover_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Not now",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }
}

@Composable
fun QuickPill(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier
            .clickable { onClick() }
            .testTag("pill_${text.lowercase()}")
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun StartupBootOverlay(
    bootLogs: List<String>,
    isVisible: Boolean
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(32.dp)
                .testTag("startup_boot_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = "Wisteria Blossom",
                        tint = WisteriaLavender,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Wisteria System Boot",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = WisteriaSoftLilac,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ForestGreenAccent.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bootLogs.forEach { log ->
                            Text(
                                text = "> $log",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (log.contains("ready")) ForestGreenMint else WisteriaSoftLilac,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
