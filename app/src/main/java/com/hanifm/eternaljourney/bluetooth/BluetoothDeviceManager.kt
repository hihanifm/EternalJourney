package com.hanifm.eternaljourney.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.hanifm.eternaljourney.data.PreferencesManager

class BluetoothDeviceManager(private val context: Context) {
    private val preferencesManager = PreferencesManager(context)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun getSelectedDevices(): Set<String> {
        return preferencesManager.getSelectedDeviceAddresses()
    }

    fun addDevice(address: String, name: String) {
        val currentDevices = preferencesManager.getSelectedDeviceAddresses().toMutableSet()
        currentDevices.add(address)
        preferencesManager.saveSelectedDeviceAddresses(currentDevices)
    }

    fun removeDevice(address: String) {
        val currentDevices = preferencesManager.getSelectedDeviceAddresses().toMutableSet()
        currentDevices.remove(address)
        preferencesManager.saveSelectedDeviceAddresses(currentDevices)
    }

    fun isDeviceSelected(address: String): Boolean {
        return preferencesManager.getSelectedDeviceAddresses().contains(address)
    }

    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        val devices = mutableListOf<BluetoothDeviceInfo>()
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            devices.add(
                BluetoothDeviceInfo(
                    address = device.address,
                    name = device.name ?: "Unknown Device",
                    isSelected = isDeviceSelected(device.address)
                )
            )
        }
        return devices.sortedBy { it.name }
    }
}

data class BluetoothDeviceInfo(
    val address: String,
    val name: String,
    val isSelected: Boolean
)

