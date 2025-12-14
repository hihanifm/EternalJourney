package com.hanifm.eternaljourney.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanifm.eternaljourney.bluetooth.BluetoothDeviceInfo
import com.hanifm.eternaljourney.bluetooth.BluetoothDeviceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceManager = BluetoothDeviceManager(application)

    private val _devices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BluetoothDeviceInfo>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadDevices()
    }

    fun loadDevices() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _devices.value = deviceManager.getPairedDevices()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleDeviceSelection(device: BluetoothDeviceInfo) {
        viewModelScope.launch {
            if (device.isSelected) {
                deviceManager.removeDevice(device.address)
            } else {
                deviceManager.addDevice(device.address, device.name)
            }
            loadDevices() // Reload to update selection state
        }
    }
}

