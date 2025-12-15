package com.hanifm.eternaljourney.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanifm.eternaljourney.bluetooth.BluetoothConnectionStateManager
import com.hanifm.eternaljourney.bluetooth.BluetoothDeviceInfo
import com.hanifm.eternaljourney.bluetooth.BluetoothDeviceManager
import com.hanifm.eternaljourney.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "/DeviceSelectionVM"
    private val deviceManager = BluetoothDeviceManager(application)

    private val _devices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BluetoothDeviceInfo>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        LogUtils.d(TAG, "ViewModel initialized")
        loadDevices()
        
        viewModelScope.launch {
            BluetoothConnectionStateManager.connectionStateChanged.collectLatest { stateChange ->
                if (stateChange != null) {
                    LogUtils.d(TAG, "Connection state changed: device=${stateChange.deviceAddress}, connected=${stateChange.isConnected}, refreshing list")
                    loadDevices()
                }
            }
        }
    }

    fun loadDevices() {
        LogUtils.d(TAG, "loadDevices: Loading device list")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deviceList = deviceManager.getPairedDevices()
                _devices.value = deviceList
                LogUtils.d(TAG, "loadDevices: Loaded ${deviceList.size} devices")
            } catch (e: Exception) {
                LogUtils.e(TAG, "loadDevices: Error loading devices", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleDeviceSelection(device: BluetoothDeviceInfo) {
        LogUtils.d(TAG, "toggleDeviceSelection: Device=${device.name}, currently selected=${device.isSelected}")
        viewModelScope.launch {
            if (device.isSelected) {
                deviceManager.removeDevice(device.address)
                LogUtils.d(TAG, "Removed device from selection: ${device.name}")
            } else {
                deviceManager.addDevice(device.address, device.name)
                LogUtils.d(TAG, "Added device to selection: ${device.name}")
            }
            loadDevices()
        }
    }
}

