package com.mindshield.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.mindshield.app.viewmodel.HomeViewModel
import com.mindshield.app.viewmodel.RoutinesViewModel
import com.mindshield.app.viewmodel.formatElapsed
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Shared intent → accent color (mirrors StatsScreen palette)
internal fun IntentType.accentColor(): Color = when (this) {
    IntentType.SOCIAL_MEDIA  -> Color(0xFFE57373)
    IntentType.WORK          -> Color(0xFF64B5F6)
    IntentType.STUDY         -> Color(0xFF81C784)
    IntentType.FITNESS       -> Color(0xFFFFB74D)
    IntentType.ENTERTAINMENT -> Color(0xFFBA68C8)
    IntentType.JUST_LOOKING  -> Color(0xFF90A4AE)
}

@Composable
fun HomeScreen(onChangeIntent: () -> Unit) {
    val vm: HomeViewModel = viewModel()
    val routinesVm: RoutinesViewModel = viewModel()
    val session by vm.session.collectAsStateWithLifecycle()
    val elapsed by vm.elapsedSeconds.collectAsStateWithLifecycle()
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
            elapsed = elapsed.formatElapsed(),
            routinePhase = routinePhase,
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
            Text("🌙", fontSize = 64.sp)
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
            .padding(horizontal = 24.dp),
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

        Spacer(Modifier.height(40.dp))

        // Quick-pick: the three most common intents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(IntentType.WORK, IntentType.STUDY, IntentType.SOCIAL_MEDIA).forEach { type ->
                QuickIntentChip(
                    type = type,
                    onClick = { onQuickStart(type) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("See all intents")
        }
    }
}

@Composable
private fun QuickIntentChip(
    type: IntentType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = type.accentColor()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = type.emoji, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active session
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveSessionContent(
    session: IntentSession,
    elapsed: String,
    routinePhase: RoutinePhase?,
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
    val accentColor = session.type.accentColor()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Session card tinted with the intent's accent color
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = session.type.emoji, fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = session.type.label,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = elapsed,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (morningConfig != null) {
            item {
                MorningCard(
                    config = morningConfig,
                    completedIds = completedIds,
                    progress = morningProgress,
                    onItemToggle = onMorningItemToggle
                )
            }
        }

        if (windDownConfig != null) {
            item {
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
        }

        item {
            // Buttons side-by-side instead of stacked
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeIntent,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Change intent")
                }
                TextButton(
                    onClick = onEndSession,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("End session")
                }
            }
        }
    }
}
