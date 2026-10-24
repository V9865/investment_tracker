package com.personal.investmenttracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectZerodhaDialog(
    initialApiKey: String,
    initialApiSecret: String,
    actions: ConnectZerodhaActions,
    isExchanging: Boolean = false,
    errorMessage: String? = null,
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var apiSecret by remember { mutableStateOf(initialApiSecret) }
    var requestTokenOrUrl by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = {
            Text(
                text = "Connect Zerodha Kite",
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
                KiteCredentialsSection(
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    apiSecret = apiSecret,
                    onApiSecretChange = { apiSecret = it },
                    showSecret = showSecret,
                    onToggleSecret = { showSecret = !showSecret },
                    actions = actions,
                )

                Spacer(modifier = Modifier.height(20.dp))

                KiteTokenExchangeSection(
                    requestTokenOrUrl = requestTokenOrUrl,
                    onTokenChange = { requestTokenOrUrl = it },
                    errorMessage = errorMessage,
                    isExchanging = isExchanging,
                    onExchange = { actions.onExchangeToken(requestTokenOrUrl.trim()) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = actions.onDismiss) {
                Text("Close")
            }
        },
    )
}

data class ConnectZerodhaActions(
    val onDismiss: () -> Unit,
    val onSaveCredentials: (apiKey: String, apiSecret: String) -> Unit,
    val onOpenInAppLogin: (apiKey: String) -> Unit,
    val onOpenExternalBrowser: (apiKey: String) -> Unit,
    val onExchangeToken: (tokenOrUrl: String) -> Unit,
)

@Composable
@Suppress("LongParameterList")
private fun KiteCredentialsSection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    apiSecret: String,
    onApiSecretChange: (String) -> Unit,
    showSecret: Boolean,
    onToggleSecret: () -> Unit,
    actions: ConnectZerodhaActions,
) {
    Text(
        text = "Enter your Kite Developer credentials. Keys are saved in hardware-backed storage.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("API Key") },
        singleLine = true,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Zerodha API Key input" },
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = apiSecret,
        onValueChange = onApiSecretChange,
        label = { Text("API Secret") },
        singleLine = true,
        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleSecret) {
                Icon(
                    imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showSecret) "Hide Secret" else "Show Secret",
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Zerodha API Secret input" },
    )

    Spacer(modifier = Modifier.height(12.dp))

    KiteLoginButtons(apiKey = apiKey, apiSecret = apiSecret, actions = actions)
}

@Composable
private fun KiteLoginButtons(
    apiKey: String,
    apiSecret: String,
    actions: ConnectZerodhaActions,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { actions.onSaveCredentials(apiKey.trim(), apiSecret.trim()) },
            enabled = apiKey.isNotBlank() && apiSecret.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(4.dp))
            Text("Save Keys", fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.size(8.dp))

        Button(
            onClick = {
                actions.onSaveCredentials(apiKey.trim(), apiSecret.trim())
                actions.onOpenInAppLogin(apiKey.trim())
            },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.weight(1.3f),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text("Login (In-App)", fontSize = 12.sp)
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    OutlinedButton(
        onClick = {
            actions.onSaveCredentials(apiKey.trim(), apiSecret.trim())
            actions.onOpenExternalBrowser(apiKey.trim())
        },
        enabled = apiKey.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text("Or Open in Chrome / Browser", fontSize = 11.sp)
    }
}

@Composable
private fun KiteTokenExchangeSection(
    requestTokenOrUrl: String,
    onTokenChange: (String) -> Unit,
    errorMessage: String?,
    isExchanging: Boolean,
    onExchange: () -> Unit,
) {
    Text(
        text = "Request Token / Redirect URL",
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "If using external browser or manual capture, paste redirected URL or request_token below:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = requestTokenOrUrl,
        onValueChange = onTokenChange,
        label = { Text("Paste URL or Token") },
        placeholder = { Text("e.g. 127.0.0.1/?request_token=... or token") },
        singleLine = false,
        maxLines = 3,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Request token or redirect URL input" },
    )

    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = onExchange,
        enabled = requestTokenOrUrl.isNotBlank() && !isExchanging,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Complete Kite token exchange" },
    ) {
        if (isExchanging) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Connecting...")
        } else {
            Text("Complete Connection & Sync")
        }
    }
}
