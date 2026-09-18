package com.mindshield.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Configure the accountability partner: email, master enable switch, and the
 * three independent trigger toggles. Purely local state mirroring the params
 * (same pattern as BatchRuleSheet) — the caller is responsible for persisting
 * via AccountabilityPartnerStore.setPartner(...) from onSave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountabilityPartnerSetupSheet(
    partnerEmail: String,
    enabled: Boolean,
    triggerWeeklyDigest: Boolean,
    triggerSessionEnd: Boolean,
    triggerViolationAlert: Boolean,
    onSave: (email: String, enabled: Boolean, weekly: Boolean, sessionEnd: Boolean, violation: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf(partnerEmail) }
    var isEnabled by remember { mutableStateOf(enabled) }
    var weekly by remember { mutableStateOf(triggerWeeklyDigest) }
    var sessionEnd by remember { mutableStateOf(triggerSessionEnd) }
    var violation by remember { mutableStateOf(triggerViolationAlert) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                "Accountability Partner",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Share a friendly summary of your activity with someone you trust.",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value         = email,
                onValueChange = { email = it },
                label         = { Text("Partner's email") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier      = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            ToggleRow(
                title    = "Enabled",
                caption  = "Turn this off at any time to stop all sharing, regardless of the triggers below.",
                checked  = isEnabled,
                onCheckedChange = { isEnabled = it }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                "Triggers",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            ToggleRow(
                title    = "Weekly digest",
                caption  = "A short summary of your week, ready to send once a week.",
                checked  = weekly,
                onCheckedChange = { weekly = it }
            )
            ToggleRow(
                title    = "When a session ends",
                caption  = "A summary of that session, ready to send as soon as it finishes.",
                checked  = sessionEnd,
                onCheckedChange = { sessionEnd = it }
            )
            ToggleRow(
                title    = "When you override a pause",
                caption  = "A note that you opened an app anyway, ready to send right after.",
                checked  = violation,
                onCheckedChange = { violation = it }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick  = {
                    onSave(email, isEnabled, weekly, sessionEnd, violation)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
