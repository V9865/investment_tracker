package com.personal.investmenttracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.personal.investmenttracker.data.broker.zerodha.KiteAuthHelper
import com.personal.investmenttracker.data.security.LockState
import com.personal.investmenttracker.data.sync.SyncStatus
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.ui.MainScreen
import com.personal.investmenttracker.ui.MainScreenActions
import com.personal.investmenttracker.ui.MainScreenState
import com.personal.investmenttracker.ui.MainViewModel
import com.personal.investmenttracker.ui.components.ConnectKotakDialog
import com.personal.investmenttracker.ui.components.ConnectZerodhaActions
import com.personal.investmenttracker.ui.components.ConnectZerodhaDialog
import com.personal.investmenttracker.ui.components.KiteLoginWebViewDialog
import com.personal.investmenttracker.ui.security.UnlockScreen
import com.personal.investmenttracker.ui.theme.InvestmentTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.IOException

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val openKotakFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        runCatching {
                            java.io.File(filesDir, "kotak_holdings.csv").writeBytes(bytes)
                        }
                        viewModel.importKotakFile(bytes.inputStream()) { result ->
                            runOnUiThread {
                                val msg =
                                    when (val s = result.status) {
                                        is SyncStatus.Success ->
                                            "Imported ${s.holdingsCount} Kotak holdings"
                                        is SyncStatus.Failure ->
                                            "Import failed: ${s.error}"
                                    }
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: IOException) {
                    Toast.makeText(this, "Could not read file: ${e.message}", Toast.LENGTH_LONG).show()
                } catch (e: SecurityException) {
                    Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleRedirectIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settingsState.collect { settings ->
                    updateFlagSecure(settings.isFlagSecureEnabled)
                }
            }
        }

        setContent {
            InvestmentTrackerTheme {
                MainAppRoot(
                    viewModel = viewModel,
                    onShowBiometrics = { showBiometricPrompt() },
                    onOpenCustomTab = { openCustomTab(it) },
                    onLaunchKotakFilePicker = {
                        openKotakFileLauncher.launch(arrayOf("text/*", "application/*"))
                    },
                    onShowToast = { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirectIntent(intent)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.onUserInteraction()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.onAppBackgrounded()
        }
    }

    private fun handleRedirectIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val raw = data.toString()
        val token = KiteAuthHelper.extractRequestToken(raw)
        if (!token.isNullOrBlank()) {
            viewModel.exchangeZerodhaToken(token) { result ->
                runOnUiThread {
                    if (result.isSuccess) {
                        Toast.makeText(this, "Zerodha connected via redirect!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast
                            .makeText(
                                this,
                                "Zerodha redirect failed: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt() {
        val canAuth =
            BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )

        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            viewModel.onUserAuthenticated()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt =
            BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        viewModel.onUserAuthenticated()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                },
            )

        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Unlock Investment Tracker")
                .setSubtitle("Use your biometric or screen lock (PIN / Pattern / Password)")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                ).build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (_: SecurityException) {
            // Biometric hardware security failure
        }
    }

    private fun openCustomTab(url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(this, Uri.parse(url))
        } catch (_: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        }
    }

    private fun updateFlagSecure(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
@Suppress("LongMethod", "FunctionNaming", "CyclomaticComplexMethod")
private fun MainAppRoot(
    viewModel: MainViewModel,
    onShowBiometrics: () -> Unit,
    onOpenCustomTab: (String) -> Unit,
    onLaunchKotakFilePicker: () -> Unit,
    onShowToast: (String) -> Unit,
) {
    val lockState by viewModel.lockState.collectAsState()
    val dashboardState by viewModel.dashboardState.collectAsState()
    val holdingsState by viewModel.holdingsState.collectAsState()
    val historyState by viewModel.pnlHistoryState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()

    var activeConnectBroker by remember { mutableStateOf<Broker?>(null) }
    var kiteError by remember { mutableStateOf<String?>(null) }
    var isExchangingKite by remember { mutableStateOf(false) }
    var kiteLoginUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lockState) {
        if (lockState is LockState.Locked) {
            onShowBiometrics()
        }
    }

    when (lockState) {
        is LockState.Locked, is LockState.PermanentlyInvalidated -> {
            UnlockScreen(
                lockState = lockState,
                onUnlockClick = onShowBiometrics,
                onResetDataClick = { viewModel.eraseAllData() },
            )
        }
        is LockState.Unlocked -> {
            MainScreen(
                state =
                    MainScreenState(
                        dashboard = dashboardState,
                        holdings = holdingsState,
                        history = historyState,
                        settings = settingsState,
                    ),
                actions =
                    MainScreenActions(
                        onSyncClick = { viewModel.syncAll() },
                        onLockClick = { viewModel.lockNow() },
                        onReconnectBroker = { broker -> activeConnectBroker = broker },
                        onViewModeSelected = { viewModel.setViewMode(it) },
                        onSortSelected = { viewModel.setSortOption(it) },
                        onLockTimeoutChange = { /* Configurable timeout */ },
                        onFlagSecureToggle = { viewModel.setFlagSecure(it) },
                        onDisconnectBroker = { viewModel.disconnectBroker(it) },
                        onEraseAllData = { viewModel.eraseAllData() },
                    ),
            )
        }
    }

    when (activeConnectBroker) {
        Broker.ZERODHA -> {
            ConnectZerodhaDialog(
                initialApiKey = viewModel.getZerodhaApiKey() ?: "",
                initialApiSecret = viewModel.getZerodhaApiSecret() ?: "",
                actions =
                    ConnectZerodhaActions(
                        onDismiss = {
                            activeConnectBroker = null
                            kiteError = null
                        },
                        onSaveCredentials = { key, secret ->
                            viewModel.saveZerodhaCredentials(key, secret)
                            onShowToast("Zerodha credentials saved")
                        },
                        onOpenInAppLogin = { key ->
                            kiteLoginUrl = KiteAuthHelper.buildLoginUrl(key)
                        },
                        onOpenExternalBrowser = { key ->
                            val loginUrl = KiteAuthHelper.buildLoginUrl(key)
                            onOpenCustomTab(loginUrl)
                        },
                        onExchangeToken = { tokenOrUrl ->
                            isExchangingKite = true
                            kiteError = null
                            viewModel.exchangeZerodhaToken(tokenOrUrl) { result ->
                                isExchangingKite = false
                                if (result.isSuccess) {
                                    activeConnectBroker = null
                                    onShowToast("Zerodha connected successfully!")
                                } else {
                                    kiteError = result.exceptionOrNull()?.message ?: "Token exchange failed"
                                }
                            }
                        },
                    ),
                isExchanging = isExchangingKite,
                errorMessage = kiteError,
            )
        }
        Broker.KOTAK_NEO -> {
            ConnectKotakDialog(
                onDismiss = { activeConnectBroker = null },
                onLaunchFilePicker = onLaunchKotakFilePicker,
                onReloadHoldings = {
                    viewModel.syncKotak()
                    onShowToast("Reloading Kotak holdings")
                },
            )
        }
        null -> {}
    }

    val activeLoginUrl = kiteLoginUrl
    if (activeLoginUrl != null) {
        KiteLoginWebViewDialog(
            loginUrl = activeLoginUrl,
            onDismiss = { kiteLoginUrl = null },
            onTokenCaptured = { token ->
                kiteLoginUrl = null
                isExchangingKite = true
                kiteError = null
                viewModel.exchangeZerodhaToken(token) { result ->
                    isExchangingKite = false
                    if (result.isSuccess) {
                        activeConnectBroker = null
                        onShowToast("Zerodha connected successfully!")
                    } else {
                        kiteError = result.exceptionOrNull()?.message ?: "Token exchange failed"
                    }
                }
            },
        )
    }
}
