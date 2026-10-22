package com.personal.investmenttracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.personal.investmenttracker.ui.MainScreen
import com.personal.investmenttracker.ui.MainViewModel
import com.personal.investmenttracker.ui.theme.InvestmentTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settingsState.collect { settings ->
                    updateFlagSecure(settings.isFlagSecureEnabled)
                }
            }
        }

        setContent {
            InvestmentTrackerTheme {
                val dashboardState by viewModel.dashboardState.collectAsState()
                val holdingsState by viewModel.holdingsState.collectAsState()
                val historyState by viewModel.pnlHistoryState.collectAsState()
                val settingsState by viewModel.settingsState.collectAsState()

                MainScreen(
                    state =
                        com.personal.investmenttracker.ui.MainScreenState(
                            dashboard = dashboardState,
                            holdings = holdingsState,
                            history = historyState,
                            settings = settingsState,
                        ),
                    actions =
                        com.personal.investmenttracker.ui.MainScreenActions(
                            onSyncClick = { viewModel.syncAll() },
                            onLockClick = { viewModel.lockNow() },
                            onReconnectBroker = { /* Broker re-auth navigation */ },
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
