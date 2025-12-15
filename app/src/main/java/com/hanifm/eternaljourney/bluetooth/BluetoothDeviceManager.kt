package com.hanifm.eternaljourney.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.hanifm.eternaljourney.data.PreferencesManager
import com.hanifm.eternaljourney.util.LogUtils

class BluetoothDeviceManager(private val context: Context) {
    private val TAG = "/BTManager"
    private val preferencesManager = PreferencesManager(context)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    init {
        LogUtils.d(TAG, "BluetoothDeviceManager initialized, adapter=${bluetoothAdapter != null}")
    }

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
        LogUtils.d(TAG, "getPairedDevices: Getting paired devices")
        val devices = mutableListOf<BluetoothDeviceInfo>()
        val bondedDevices = bluetoothAdapter?.bondedDevices
        LogUtils.d(TAG, "Found ${bondedDevices?.size ?: 0} bonded devices")
        
        bondedDevices?.forEach { device ->
            val cachedState = BluetoothConnectionStateManager.getCachedConnectionState(device.address)
            val isConnected = if (cachedState != null) {
                LogUtils.d(TAG, "Using cached connection state for ${device.address}: $cachedState")
                cachedState
            } else {
                isDeviceConnected(device)
            }
            val isSelected = isDeviceSelected(device.address)
            LogUtils.d(TAG, "Device: ${device.name} (${device.address}), connected=$isConnected, selected=$isSelected")
            devices.add(
                BluetoothDeviceInfo(
                    address = device.address,
                    name = device.name ?: "Unknown Device",
                    isSelected = isSelected,
                    isConnected = isConnected
                )
            )
        }
        val sortedDevices = devices.sortedBy { it.name }
        LogUtils.d(TAG, "Returning ${sortedDevices.size} devices")
        return sortedDevices
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            LogUtils.d(TAG, "isDeviceConnected: Checking ${device.name} (${device.address})")
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothAdapter
            if (bluetoothManager == null || adapter == null || !adapter.isEnabled) {
                LogUtils.d(TAG, "isDeviceConnected: Bluetooth not available or disabled")
                return false
            }
            
            val profilesToCheck = listOf(
                BluetoothProfile.A2DP,
                BluetoothProfile.HEADSET
            )
            
            for (profile in profilesToCheck) {
                try {
                    val connectedDevices = bluetoothManager.getConnectedDevices(profile)
                    if (connectedDevices.any { it.address == device.address }) {
                        LogUtils.d(TAG, "isDeviceConnected: Device found in connected devices for profile $profile")
                        return true
                    }
                } catch (e: SecurityException) {
                    LogUtils.d(TAG, "isDeviceConnected: SecurityException for profile $profile: ${e.message}")
                    continue
                } catch (e: Exception) {
                    LogUtils.d(TAG, "isDeviceConnected: Error checking profile $profile: ${e.message}")
                    continue
                }
            }
            
            try {
                val method = device.javaClass.getMethod("isConnected")
                val isConnected = method.invoke(device) as? Boolean
                LogUtils.d(TAG, "isDeviceConnected: Reflection isConnected() returned: $isConnected")
                if (isConnected == true) return true
            } catch (e: NoSuchMethodException) {
                try {
                    val method = device.javaClass.getMethod("getConnectionState")
                    val state = method.invoke(device) as? Int
                    LogUtils.d(TAG, "isDeviceConnected: Reflection getConnectionState() returned: $state")
                    if (state == BluetoothProfile.STATE_CONNECTED) return true
                } catch (e2: Exception) {
                    LogUtils.d(TAG, "isDeviceConnected: Both reflection methods failed")
                }
            } catch (e: Exception) {
                LogUtils.d(TAG, "isDeviceConnected: Reflection failed: ${e.message}")
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val audioDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                    val hasBluetoothAudio = audioDevices?.any { 
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    } == true
                    
                    LogUtils.d(TAG, "isDeviceConnected: AudioManager hasBluetoothAudio=$hasBluetoothAudio, isSelected=${isDeviceSelected(device.address)}")
                    
                    if (hasBluetoothAudio && isDeviceSelected(device.address)) {
                        val deviceName = audioDevices?.firstOrNull { 
                            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        }?.productName
                        
                        if (deviceName == null || device.name.contains(deviceName, ignoreCase = true) || 
                            deviceName.contains(device.name, ignoreCase = true)) {
                            LogUtils.d(TAG, "isDeviceConnected: Assuming connected based on AudioManager heuristic")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.d(TAG, "isDeviceConnected: AudioManager check failed: ${e.message}")
                }
            }
            
            LogUtils.d(TAG, "isDeviceConnected: Device ${device.name} is NOT connected")
            false
        } catch (e: Exception) {
            LogUtils.e(TAG, "Exception checking connection", e)
            false
        }
    }
}

data class BluetoothDeviceInfo(
    val address: String,
    val name: String,
    val isSelected: Boolean,
    val isConnected: Boolean = false
)

