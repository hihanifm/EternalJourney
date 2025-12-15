package com.hanifm.eternaljourney.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.Toast
import android.util.Log
import com.hanifm.eternaljourney.audio.AudioFileManager
import com.hanifm.eternaljourney.bluetooth.BluetoothConnectionStateManager
import com.hanifm.eternaljourney.bluetooth.BluetoothDeviceManager
import com.hanifm.eternaljourney.data.PreferencesManager
import com.hanifm.eternaljourney.service.AudioPlaybackService
import com.hanifm.eternaljourney.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BluetoothConnectionReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "${Constants.LOG_TAG}/BTReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Log.d(TAG, "Bluetooth device connected")
                handleDeviceConnected(context, intent)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Log.d(TAG, "Bluetooth device disconnected")
                handleDeviceDisconnected(context, intent)
            }
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                Log.d(TAG, "Audio becoming noisy - Bluetooth may have disconnected")
                // Audio output is becoming noisy (e.g., Bluetooth disconnected)
                // Service will handle routing automatically
            }
            else -> {
                Log.d(TAG, "Unknown action: ${intent.action}")
            }
        }
    }

    private fun handleDeviceConnected(context: Context, intent: Intent) {
        val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        device?.let {
            Log.d(TAG, "Device connected: name=${it.name}, address=${it.address}")
            val deviceManager = BluetoothDeviceManager(context)
            val preferencesManager = PreferencesManager(context)

            // Notify connection state change (connected = true)
            BluetoothConnectionStateManager.notifyConnectionChanged(it.address, isConnected = true)
            Log.d(TAG, "Notified connection state manager: device=${it.address}, connected=true")

            // Show toast notification
            scope.launch {
                Toast.makeText(context, "Bluetooth connected: ${it.name}", Toast.LENGTH_SHORT).show()
            }

            // Check if auto-play is enabled and device is selected
            val isSelected = deviceManager.isDeviceSelected(it.address)
            val autoPlayEnabled = preferencesManager.isAutoPlayEnabled()
            Log.d(TAG, "Auto-play check: enabled=$autoPlayEnabled, selected=$isSelected")
            
            if (autoPlayEnabled && isSelected) {
                Log.i(TAG, "Starting auto-play for device: ${it.name}")
                startPlayback(context)
            } else {
                Log.d(TAG, "Auto-play not triggered: enabled=$autoPlayEnabled, selected=$isSelected")
            }
        } ?: run {
            Log.w(TAG, "Device connected but device object is null")
        }
    }

    private fun handleDeviceDisconnected(context: Context, intent: Intent) {
        val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        device?.let {
            Log.d(TAG, "Device disconnected: name=${it.name}, address=${it.address}")
            val deviceManager = BluetoothDeviceManager(context)
            
            // Notify connection state change (connected = false)
            BluetoothConnectionStateManager.notifyConnectionChanged(it.address, isConnected = false)
            Log.d(TAG, "Notified connection state manager: device=${it.address}, connected=false")

            // Show toast notification
            scope.launch {
                Toast.makeText(context, "Bluetooth disconnected: ${it.name}", Toast.LENGTH_SHORT).show()
            }
            
            // If this was a selected device, audio will continue on phone speaker
            // The service handles this automatically via audio routing
            if (deviceManager.isDeviceSelected(it.address)) {
                Log.d(TAG, "Selected device disconnected, audio will continue on phone speaker")
                // Audio continues playing on phone speaker as per requirements
            }
        } ?: run {
            Log.w(TAG, "Device disconnected but device object is null")
        }
    }

    private fun startPlayback(context: Context) {
        Log.d(TAG, "startPlayback: Starting auto-play")
        val audioFileManager = AudioFileManager(context)
        val defaultAudioFile = audioFileManager.getDefaultAudioFile()
        Log.d(TAG, "Default audio file: $defaultAudioFile")

        scope.launch {
            if (defaultAudioFile != null) {
                // Get all audio files to find the default one
                val audioFiles = try {
                    Log.d(TAG, "Loading audio files to find default: $defaultAudioFile")
                    val bundled = audioFileManager.getBundledAudioFiles()
                    val user = audioFileManager.getUserAudioFiles()
                    Log.d(TAG, "Found ${bundled.size} bundled files, ${user.size} user files")
                    (bundled + user).firstOrNull { it.fileName == defaultAudioFile }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading audio files", e)
                    null
                }

                audioFiles?.let { file ->
                    Log.i(TAG, "Starting playback of: ${file.fileName}, URI: ${file.uri}")
                    val playIntent = Intent(context, AudioPlaybackService::class.java).apply {
                        action = AudioPlaybackService.ACTION_PLAY
                        putExtra(AudioPlaybackService.EXTRA_AUDIO_URI, file.uri.toString())
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(playIntent)
                        } else {
                            @Suppress("DEPRECATION")
                            context.startService(playIntent)
                        }
                        Log.d(TAG, "Playback service started successfully")
                        Toast.makeText(context, "Playing: ${file.displayName}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting playback service", e)
                        Toast.makeText(context, "Error starting playback", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Log.w(TAG, "Default audio file not found: $defaultAudioFile")
                    Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
                }
            } else {
                // No default file set, get first available
                Log.d(TAG, "No default file set, trying first available")
                try {
                    val audioFiles = audioFileManager.getBundledAudioFiles() + audioFileManager.getUserAudioFiles()
                    Log.d(TAG, "Found ${audioFiles.size} total audio files")
                    audioFiles.firstOrNull()?.let { file ->
                        Log.i(TAG, "Starting playback of first available: ${file.fileName}")
                        val playIntent = Intent(context, AudioPlaybackService::class.java).apply {
                            action = AudioPlaybackService.ACTION_PLAY
                            putExtra(AudioPlaybackService.EXTRA_AUDIO_URI, file.uri.toString())
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(playIntent)
                        } else {
                            @Suppress("DEPRECATION")
                            context.startService(playIntent)
                        }
                        Toast.makeText(context, "Playing: ${file.displayName}", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Log.w(TAG, "No audio files available to play")
                        Toast.makeText(context, "No audio files available", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading audio files for playback", e)
                    Toast.makeText(context, "Error loading audio files", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

