package com.hanifm.eternaljourney.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.hanifm.eternaljourney.audio.AudioFileManager
import com.hanifm.eternaljourney.bluetooth.BluetoothDeviceManager
import com.hanifm.eternaljourney.data.PreferencesManager
import com.hanifm.eternaljourney.service.AudioPlaybackService

class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                handleDeviceConnected(context, intent)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                handleDeviceDisconnected(context, intent)
            }
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                // Audio output is becoming noisy (e.g., Bluetooth disconnected)
                // Service will handle routing automatically
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
            val deviceManager = BluetoothDeviceManager(context)
            val preferencesManager = PreferencesManager(context)

            // Check if auto-play is enabled and device is selected
            if (preferencesManager.isAutoPlayEnabled() && deviceManager.isDeviceSelected(it.address)) {
                startPlayback(context)
            }
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
            val deviceManager = BluetoothDeviceManager(context)
            // If this was a selected device, audio will continue on phone speaker
            // The service handles this automatically via audio routing
            if (deviceManager.isDeviceSelected(it.address)) {
                // Audio continues playing on phone speaker as per requirements
            }
        }
    }

    private fun startPlayback(context: Context) {
        val audioFileManager = AudioFileManager(context)
        val defaultAudioFile = audioFileManager.getDefaultAudioFile()

        if (defaultAudioFile != null) {
            // Get all audio files to find the default one
            val audioFiles = try {
                val bundled = audioFileManager.getBundledAudioFiles()
                val user = audioFileManager.getUserAudioFiles()
                (bundled + user).firstOrNull { it.fileName == defaultAudioFile }
            } catch (e: Exception) {
                null
            }

            audioFiles?.let { file ->
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
            }
        } else {
            // No default file set, get first available
            try {
                val audioFiles = audioFileManager.getBundledAudioFiles() + audioFileManager.getUserAudioFiles()
                audioFiles.firstOrNull()?.let { file ->
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

