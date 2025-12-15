package com.hanifm.eternaljourney.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanifm.eternaljourney.bluetooth.BluetoothConnectionStateManager
import com.hanifm.eternaljourney.bluetooth.BluetoothDeviceInfo
import com.hanifm.eternaljourney.bluetooth.BluetoothDeviceManager
import com.hanifm.eternaljourney.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "${Constants.LOG_TAG}/DeviceSelectionVM"
    private val deviceManager = BluetoothDeviceManager(application)

    private val _devices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BluetoothDeviceInfo>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        Log.d(TAG, "ViewModel initialized")
        loadDevices()
        
        // Observe connection state changes and refresh device list
        viewModelScope.launch {
            BluetoothConnectionStateManager.connectionStateChanged.collectLatest { stateChange ->
                if (stateChange != null) {
                    Log.d(TAG, "Connection state changed: device=${stateChange.deviceAddress}, connected=${stateChange.isConnected}, refreshing list")
                    // Refresh devices when connection state changes
                    // No delay needed since we're using cached state
                    loadDevices()
                }
            }
        }
    }

    fun loadDevices() {
        Log.d(TAG, "loadDevices: Loading device list")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deviceList = deviceManager.getPairedDevices()
                _devices.value = deviceList
                Log.d(TAG, "loadDevices: Loaded ${deviceList.size} devices")
            } catch (e: Exception) {
                Log.e(TAG, "loadDevices: Error loading devices", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleDeviceSelection(device: BluetoothDeviceInfo) {
        Log.d(TAG, "toggleDeviceSelection: Device=${device.name}, currently selected=${device.isSelected}")
        viewModelScope.launch {
            if (device.isSelected) {
                deviceManager.removeDevice(device.address)
                Log.d(TAG, "Removed device from selection: ${device.name}")
            } else {
                deviceManager.addDevice(device.address, device.name)
                Log.d(TAG, "Added device to selection: ${device.name}")
            }
            loadDevices() // Reload to update selection state
        }
    }
}

