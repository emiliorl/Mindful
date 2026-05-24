package com.mindshield.app.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.IntentType
import com.mindshield.app.viewmodel.AppEntry
import com.mindshield.app.viewmodel.AppsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen() {
    val vm: AppsViewModel = viewModel()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Apps",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Toggle friction per app, or auto-apply it for specific sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = vm::onSearchQueryChanged,
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider()

        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onToggleAlways = { vm.setFriction(app.packageName, it) },
                        onToggleIntent = { type, enabled ->
                            vm.setIntentRule(app.packageName, type, enabled)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppRow(
    app: AppEntry,
    onToggleAlways: (Boolean) -> Unit,
    onToggleIntent: (IntentType, Boolean) -> Unit
) {
    val context = LocalContext.current
    val icon: ImageBitmap? by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(app.packageName)
                    .toBitmap(96, 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // App icon
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon!!,
                        contentDescription = app.label,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                if (app.isFrictionEnabled) {
                    Text(
                        text = "Always on",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (app.frictionIntents.isNotEmpty()) {
                    Text(
                        text = "On during: ${app.frictionIntents.joinToString { it.label }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Switch(
                checked = app.isFrictionEnabled,
                onCheckedChange = onToggleAlways
            )
        }

        // Intent chips — always visible so the user can set up rules even with Always off
        Spacer(Modifier.height(6.dp))
        IntentChipRow(
            selectedIntents = app.frictionIntents,
            onToggle = { type, enabled -> onToggleIntent(type, enabled) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Intent chips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IntentChipRow(
    selectedIntents: Set<IntentType>,
    onToggle: (IntentType, Boolean) -> Unit
) {
    // Exclude JUST_LOOKING — friction during "just looking" defeats the purpose
    val types = IntentType.entries.filter { it != IntentType.JUST_LOOKING }

    Row(
        modifier = Modifier.padding(start = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        types.forEach { type ->
            val selected = type in selectedIntents
            FilterChip(
                selected = selected,
                onClick = { onToggle(type, !selected) },
                label = { Text(type.emoji, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
        }
    }
}
