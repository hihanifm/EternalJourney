package com.hanifm.eternaljourney.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.hanifm.eternaljourney.data.PreferencesManager
import com.hanifm.eternaljourney.util.Constants

class BluetoothDeviceManager(private val context: Context) {
    private val TAG = "${Constants.LOG_TAG}/BTManager"
    private val preferencesManager = PreferencesManager(context)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    init {
        Log.d(TAG, "BluetoothDeviceManager initialized, adapter=${bluetoothAdapter != null}")
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
        Log.d(TAG, "getPairedDevices: Getting paired devices")
        val devices = mutableListOf<BluetoothDeviceInfo>()
        val bondedDevices = bluetoothAdapter?.bondedDevices
        Log.d(TAG, "Found ${bondedDevices?.size ?: 0} bonded devices")
        
        bondedDevices?.forEach { device ->
            // First check cached connection state (from recent broadcast events)
            val cachedState = BluetoothConnectionStateManager.getCachedConnectionState(device.address)
            val isConnected = if (cachedState != null) {
                Log.d(TAG, "Using cached connection state for ${device.address}: $cachedState")
                cachedState
            } else {
                // Fallback to checking system state
                isDeviceConnected(device)
            }
            val isSelected = isDeviceSelected(device.address)
            Log.d(TAG, "Device: ${device.name} (${device.address}), connected=$isConnected, selected=$isSelected")
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
        Log.d(TAG, "Returning ${sortedDevices.size} devices")
        return sortedDevices
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            Log.d(TAG, "isDeviceConnected: Checking ${device.name} (${device.address})")
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothAdapter
            if (bluetoothManager == null || adapter == null || !adapter.isEnabled) {
                Log.d(TAG, "isDeviceConnected: Bluetooth not available or disabled")
                return false
            }
            
            // Method 1: Check Bluetooth profiles first (most reliable)
            val profilesToCheck = listOf(
                BluetoothProfile.A2DP,
                BluetoothProfile.HEADSET
            )
            
            for (profile in profilesToCheck) {
                try {
                    val connectedDevices = bluetoothManager.getConnectedDevices(profile)
                    val isConnected = connectedDevices.any { it.address == device.address }
                    if (isConnected) {
                        Log.d(TAG, "isDeviceConnected: Device found in connected devices for profile $profile")
                        return true
                    }
                } catch (e: SecurityException) {
                    Log.d(TAG, "isDeviceConnected: SecurityException for profile $profile: ${e.message}")
                    // Missing permission, skip
                    continue
                } catch (e: Exception) {
                    Log.d(TAG, "isDeviceConnected: Error checking profile $profile: ${e.message}")
                    // Other error, continue
                    continue
                }
            }
            
            // Method 2: Use reflection to check device connection state directly (works on most Android versions)
            try {
                val method = device.javaClass.getMethod("isConnected")
                val isConnected = method.invoke(device) as? Boolean
                Log.d(TAG, "isDeviceConnected: Reflection isConnected() returned: $isConnected")
                if (isConnected == true) return true
            } catch (e: NoSuchMethodException) {
                // Try alternative reflection method
                try {
                    val method = device.javaClass.getMethod("getConnectionState")
                    val state = method.invoke(device) as? Int
                    Log.d(TAG, "isDeviceConnected: Reflection getConnectionState() returned: $state")
                    if (state == BluetoothProfile.STATE_CONNECTED) return true
                } catch (e2: Exception) {
                    Log.d(TAG, "isDeviceConnected: Both reflection methods failed")
                    // Both reflection methods failed
                }
            } catch (e: Exception) {
                Log.d(TAG, "isDeviceConnected: Reflection failed: ${e.message}")
                // Reflection failed, try other methods
            }
            
            // Method 2: Check AudioManager for active Bluetooth audio routing (Android 8.0+)
            // If audio is routing to Bluetooth and this device is selected, it's likely connected
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val audioDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                    val hasBluetoothAudio = audioDevices?.any { 
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    } == true
                    
                    // If Bluetooth audio is active and this device is selected, assume it's connected
                    // This is a heuristic but works well in practice
                    if (hasBluetoothAudio && isDeviceSelected(device.address)) {
                        // Additional check: verify device name matches (if available from audio device)
                        val deviceName = audioDevices?.firstOrNull { 
                            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        }?.productName
                        
                        // If device name matches or we can't determine, assume connected if selected
                        if (deviceName == null || device.name.contains(deviceName, ignoreCase = true) || 
                            deviceName.contains(device.name, ignoreCase = true)) {
                            return true
                        }
                    }
                } catch (e: Exception) {
                    // Audio manager check failed
                }
            }
            
            // Method 3: Check AudioManager for active Bluetooth audio routing (Android 8.0+)
            // This is a fallback heuristic
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val audioDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                    val hasBluetoothAudio = audioDevices?.any { 
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    } == true
                    
                    Log.d(TAG, "isDeviceConnected: AudioManager hasBluetoothAudio=$hasBluetoothAudio, isSelected=${isDeviceSelected(device.address)}")
                    
                    // If Bluetooth audio is active and this device is selected, assume it's connected
                    // This is a heuristic but works well in practice
                    if (hasBluetoothAudio && isDeviceSelected(device.address)) {
                        // Additional check: verify device name matches (if available from audio device)
                        val deviceName = audioDevices?.firstOrNull { 
                            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        }?.productName
                        
                        // If device name matches or we can't determine, assume connected if selected
                        if (deviceName == null || device.name.contains(deviceName, ignoreCase = true) || 
                            deviceName.contains(device.name, ignoreCase = true)) {
                            Log.d(TAG, "isDeviceConnected: Assuming connected based on AudioManager heuristic")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "isDeviceConnected: AudioManager check failed: ${e.message}")
                    // Audio manager check failed
                }
            }
            
            Log.d(TAG, "isDeviceConnected: Device ${device.name} is NOT connected")
            false
        } catch (e: Exception) {
            Log.e(TAG, "isDeviceConnected: Exception checking connection", e)
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

