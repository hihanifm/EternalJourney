package com.hanifm.eternaljourney.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.Toast
import com.hanifm.eternaljourney.audio.AudioFileManager
import com.hanifm.eternaljourney.bluetooth.BluetoothConnectionStateManager
import com.hanifm.eternaljourney.bluetooth.BluetoothDeviceManager
import com.hanifm.eternaljourney.data.PreferencesManager
import com.hanifm.eternaljourney.service.AudioPlaybackService
import com.hanifm.eternaljourney.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BluetoothConnectionReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "/BTReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        LogUtils.d(TAG, "onReceive: action=${intent.action}")
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                LogUtils.d(TAG, "Bluetooth device connected")
                handleDeviceConnected(context, intent)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                LogUtils.d(TAG, "Bluetooth device disconnected")
                handleDeviceDisconnected(context, intent)
            }
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                LogUtils.d(TAG, "Audio becoming noisy - Bluetooth may have disconnected")
                // Audio output is becoming noisy (e.g., Bluetooth disconnected)
                // Service will handle routing automatically
            }
            else -> {
                LogUtils.d(TAG, "Unknown action: ${intent.action}")
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
            LogUtils.d(TAG, "Device connected: name=${it.name}, address=${it.address}")
            val deviceManager = BluetoothDeviceManager(context)
            val preferencesManager = PreferencesManager(context)

            BluetoothConnectionStateManager.notifyConnectionChanged(it.address, isConnected = true)
            LogUtils.d(TAG, "Notified connection state manager: device=${it.address}, connected=true")

            scope.launch {
                Toast.makeText(context, "Bluetooth connected: ${it.name}", Toast.LENGTH_SHORT).show()
            }

            val isSelected = deviceManager.isDeviceSelected(it.address)
            val autoPlayEnabled = preferencesManager.isAutoPlayEnabled()
            LogUtils.d(TAG, "Auto-play check: enabled=$autoPlayEnabled, selected=$isSelected")
            
            if (autoPlayEnabled && isSelected) {
                LogUtils.i(TAG, "Starting auto-play for device: ${it.name}")
                startPlayback(context)
            } else {
                LogUtils.d(TAG, "Auto-play not triggered: enabled=$autoPlayEnabled, selected=$isSelected")
            }
        } ?: run {
            LogUtils.w(TAG, "Device connected but device object is null")
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
            LogUtils.d(TAG, "Device disconnected: name=${it.name}, address=${it.address}")
            val deviceManager = BluetoothDeviceManager(context)
            
            BluetoothConnectionStateManager.notifyConnectionChanged(it.address, isConnected = false)
            LogUtils.d(TAG, "Notified connection state manager: device=${it.address}, connected=false")

            scope.launch {
                Toast.makeText(context, "Bluetooth disconnected: ${it.name}", Toast.LENGTH_SHORT).show()
            }
            
            if (deviceManager.isDeviceSelected(it.address)) {
                LogUtils.d(TAG, "Selected device disconnected, audio will continue on phone speaker")
            }
        } ?: run {
            LogUtils.w(TAG, "Device disconnected but device object is null")
        }
    }

    private fun startPlayback(context: Context) {
        LogUtils.d(TAG, "startPlayback: Starting auto-play")
        val audioFileManager = AudioFileManager(context)
        val defaultAudioFile = audioFileManager.getDefaultAudioFile()
        LogUtils.d(TAG, "Default audio file: $defaultAudioFile")

        scope.launch {
            if (defaultAudioFile != null) {
                val audioFiles = try {
                    LogUtils.d(TAG, "Loading audio files to find default: $defaultAudioFile")
                    val bundled = audioFileManager.getBundledAudioFiles()
                    val user = audioFileManager.getUserAudioFiles()
                    LogUtils.d(TAG, "Found ${bundled.size} bundled files, ${user.size} user files")
                    (bundled + user).firstOrNull { it.fileName == defaultAudioFile }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error loading audio files", e)
                    null
                }

                audioFiles?.let { file ->
                    LogUtils.i(TAG, "Starting playback of: ${file.fileName}, URI: ${file.uri}")
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
                        LogUtils.d(TAG, "Playback service started successfully")
                        Toast.makeText(context, "Playing: ${file.displayName}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error starting playback service", e)
                        Toast.makeText(context, "Error starting playback", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    LogUtils.w(TAG, "Default audio file not found: $defaultAudioFile")
                    Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
                }
            } else {
                LogUtils.d(TAG, "No default file set, trying first available")
                try {
                    val audioFiles = audioFileManager.getBundledAudioFiles() + audioFileManager.getUserAudioFiles()
                    LogUtils.d(TAG, "Found ${audioFiles.size} total audio files")
                    audioFiles.firstOrNull()?.let { file ->
                        LogUtils.i(TAG, "Starting playback of first available: ${file.fileName}")
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
                        LogUtils.w(TAG, "No audio files available to play")
                        Toast.makeText(context, "No audio files available", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error loading audio files for playback", e)
                    Toast.makeText(context, "Error loading audio files", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

