package com.example.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun SyncDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Real-Time Cloud Sync") },
        text = { 
            Text(
                "To enable Real-Time Cloud Sync across your devices, a Firebase project integration is required. " +
                "Since this requires your personal google-services.json configuration, it is currently running in Secure Offline mode with End-to-End Encryption."
            )
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("dialog_ok")) {
                Text("Understood")
            }
        }
    )
}
