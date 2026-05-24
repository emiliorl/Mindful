package com.mindshield.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay

// Local Color Palette representing the 'Sleek Interface' Design Theme
private val SleekBg = Color(0xFFF7F9FF)
private val SleekPrimary = Color(0xFF005AC1)
private val SleekTextPrimary = Color(0xFF1A1C1E)
private val SleekTextSecondary = Color(0xFF44474E)
private val SleekTextMuted = Color(0xFF74777F)
private val SleekContainer = Color(0xFFDDE2F9)
private val SleekTextOnContainer = Color(0xFF001A40)
private val SleekBorder = Color(0xFFE1E2E9)

// Individual permission sync check functions
fun checkNotificationsPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            "android.permission.POST_NOTIFICATIONS"
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun checkAccessibilityPermission(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    return enabledServices.contains("com.mindshield.app")
}

fun checkNotificationListenerPermission(context: Context): Boolean {
    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    return enabledListeners.contains("com.mindshield.app")
}

fun checkBatteryOptimizationPermission(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations("com.mindshield.app")
}

fun checkOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

@Composable
fun PermissionGate(onAllGranted: () -> Unit) {
    val context = LocalContext.current

    // Synchronize current local states
    var step1Granted by remember { mutableStateOf(checkNotificationsPermission(context)) }
    var step2Granted by remember { mutableStateOf(checkAccessibilityPermission(context)) }
    var step3Granted by remember { mutableStateOf(checkNotificationListenerPermission(context)) }
    var step4Granted by remember { mutableStateOf(checkBatteryOptimizationPermission(context)) }
    var step5Granted by remember { mutableStateOf(checkOverlayPermission(context)) }

    // Helper checking sequence
    fun refreshStates() {
        step1Granted = checkNotificationsPermission(context)
        step2Granted = checkAccessibilityPermission(context)
        step3Granted = checkNotificationListenerPermission(context)
        step4Granted = checkBatteryOptimizationPermission(context)
        step5Granted = checkOverlayPermission(context)
    }

    // Determine the first unresolved step
    val actualUngrantedStep = remember(step1Granted, step2Granted, step3Granted, step4Granted, step5Granted) {
        when {
            !step1Granted -> 1
            !step2Granted -> 2
            !step3Granted -> 3
            !step4Granted -> 4
            !step5Granted -> 5
            else -> 6
        }
    }

    // Capture the initial ungranted step on first composition
    val initialStep = remember {
        val s1 = checkNotificationsPermission(context)
        val s2 = checkAccessibilityPermission(context)
        val s3 = checkNotificationListenerPermission(context)
        val s4 = checkBatteryOptimizationPermission(context)
        val s5 = checkOverlayPermission(context)
        when {
            !s1 -> 1
            !s2 -> 2
            !s3 -> 3
            !s4 -> 4
            !s5 -> 5
            else -> 6
        }
    }

    var visibleStep by remember { mutableStateOf(initialStep) }

    // Satisfying delayed step progression when user sets permission
    LaunchedEffect(actualUngrantedStep) {
        if (actualUngrantedStep == 6) {
            delay(800)
            onAllGranted()
        } else if (actualUngrantedStep > visibleStep) {
            delay(800)
            visibleStep = actualUngrantedStep
        } else {
            visibleStep = actualUngrantedStep
        }
    }

    // Activity launcher for step 1 notifications
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        step1Granted = isGranted
        if (isGranted) {
            refreshStates()
        }
    }

    // Lifecycle resume listener to monitor settings changes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Match localized titles
    val stepTitle = when (visibleStep) {
        1 -> "Notifications"
        2 -> "Accessibility Access"
        3 -> "Notification Access"
        4 -> "Background Activity"
        5 -> "Display Over Apps"
        else -> ""
    }

    // Match localized plain English rationale
    val stepReason = when (visibleStep) {
        1 -> "So MindShield can show your active session and notify you when a batch is ready."
        2 -> "So MindShield can show a pause screen before designated apps open."
        3 -> "So MindShield can hold and batch your notifications."
        4 -> "So MindShield keeps running when your screen is off."
        5 -> "So MindShield can show the pause screen on top of other apps."
        else -> ""
    }

    // Identify current active verification parameter
    val isCurrentStepGranted = when (visibleStep) {
        1 -> step1Granted
        2 -> step2Granted
        3 -> step3Granted
        4 -> step4Granted
        5 -> step5Granted
        else -> false
    }

    // High quality vector icon corresponding to each permission step
    val stepIcon: ImageVector = when (visibleStep) {
        1 -> Icons.Filled.Notifications
        2 -> Icons.Filled.Settings
        3 -> Icons.Filled.List
        4 -> Icons.Filled.Warning
        5 -> Icons.Filled.Info
        else -> Icons.Filled.Check
    }

    // Intent handling matching specifications
    val onGrantClicked = {
        when (visibleStep) {
            1 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch("android.permission.POST_NOTIFICATIONS")
                }
            }
            2 -> {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }
            3 -> {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
            4 -> {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:com.mindshield.app")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    context.startActivity(intent)
                }
            }
            5 -> {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:com.mindshield.app")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    context.startActivity(intent)
                }
            }
        }
    }

    // Elegant full viewport container
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(SleekBg),
        color = SleekBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // HEADER SECTION
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Step Badge Pill
                    Box(
                        modifier = Modifier
                            .background(SleekContainer, RoundedCornerShape(100.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Step $visibleStep of 5",
                            color = SleekTextOnContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Progress Lines
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in 1..5) {
                            val activeColor = if (i <= visibleStep) SleekPrimary else SleekBorder
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 4.dp)
                                    .background(activeColor, RoundedCornerShape(2.dp))
                                    .testTag("step_indicator_$i")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Permission Title
                Text(
                    text = stepTitle,
                    color = SleekTextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.testTag("step_title")
                )
            }

            // MAIN AREA
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Centered Illustrative Icon Casing
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(SleekContainer, RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = stepIcon,
                        contentDescription = "$stepTitle Icon",
                        tint = SleekPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Plain English description text
                Text(
                    text = stepReason,
                    color = SleekTextSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag("step_description")
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Privacy Safeguard Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, SleekBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Privacy Guard Icon",
                            tint = SleekPrimary,
                            modifier = Modifier
                                .size(20.dp)
                                .offset(y = 1.dp)
                        )
                        Column {
                            Text(
                                text = "Privacy First",
                                color = SleekTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "MindShield only observes app launches to apply your focus settings. No personal data is stored or shared.",
                                color = SleekTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            // FOOTER & ACTION ELEMENTS
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                if (isCurrentStepGranted) {
                    // satisfy checkmark replace button when granted
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("checkmark_icon"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(SleekPrimary, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Step Granted",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    // Show actions when not granted
                    Button(
                        onClick = onGrantClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("grant_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SleekPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text(
                            text = if (visibleStep == 2 || visibleStep == 3) "Open Settings" else "Grant",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Double confirmation button requested for settings redirections
                    if (visibleStep == 2 || visibleStep == 3) {
                        Button(
                            onClick = { refreshStates() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("enabled_confirm_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = SleekPrimary
                            ),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "I've enabled it",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Brand Version stamp
                Text(
                    text = "MINDSHIELD V1.0.0 FOUNDATION",
                    color = SleekTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
