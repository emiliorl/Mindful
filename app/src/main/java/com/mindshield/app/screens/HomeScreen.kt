package com.mindshield.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.ChecklistItem
import com.mindshield.app.data.MorningConfig
import com.mindshield.app.data.RoutinePhase
import com.mindshield.app.data.WindDownConfig
import com.mindshield.app.service.ZoneManagerService
import com.mindshield.app.viewmodel.HomeViewModel
import com.mindshield.app.viewmodel.RoutinesViewModel
import com.mindshield.app.viewmodel.formatElapsed
import java.time.format.DateTimeFormatter

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (session == null) {
            NoSessionContent(onStart = onChangeIntent)
        } else {
            ActiveSessionContent(
                emoji   = session!!.type.emoji,
                label   = session!!.type.label,
                elapsed = elapsed.formatElapsed(),
                routinePhase = routinePhase,
                morningConfig = if (routinePhase == RoutinePhase.MORNING && morning.enabled) morning else null,
                windDownConfig = if ((routinePhase == RoutinePhase.WIND_DOWN || routinePhase == RoutinePhase.SLEEP) && windDown.enabled) windDown else null,
                completedIds = completedIds,
                morningProgress = morningProgress,
                windDownProgress = windDownProgress,
                onMorningItemToggle = { routinesVm.toggleMorningItem(it) },
                onWindDownItemToggle = { routinesVm.toggleWindDownItem(it) },
                onChangeIntent = onChangeIntent,
                onEndSession = {
                    ContextCompat.startForegroundService(
                        context,
                        ZoneManagerService.stopIntent(context)
                    )
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoSessionContent(onStart: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🧘", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No active session",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Set your intention before you begin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Start a session")
            }
        }
    }
}

@Composable
private fun ActiveSessionContent(
    emoji: String,
    label: String,
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

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            // Session card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = emoji, fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = elapsed,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Morning card
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

        // Wind-down / sleep card
        if (windDownConfig != null) {
            item {
                WindDownCard(
                    startLabel = windDownConfig.startTime.format(fmt),
                    sleepLabel = windDownConfig.sleepTime.format(fmt),
                    extendedDelaySecs = windDownConfig.extendedDelaySeconds,
                    checklist = windDownConfig.checklist,
                    completedIds = completedIds,
                    progress = windDownProgress,
                    isSleep = routinePhase == RoutinePhase.SLEEP,
                    onItemToggle = onWindDownItemToggle
                )
            }
        }

        item {
            Column {
                OutlinedButton(
                    onClick = onChangeIntent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change intent")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onEndSession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("End session", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
