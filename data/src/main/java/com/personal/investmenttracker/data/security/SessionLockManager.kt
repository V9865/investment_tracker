package com.personal.investmenttracker.data.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LockState {
    object Locked : LockState()

    object Unlocked : LockState()

    data class PermanentlyInvalidated(
        val reason: String,
    ) : LockState()
}

interface LockableStorage {
    fun closeAndLock()

    fun deleteDatabaseAndReset()
}

class SessionLockManager(
    private val lockableStorage: LockableStorage,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val lockTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private val _lockState = MutableStateFlow<LockState>(LockState.Locked)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private var timeoutJob: Job? = null

    var isFlagSecureEnabled: Boolean = true
        private set

    fun setFlagSecure(enabled: Boolean) {
        isFlagSecureEnabled = enabled
    }

    fun onUserAuthenticated() {
        timeoutJob?.cancel()
        _lockState.value = LockState.Unlocked
        scheduleRelock()
    }

    fun onKeyPermanentlyInvalidated(reason: String) {
        timeoutJob?.cancel()
        lockableStorage.closeAndLock()
        _lockState.value = LockState.PermanentlyInvalidated(reason)
    }

    fun onUserInteraction() {
        if (_lockState.value is LockState.Unlocked) {
            scheduleRelock()
        }
    }

    fun onAppBackgrounded() {
        lockNow()
    }

    fun lockNow() {
        timeoutJob?.cancel()
        lockableStorage.closeAndLock()
        if (_lockState.value !is LockState.PermanentlyInvalidated) {
            _lockState.value = LockState.Locked
        }
    }

    fun resetDataAndResync(masterKeyManager: MasterKeyManager) {
        timeoutJob?.cancel()
        lockableStorage.deleteDatabaseAndReset()
        masterKeyManager.deleteMasterKey()
        _lockState.value = LockState.Locked
    }

    private fun scheduleRelock() {
        timeoutJob?.cancel()
        timeoutJob =
            scope.launch {
                delay(lockTimeoutMillis)
                lockNow()
            }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 300_000L // 5 minutes inactivity
    }
}
