package com.personal.investmenttracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
@Suppress("LongParameterList")
fun ConnectKotakDialog(
    initialAccessToken: String,
    initialSid: String,
    onDismiss: () -> Unit,
    onSaveCredentials: (accessToken: String, sid: String?) -> Unit,
    onLaunchFilePicker: () -> Unit,
    errorMessage: String? = null,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var accessToken by remember { mutableStateOf(initialAccessToken) }
    var sid by remember { mutableStateOf(initialSid) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect Kotak Neo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("API Token") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Import File") },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    KotakApiTokenTab(
                        accessToken = accessToken,
                        onAccessTokenChange = { accessToken = it },
                        sid = sid,
                        onSidChange = { sid = it },
                        errorMessage = errorMessage,
                        onSave = {
                            onSaveCredentials(accessToken.trim(), sid.trim().ifEmpty { null })
                            onDismiss()
                        },
                    )
                } else {
                    KotakFileImportTab(
                        onSelectFile = {
                            onLaunchFilePicker()
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
@Suppress("LongParameterList")
private fun KotakApiTokenTab(
    accessToken: String,
    onAccessTokenChange: (String) -> Unit,
    sid: String,
    onSidChange: (String) -> Unit,
    errorMessage: String?,
    onSave: () -> Unit,
) {
    Text(
        text = "Generate your session access token from trade.kotakneo.com/trade-api and paste below:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = accessToken,
        onValueChange = onAccessTokenChange,
        label = { Text("Access Token / Bearer Token") },
        singleLine = false,
        maxLines = 3,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Kotak Access Token input" },
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = sid,
        onValueChange = onSidChange,
        label = { Text("Session ID (SID) - Optional") },
        singleLine = true,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Kotak SID input" },
    )

    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onSave,
        enabled = accessToken.isNotBlank(),
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Save Kotak Token and Sync" },
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text("Save & Sync Portfolio")
    }
}

@Composable
private fun KotakFileImportTab(onSelectFile: () -> Unit) {
    Text(
        text =
            "Kotak Neo API requires a registered static IP. If your phone's dynamic IP is rejected " +
                "by Kotak, export your equity holdings CSV from Kotak Neo and import it directly into the " +
                "encrypted local database.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(20.dp))

    OutlinedButton(
        onClick = onSelectFile,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Choose Kotak CSV or TSV file" },
    ) {
        Icon(
            imageVector = Icons.Default.FileOpen,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text("Choose CSV / TSV File")
    }
}
