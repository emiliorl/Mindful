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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

private enum class PermStep {
    NOTIFICATIONS, ACCESSIBILITY, BATTERY, OVERLAY, DONE;

    val title: String get() = when (this) {
        NOTIFICATIONS -> "Stay informed"
        ACCESSIBILITY -> "Detect app openings"
        BATTERY       -> "Run in the background"
        OVERLAY       -> "Show the pause screen"
        DONE          -> "You're all set"
    }

    val description: String get() = when (this) {
        NOTIFICATIONS ->
            "MindShield needs permission to show you session summaries and batch notification alerts."
        ACCESSIBILITY ->
            "The Accessibility permission lets MindShield detect when you open a friction-enabled app and show the breath moment. No keystrokes or content are ever read."
        BATTERY ->
            "Exclude MindShield from battery optimisation so your session state and routines keep running reliably."
        OVERLAY ->
            "The \"draw over other apps\" permission lets MindShield show the pause screen on top of an app before it opens."
        DONE ->
            "All permissions are in place. MindShield is ready to help you use your phone with intention."
    }

    val icon: ImageVector get() = when (this) {
        NOTIFICATIONS -> Icons.Outlined.Notifications
        ACCESSIBILITY -> Icons.Outlined.Accessibility
        BATTERY       -> Icons.Outlined.BatterySaver
        OVERLAY       -> Icons.Outlined.Layers
        DONE          -> Icons.Outlined.TaskAlt
    }

    val buttonLabel: String get() = when (this) {
        DONE -> "Get started"
        else -> "Grant permission"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PermissionGate(onFinished: () -> Unit) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var step            by remember { mutableStateOf(PermStep.NOTIFICATIONS) }
    var pendingNextStep by remember { mutableStateOf<PermStep?>(null) }

    // Advance step when returning from a system settings screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                pendingNextStep?.let { next ->
                    step = next
                    pendingNextStep = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> step = PermStep.ACCESSIBILITY }

    val steps        = PermStep.entries
    val currentIndex = steps.indexOf(step).coerceAtLeast(0)
    // Exclude DONE from the progress denominator so bar fills on the last real step
    val progressSteps = steps.size - 1
    val animatedProgress by animateFloatAsState(
        targetValue   = currentIndex.toFloat() / progressSteps.toFloat(),
        animationSpec = tween(500),
        label         = "progress"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(40.dp))

            // Wordmark
            Text(
                text          = "MindShield",
                style         = MaterialTheme.typography.titleSmall,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color         = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )

            Spacer(Modifier.height(24.dp))

            // Slim progress bar
            LinearProgressIndicator(
                progress      = { animatedProgress },
                modifier      = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(50)),
                color         = MaterialTheme.colorScheme.primary,
                trackColor    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )

            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState  = step,
                transitionSpec = {
                    (slideInHorizontally(tween(300)) { it / 6 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(200)))
                },
                label = "perm_step"
            ) { current ->
                PermStepContent(
                    step         = current,
                    stepNumber   = steps.indexOf(current) + 1,
                    totalSteps   = progressSteps,
                    onAction     = {
                        when (current) {
                            PermStep.NOTIFICATIONS -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    step = PermStep.ACCESSIBILITY
                                }
                            }
                            PermStep.ACCESSIBILITY -> {
                                pendingNextStep = PermStep.BATTERY
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                            PermStep.BATTERY -> {
                                val pm = context.getSystemService(PowerManager::class.java)
                                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                    pendingNextStep = PermStep.OVERLAY
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                } else {
                                    step = PermStep.OVERLAY
                                }
                            }
                            PermStep.OVERLAY -> {
                                if (!Settings.canDrawOverlays(context)) {
                                    pendingNextStep = PermStep.DONE
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                } else {
                                    step = PermStep.DONE
                                }
                            }
                            PermStep.DONE -> onFinished()
                        }
                    },
                    onSkip = {
                        val nextIndex = steps.indexOf(current) + 1
                        if (nextIndex < steps.size) step = steps[nextIndex]
                    }
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single-step content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermStepContent(
    step       : PermStep,
    stepNumber : Int,
    totalSteps : Int,
    onAction   : () -> Unit,
    onSkip     : () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.fillMaxWidth()
    ) {

        // Icon — subtle circular backing, no saturated color block
        Box(
            modifier         = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = step.icon,
                contentDescription = null,
                modifier           = Modifier.size(36.dp),
                tint               = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(32.dp))

        // Step counter
        if (step != PermStep.DONE) {
            Text(
                text          = "%02d / %02d".format(stepNumber, totalSteps),
                style         = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color         = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(10.dp))
        }

        Text(
            text       = step.title,
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text       = step.description,
            style      = MaterialTheme.typography.bodyLarge,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            lineHeight = 26.sp
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick   = onAction,
            modifier  = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape     = RoundedCornerShape(14.dp),
            colors    = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor   = MaterialTheme.colorScheme.surface
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Text(
                text          = step.buttonLabel,
                style         = MaterialTheme.typography.titleSmall,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            )
        }

        if (step != PermStep.DONE) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip) {
                Text(
                    text  = "Skip for now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}
