package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CareActionEntity
import com.example.domain.agent.model.AgentExecutionState
import com.example.domain.agent.model.CycleTexture
import com.example.domain.agent.model.ToolCallRecord
import com.example.ui.theme.CalmTeal
import com.example.ui.theme.DropCoral
import com.example.ui.theme.ForestGreenAccent
import com.example.ui.theme.ForestGreenMint
import com.example.ui.theme.ForestGreenSage
import com.example.ui.theme.PeriodCrimson
import com.example.ui.theme.SpottingRose
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WisteriaLavender
import com.example.ui.theme.WisteriaSoftLilac

@Composable
fun CycleTextureGauge(
    texture: CycleTexture,
    rating: Int,
    isPmddImminent: Boolean,
    modifier: Modifier = Modifier
) {
    val textureColor = when (texture) {
        CycleTexture.FEELING_GOOD -> ForestGreenMint
        CycleTexture.SPOTTING_PHASE -> SpottingRose
        CycleTexture.ACTUAL_PERIOD -> PeriodCrimson
        CycleTexture.MEDS_DROP_WINDOW -> DropCoral
        CycleTexture.UNKNOWN_CALIBRATING -> WisteriaLavender
    }

    val stateTitle = when (texture) {
        CycleTexture.FEELING_GOOD -> "Alive & Baseline Okay"
        CycleTexture.SPOTTING_PHASE -> "10-Day Spotting Window"
        CycleTexture.ACTUAL_PERIOD -> "Actual Period Flow"
        CycleTexture.MEDS_DROP_WINDOW -> "5-Day Meds Efficacy Drop"
        CycleTexture.UNKNOWN_CALIBRATING -> "Calibrating Cycle Texture"
    }

    Card(
        modifier = modifier.testTag("cycle_texture_gauge_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val gaugeStart = MaterialTheme.colorScheme.primary
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(92.dp)
            ) {
                Canvas(modifier = Modifier.size(92.dp)) {
                    val strokeWidth = 8.dp.toPx()
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.15f),
                        style = Stroke(width = strokeWidth)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(gaugeStart, ForestGreenAccent, textureColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = (rating / 5f) * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$rating/5",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "Pulse",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = "Texture",
                        tint = textureColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cycle Texture",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stateTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = textureColor
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPmddImminent)
                        "⚡ Pre-crash window active: Rest and hydration queued before drop hits."
                    else
                        "Pattern learned from texture, not standard 28-day calendar math.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun AgentReasoningCard(
    thoughtTrace: String,
    agentState: AgentExecutionState,
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agent_reasoning_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "ADK Thought Trace",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Google ADK Companion Engine",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Trace",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = thoughtTrace.ifEmpty { "Working context active. Gemini 3.5 Flash watching irregular cycle signals to protect against the PMDD drop." },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = WisteriaSoftLilac
                            ),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolExecutionChip(
    toolRecord: ToolCallRecord,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("tool_chip_${toolRecord.toolName}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (toolRecord.status == "SUCCESS") ForestGreenMint else DropCoral)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${toolRecord.toolName}: ${toolRecord.resultSummary}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
        }
    }
}

@Composable
fun CareActionRow(
    item: CareActionEntity,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val actionColor = when (item.type) {
        "REST_SUPPORT" -> ForestGreenMint
        "COGNITIVE_REDUCTION" -> WisteriaLavender
        "LOW_EFFORT_MEAL" -> WarningAmber
        "COMFORT_QUEUE" -> CalmTeal
        else -> WisteriaSoftLilac
    }

    val iconVector = when (item.type) {
        "REST_SUPPORT" -> Icons.Default.Spa
        "COGNITIVE_REDUCTION" -> Icons.Default.Shield
        "LOW_EFFORT_MEAL" -> Icons.Default.Restaurant
        "COMFORT_QUEUE" -> Icons.Default.Favorite
        else -> Icons.Default.AutoAwesome
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("care_action_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!item.isCompleted) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (item.isCompleted) "Completed" else "Incomplete",
                tint = if (item.isCompleted) ForestGreenMint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = actionColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
