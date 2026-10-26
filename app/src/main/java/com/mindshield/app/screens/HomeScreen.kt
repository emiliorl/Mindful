package com.mindshield.app.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.ChecklistItem
import com.mindshield.app.data.IntentSession
import com.mindshield.app.data.IntentType
import com.mindshield.app.data.MorningConfig
import com.mindshield.app.data.RoutinePhase
import com.mindshield.app.data.WindDownConfig
import com.mindshield.app.service.ZoneManagerService
import com.mindshield.app.ui.theme.accent
import com.mindshield.app.ui.theme.icon
import com.mindshield.app.viewmodel.HomeViewModel
import com.mindshield.app.viewmodel.RoutinesViewModel
import com.mindshield.app.viewmodel.formatElapsed
import com.mindshield.app.viewmodel.spokenElapsed
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(onChangeIntent: () -> Unit, onOpenInsights: () -> Unit = {}) {
    val vm: HomeViewModel = viewModel()
    val routinesVm: RoutinesViewModel = viewModel()
    val session by vm.session.collectAsStateWithLifecycle()
    val elapsed by vm.elapsedSeconds.collectAsStateWithLifecycle()
    val protectedCount by vm.protectedCount.collectAsStateWithLifecycle()
    val batchDuringSession by vm.batchDuringSession.collectAsStateWithLifecycle()
    val queuedCount by vm.queuedCount.collectAsStateWithLifecycle()
    val routinePhase by routinesVm.routinePhase.collectAsStateWithLifecycle()
    val morning by routinesVm.morning.collectAsStateWithLifecycle()
    val windDown by routinesVm.windDown.collectAsStateWithLifecycle()
    val completedIds by routinesVm.completedIds.collectAsStateWithLifecycle()
    val morningProgress by routinesVm.morningProgress.collectAsStateWithLifecycle()
    val windDownProgress by routinesVm.windDownProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when {
        routinePhase == RoutinePhase.SLEEP -> SleepModeContent()

        session == null -> NoSessionContent(
            queuedCount = queuedCount,
            onOpenInsights = onOpenInsights,
            onStart = onChangeIntent,
            onQuickStart = { type ->
                ContextCompat.startForegroundService(
                    context,
                    ZoneManagerService.startIntent(context, type)
                )
            }
        )

        else -> ActiveSessionContent(
            session = session!!,
            elapsedSeconds = elapsed,
            protectedCount = protectedCount,
            batchDuringSession = batchDuringSession,
            queuedCount = queuedCount,
            onOpenInsights = onOpenInsights,
            morningConfig = if (routinePhase == RoutinePhase.MORNING && morning.enabled) morning else null,
            windDownConfig = if (routinePhase == RoutinePhase.WIND_DOWN && windDown.enabled) windDown else null,
            completedIds = completedIds,
            morningProgress = morningProgress,
            windDownProgress = windDownProgress,
            onMorningItemToggle = { routinesVm.toggleMorningItem(it) },
            onWindDownItemToggle = { routinesVm.toggleWindDownItem(it) },
            onChangeIntent = onChangeIntent,
            onEndSession = {
                ContextCompat.startForegroundService(context, ZoneManagerService.stopIntent(context))
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sleep mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SleepModeContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Sleep mode",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your phone is resting.\nSee you in the morning.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// No session — greeting + quick-pick
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoSessionContent(
    queuedCount: Int,
    onOpenInsights: () -> Unit,
    onStart: () -> Unit,
    onQuickStart: (IntentType) -> Unit
) {
    val greeting = remember {
        when {
            LocalTime.now().hour < 12 -> "Good morning"
            LocalTime.now().hour < 17 -> "Good afternoon"
            else                      -> "Good evening"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "What's your intention?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        // Quick-pick: the three most common intents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(IntentType.WORK, IntentType.STUDY, IntentType.SOCIAL_MEDIA).forEach { type ->
                QuickIntentTile(
                    type = type,
                    onClick = { onQuickStart(type) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("See all intents")
        }

        if (queuedCount > 0) {
            Spacer(Modifier.height(16.dp))
            HeldPill(count = queuedCount, onClick = onOpenInsights)
        }
    }
}

@Composable
private fun QuickIntentTile(
    type: IntentType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = type.accent()
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = accent
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared small pieces
// ─────────────────────────────────────────────────────────────────────────────

/** Count-only pill; the contents of the queue are never shown. */
@Composable
private fun HeldPill(count: Int, onClick: () -> Unit) {
    val noun = if (count == 1) "notification" else "notifications"
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Inbox,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$count $noun held for the next delivery",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StatusChip(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Clock-style ring: sweeps one full revolution per hour. There is no target duration,
 * so it conveys the passage of time rather than progress toward a goal.
 */
@Composable
private fun TimerRing(type: IntentType, elapsedSeconds: Long, modifier: Modifier = Modifier) {
    val accent = type.accent()
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    // Animate a monotonically increasing angle so the 360 -> 0 wrap never spins backwards;
    // the modulo is applied only when drawing.
    val totalAngle by animateFloatAsState(
        targetValue = elapsedSeconds / 3600f * 360f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "timerRingAngle"
    )
    val sweep = totalAngle % 360f

    val description = "${type.label}, ${elapsedSeconds.spokenElapsed()}"

    Box(
        modifier = modifier
            .size(240.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawCircle(
                color = trackColor,
                radius = (size.minDimension - strokeWidth) / 2f,
                style = Stroke(width = strokeWidth)
            )
            if (sweep > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = elapsedSeconds.formatElapsed(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active session
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSessionContent(
    session: IntentSession,
    elapsedSeconds: Long,
    protectedCount: Int,
    batchDuringSession: Boolean,
    queuedCount: Int,
    onOpenInsights: () -> Unit,
    morningConfig: MorningConfig?,
    windDownConfig: WindDownConfig?,
    completedIds: Set<String>,
    morningProgress: Float,
    windDownProgress: Float,
    onMorningItemToggle: (ChecklistItem) -> Unit,
    onWindDownItemToggle: (ChecklistItem) -> Unit,
    onChangeIntent: () -> Unit,
    onEndSession: () -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    var confirmEnd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimerRing(type = session.type, elapsedSeconds = elapsedSeconds)

            if (protectedCount > 0 || batchDuringSession) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (protectedCount > 0) {
                        val noun = if (protectedCount == 1) "app" else "apps"
                        StatusChip(Icons.Rounded.PauseCircle, "Pause screen on · $protectedCount $noun")
                    }
                    if (batchDuringSession) {
                        StatusChip(Icons.Rounded.NotificationsPaused, "Notifications held")
                    }
                }
            }

            if (queuedCount > 0) {
                HeldPill(count = queuedCount, onClick = onOpenInsights)
            }

            if (morningConfig != null) {
                MorningCard(
                    config = morningConfig,
                    completedIds = completedIds,
                    progress = morningProgress,
                    onItemToggle = onMorningItemToggle
                )
            }

            if (windDownConfig != null) {
                WindDownCard(
                    startLabel = windDownConfig.startTime.format(fmt),
                    sleepLabel = windDownConfig.sleepTime.format(fmt),
                    extendedDelaySecs = windDownConfig.extendedDelaySeconds,
                    checklist = windDownConfig.checklist,
                    completedIds = completedIds,
                    progress = windDownProgress,
                    isSleep = false,
                    onItemToggle = onWindDownItemToggle
                )
            }

            IntentNudgeCard(type = session.type)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onChangeIntent,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Switch intent")
            }
            OutlinedButton(
                onClick = { confirmEnd = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text("End session")
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this session?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEnd = false
                        onEndSession()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("End session") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Keep going") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Intent nudge — ambient, low-emphasis encouragement tied to the active intent
// ─────────────────────────────────────────────────────────────────────────────

private fun IntentType.nudgeCopy(): String = when (this) {
    IntentType.SOCIAL_MEDIA  -> "A quick check-in is fine — notice when it turns into a scroll."
    IntentType.WORK          -> "Heads-down time. Everything else can wait."
    IntentType.STUDY         -> "Small sessions add up. Keep going."
    IntentType.FITNESS       -> "Away from the screen is the point — enjoy it."
    IntentType.ENTERTAINMENT -> "Enjoy it — you chose this on purpose."
    IntentType.JUST_LOOKING  -> "No agenda needed. Just notice what you reach for next."
}

@Composable
private fun IntentNudgeCard(type: IntentType) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = type.nudgeCopy(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
