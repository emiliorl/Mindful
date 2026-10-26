package com.mindshield.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.eventFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.FrictionMode
import com.mindshield.app.ui.components.AppIcon
import com.mindshield.app.ui.components.SectionHeader
import com.mindshield.app.util.PermissionStatus
import com.mindshield.app.viewmodel.ShieldApp
import com.mindshield.app.viewmodel.ShieldFilter
import com.mindshield.app.viewmodel.ShieldViewModel
import com.mindshield.app.viewmodel.statusText
import kotlinx.coroutines.flow.filterIsInstance

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldScreen(onOpenSettings: () -> Unit) {
    val vm: ShieldViewModel = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check both capabilities whenever the screen resumes (e.g. returning from system settings).
    var accessibilityOn by remember { mutableStateOf(PermissionStatus.isAccessibilityEnabled(context)) }
    var listenerOn by remember { mutableStateOf(PermissionStatus.isNotificationListenerEnabled(context)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.eventFlow
            .filterIsInstance<Lifecycle.Event>()
            .collect { event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    accessibilityOn = PermissionStatus.isAccessibilityEnabled(context)
                    listenerOn = PermissionStatus.isNotificationListenerEnabled(context)
                }
            }
    }

    val protectedApps by vm.protectedApps.collectAsStateWithLifecycle()
    val otherApps by vm.otherApps.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val pauseDuration by vm.pauseDuration.collectAsStateWithLifecycle()
    val settings by vm.globalSettings.collectAsStateWithLifecycle()

    var configPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var showPauseSheet by rememberSaveable { mutableStateOf(false) }
    var showDeliverySheet by rememberSaveable { mutableStateOf(false) }

    val searching = query.isNotBlank() || filter == ShieldFilter.SUGGESTED
    val noMatches = !isLoading && protectedApps.isEmpty() && otherApps.isEmpty() && searching
    val showProtectedSection = protectedApps.isNotEmpty() ||
        (!isLoading && !searching)
    val showOtherSection = otherApps.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (!accessibilityOn || !listenerOn) {
            item(key = "banners") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        // One banner when both are missing keeps the app list near the top.
                        !accessibilityOn && !listenerOn -> ShieldPermissionBanner(
                            "Pausing and holding notifications need Accessibility and Notification access",
                            onOpenSettings
                        )
                        !accessibilityOn -> ShieldPermissionBanner(
                            "Pause screen needs Accessibility access", onOpenSettings
                        )
                        else -> ShieldPermissionBanner(
                            "Held notifications need Notification access", onOpenSettings
                        )
                    }
                }
            }
        }

        item(key = "controls") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShieldSearchField(query = query, onQueryChange = vm::onSearchQueryChanged)
                ShieldFilterChips(selected = filter, onSelect = vm::setFilter)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShieldSummaryRow(
                        icon = Icons.Rounded.Timer,
                        title = "Pause length · ${pauseDuration}s",
                        subtitle = null,
                        onClick = { showPauseSheet = true }
                    )
                    ShieldSummaryRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Notification delivery",
                        subtitle = buildString {
                            append(
                                if (settings.deliveryTimes.isEmpty()) "No delivery times set"
                                else settings.deliveryTimes.joinToString(", ") { formatDeliveryTime(it) }
                            )
                            if (settings.batchDuringSession) append("\nHeld during focus sessions")
                        },
                        onClick = { showDeliverySheet = true }
                    )
                }
            }
        }

        if (isLoading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        } else if (noMatches) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No apps match",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (showProtectedSection) {
                item(key = "header_protected") {
                    SectionHeader("Protected", count = protectedApps.size)
                }
                if (protectedApps.isEmpty()) {
                    item(key = "protected_hint") {
                        Text(
                            "Apps you pause or batch show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(protectedApps, key = { "p_" + it.packageName }) { app ->
                        ShieldAppRow(
                            app = app,
                            pauseDuration = pauseDuration,
                            dimSwitch = !accessibilityOn,
                            onClick = { configPackage = app.packageName },
                            onToggle = { vm.toggleFriction(app.packageName, it) }
                        )
                    }
                }
            }
            if (showOtherSection) {
                item(key = "header_all") { SectionHeader("All apps") }
                items(otherApps, key = { "o_" + it.packageName }) { app ->
                    ShieldAppRow(
                        app = app,
                        pauseDuration = pauseDuration,
                        dimSwitch = !accessibilityOn,
                        onClick = { configPackage = app.packageName },
                        onToggle = { vm.toggleFriction(app.packageName, it) }
                    )
                }
            }
        }
    }

    // ── Sheets ────────────────────────────────────────────────────────────────
    configPackage?.let { pkg ->
        AppConfigSheet(
            packageName = pkg,
            vm = vm,
            accessibilityEnabled = accessibilityOn,
            notificationAccessEnabled = listenerOn,
            onOpenSettings = onOpenSettings,
            onChangePauseLength = { showPauseSheet = true },
            onDismiss = { configPackage = null }
        )
    }
    if (showPauseSheet) {
        PauseDurationSheet(
            currentSeconds = pauseDuration,
            onSelect = vm::setPauseDuration,
            onDismiss = { showPauseSheet = false }
        )
    }
    if (showDeliverySheet) {
        DeliveryScheduleSheet(
            settings = settings,
            onTimesChange = vm::setDeliveryTimes,
            onBatchDuringSessionChange = vm::setBatchDuringSession,
            onDismiss = { showDeliverySheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShieldPermissionBanner(text: String, onEnable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onEnable) { Text("Enable") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShieldSearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ShieldFilterChips(selected: ShieldFilter, onSelect: (ShieldFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShieldFilter.entries.forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = {
                    Text(
                        when (f) {
                            ShieldFilter.ALL       -> "All"
                            ShieldFilter.PROTECTED -> "Protected"
                            ShieldFilter.SUGGESTED -> "Likely distracting"
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun ShieldSummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShieldAppRow(
    app: ShieldApp,
    pauseDuration: Int,
    dimSwitch: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val status = statusText(app.friction, app.rule, pauseDuration)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Configure ${app.label}", onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = app.packageName, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = app.friction.mode != FrictionMode.OFF,
            onCheckedChange = onToggle,
            modifier = Modifier
                .alpha(if (dimSwitch) 0.6f else 1f)
                .semantics { contentDescription = "Pause before opening ${app.label}" }
        )
    }
}
