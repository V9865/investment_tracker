package com.personal.investmenttracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            PrivacyTopBar(onNavigateBack = onNavigateBack)
        },
        modifier = modifier,
    ) { innerPadding ->
        PrivacyContent(innerPadding = innerPadding)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "Privacy & Architecture",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Navigate back"
                    },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun PrivacyContent(innerPadding: PaddingValues) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        PrivacyItemCard(
            title = "100% On-Device Execution",
            description =
                "There is no server, no VPS, no backend, and no " +
                    "intermediary database. Every calculation, portfolio grouping, " +
                    "and snapshot is computed entirely on your phone.",
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacyItemCard(
            title = "Direct, Read-Only Broker Traffic",
            description =
                "Network requests travel directly from this device " +
                    "to Zerodha Kite (api.kite.trade) and Kotak Neo (trade.kotakneo.com) " +
                    "over encrypted HTTPS. Strict network interceptors reject any order, " +
                    "trade, modify, cancel, or fund transfer call before it ever touches " +
                    "the network.",
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacyItemCard(
            title = "Zero Analytics & Zero Telemetry",
            description =
                "This application contains zero tracking libraries, " +
                    "zero ad SDKs, zero crash-reporting services, and zero analytics. " +
                    "No telemetry of any kind is collected.",
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacyItemCard(
            title = "Hardware Keystore & SQLCipher Encryption",
            description =
                "Your local portfolio database is encrypted with SQLCipher " +
                    "256-bit AES. The database encryption key is protected by Android " +
                    "Keystore hardware security and bound to your biometric authentication.",
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacyItemCard(
            title = "Data Backups Disabled",
            description =
                "Android auto-backup and cloud extraction are explicitly " +
                    "disabled via Android security configuration (allowBackup=false).",
        )
    }
}

@Composable
private fun PrivacyItemCard(
    title: String,
    description: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}
