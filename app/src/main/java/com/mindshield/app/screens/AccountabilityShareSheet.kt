package com.mindshield.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Review a pending share before it goes out. Pure Compose piece — no Intent/
 * Context launching here (that's the caller's job via onSend), same
 * separation of concerns as BatchRuleSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountabilityShareSheet(
    label: String,
    initialText: String,
    partnerEmail: String,
    onSend: (editedText: String) -> Unit,
    onDiscard: () -> Unit,
    onEditSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                label,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Ready to send to $partnerEmail. Edit the wording if you'd like before sending.",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                label         = { Text("Message") },
                minLines      = 4,
                modifier      = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick  = {
                    onSend(text)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send") }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = {
                    onDiscard()
                    onDismiss()
                }) { Text("Discard") }

                TextButton(onClick = onEditSettings) { Text("Edit settings") }
            }
        }
    }
}
