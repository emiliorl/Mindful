package com.mindshield.app.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Data model for each permission step
// ─────────────────────────────────────────────────────────────────────────────

private enum class PermStep {
    NOTIFICATIONS,
    ACCESSIBILITY,
    BATTERY,
    OVERLAY,
    DONE;

    val title: String
        get() = when (this) {
            NOTIFICATIONS -> "Stay informed"
            ACCESSIBILITY -> "Detect app openings"
            BATTERY      -> "Run in the background"
            OVERLAY      -> "Show the pause screen"
            DONE         -> "You're all set"
        }

    val description: String
        get() = when (this) {
            NOTIFICATIONS ->
                "MindShield needs permission to show you session summaries and batch notification alerts."
            ACCESSIBILITY ->
                "The Accessibility permission lets MindShield detect when you open a friction-enabled app and show the breath moment. No keystrokes or content are ever read."
            BATTERY      ->
                "Exclude MindShield from battery optimisation so your session state and routines keep running reliably."
            OVERLAY      ->
                "The \"draw over other apps\" permission lets MindShield show the pause screen on top of an app before it opens."
            DONE         ->
                "All permissions are in place. MindShield is ready to help you use your phone with intention."
        }

    val emoji: String
        get() = when (this) {
            NOTIFICATIONS -> "🔔"
            ACCESSIBILITY -> "👁"
            BATTERY      -> "🔋"
            OVERLAY      -> "🧘"
            DONE         -> "✅"
        }

    val buttonLabel: String
        get() = when (this) {
            DONE -> "Start MindShield"
            else -> "Grant permission"
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PermissionGate(onFinished: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(PermStep.NOTIFICATIONS) }

    // Runtime permission launcher (for POST_NOTIFICATIONS on Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed regardless — user can grant later from settings
        step = PermStep.ACCESSIBILITY
    }

    val steps = PermStep.entries
    val currentIndex = steps.indexOf(step).coerceAtLeast(0)
    val progressFraction = currentIndex.toFloat() / (steps.size - 1).toFloat()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(48.dp))

            // Progress dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.dropLast(1).forEachIndexed { index, _ ->
                    val active = index <= currentIndex
                    Box(
                        Modifier
                            .size(if (index == currentIndex) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(Modifier.height(64.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "perm_step"
            ) { current ->
                PermStepContent(
                    step = current,
                    onAction = {
                        when (current) {
                            PermStep.NOTIFICATIONS -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    step = PermStep.ACCESSIBILITY
                                }
                            }
                            PermStep.ACCESSIBILITY -> {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                )
                                step = PermStep.BATTERY
                            }
                            PermStep.BATTERY -> {
                                val pm = context.getSystemService(PowerManager::class.java)
                                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                                step = PermStep.OVERLAY
                            }
                            PermStep.OVERLAY -> {
                                if (!Settings.canDrawOverlays(context)) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                                step = PermStep.DONE
                            }
                            PermStep.DONE -> onFinished()
                        }
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single step content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermStepContent(
    step: PermStep,
    onAction: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Emoji icon
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.emoji,
                fontSize = 40.sp
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = step.buttonLabel,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (step != PermStep.DONE) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = {
                // Skip — proceed to next step without granting
                // (user can re-run onboarding from settings)
            }) {
                Text(
                    text = "Skip for now",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
