package com.dito.travelbuddy.bluetooth

import com.dito.travelbuddy.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectionStateChange(
    val deviceAddress: String,
    val isConnected: Boolean
)

object BluetoothConnectionStateManager {
    private val TAG = "/BTStateManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _connectionStateChanged = MutableStateFlow<ConnectionStateChange?>(null)
    val connectionStateChanged: StateFlow<ConnectionStateChange?> = _connectionStateChanged.asStateFlow()
    
    private val connectionStateCache = mutableMapOf<String, Boolean>()

    fun notifyConnectionChanged(deviceAddress: String, isConnected: Boolean) {
        LogUtils.d(TAG, "notifyConnectionChanged: Device=$deviceAddress, isConnected=$isConnected")
        connectionStateCache[deviceAddress] = isConnected
        _connectionStateChanged.value = ConnectionStateChange(deviceAddress, isConnected)
        scope.launch {
            delay(100)
            _connectionStateChanged.value = null
            LogUtils.d(TAG, "Connection state reset")
        }
    }
    
    fun getCachedConnectionState(deviceAddress: String): Boolean? {
        return connectionStateCache[deviceAddress]
    }
    
    fun clearCache() {
        connectionStateCache.clear()
        LogUtils.d(TAG, "Connection state cache cleared")
    }
}
