package com.goreecloud.dialer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Read-only GC UI Compose island inside the existing Android Settings screen.
 * No permissions, Phone-role requests, contacts, calls or persistence here.
 */
@Composable
internal fun DialerPrivacyPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Privacy and safety", style = MaterialTheme.typography.titleMedium)
            Text(
                "No Internet permission, contact uploads, recording, AI screening or tracking. " +
                    "The platform controls SIM, emergency routing and call audio. " +
                    "Use a secondary test phone until real call and emergency-routing review is complete.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
