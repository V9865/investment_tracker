package com.personal.investmenttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.investmenttracker.domain.model.Broker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    var brokerToDisconnect by remember { mutableStateOf<Broker?>(null) }
    var showEraseDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Privacy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            SecurityCard(state = state, actions = actions)

            Spacer(modifier = Modifier.height(24.dp))

            BrokerConnectionsCard(
                state = state,
                onSelectDisconnect = { brokerToDisconnect = it },
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrivacyCard(onNavigateToPrivacy = actions.onNavigateToPrivacy)

            Spacer(modifier = Modifier.height(24.dp))

            DangerZoneCard(onOpenEraseDialog = { showEraseDialog = true })
        }
    }

    DisconnectDialog(
        broker = brokerToDisconnect,
        onDismiss = { brokerToDisconnect = null },
        onConfirm = {
            actions.onDisconnectBroker(it)
            brokerToDisconnect = null
        },
    )

    EraseDialog(
        show = showEraseDialog,
        onDismiss = { showEraseDialog = false },
        onConfirm = {
            showEraseDialog = false
            actions.onEraseAllData()
        },
    )
}

data class SettingsUiState(
    val lockTimeoutMinutes: Int = 5,
    val isFlagSecureEnabled: Boolean = true,
    val isZerodhaConnected: Boolean = true,
    val isKotakConnected: Boolean = true,
)

data class SettingsActions(
    val onLockTimeoutChange: (Int) -> Unit = {},
    val onFlagSecureToggle: (Boolean) -> Unit = {},
    val onLockNow: () -> Unit = {},
    val onDisconnectBroker: (Broker) -> Unit = {},
    val onEraseAllData: () -> Unit = {},
    val onNavigateToPrivacy: () -> Unit = {},
)

@Composable
private fun SecurityCard(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    Text(
        text = "Security & Biometric Lock",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Screen Protection (FLAG_SECURE)",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Blanks app in recent tasks and blocks screenshots",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.isFlagSecureEnabled,
                    onCheckedChange = actions.onFlagSecureToggle,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Toggle screenshot and recents protection"
                        },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Inactivity Lock: ${state.lockTimeoutMinutes} minutes",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = actions.onLockNow,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Lock app immediately" },
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text("Lock App Now")
            }
        }
    }
}

@Composable
private fun BrokerConnectionsCard(
    state: SettingsUiState,
    onSelectDisconnect: (Broker) -> Unit,
) {
    Text(
        text = "Broker Connections",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            BrokerConnectionRow(
                brokerName = "Zerodha Kite",
                isConnected = state.isZerodhaConnected,
                onDisconnect = { onSelectDisconnect(Broker.ZERODHA) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            BrokerConnectionRow(
                brokerName = "Kotak Neo",
                isConnected = state.isKotakConnected,
                onDisconnect = { onSelectDisconnect(Broker.KOTAK_NEO) },
            )
        }
    }
}

@Composable
private fun PrivacyCard(onNavigateToPrivacy: () -> Unit) {
    Card(
        onClick = onNavigateToPrivacy,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Open privacy and architecture details"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.padding(start = 12.dp))
                Column {
                    Text(
                        text = "Privacy & Data Architecture",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "No server, no cloud, data stays strictly on phone",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun DangerZoneCard(onOpenEraseDialog: () -> Unit) {
    Text(
        text = "Danger Zone",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Erase All Local Data",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    "Wipes the encrypted database, clears all broker " +
                        "credentials, deletes Keystore keys, and resets the " +
                        "app. This cannot be undone.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenEraseDialog,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Erase all local application data" },
            ) {
                Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text("Erase Everything & Reset")
            }
        }
    }
}

@Composable
private fun DisconnectDialog(
    broker: Broker?,
    onDismiss: () -> Unit,
    onConfirm: (Broker) -> Unit,
) {
    if (broker == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect ${broker.displayName}?") },
        text = {
            Text(
                "This will remove stored API credentials for ${broker.displayName}. " +
                    "Your saved local holdings will remain until refreshed or erased.",
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(broker) }) {
                Text("Confirm Disconnect", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EraseDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Erase All Local Data?") },
        text = {
            Text(
                "This permanently deletes your encrypted database, encryption " +
                    "keys, and credentials from this phone. You will need to " +
                    "set up your brokers again.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text("Erase & Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BrokerConnectionRow(
    brokerName: String,
    isConnected: Boolean,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = brokerName,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitleColor =
                if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            Text(
                text = if (isConnected) "Connected & Saved" else "Not configured",
                fontSize = 12.sp,
                color = subtitleColor,
            )
        }
        if (isConnected) {
            OutlinedButton(
                onClick = onDisconnect,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Disconnect $brokerName"
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.padding(start = 4.dp))
                Text("Disconnect", fontSize = 12.sp)
            }
        }
    }
}
