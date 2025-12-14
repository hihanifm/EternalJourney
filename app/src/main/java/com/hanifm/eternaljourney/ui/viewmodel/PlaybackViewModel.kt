package com.hanifm.eternaljourney.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanifm.eternaljourney.data.PreferencesManager
import com.hanifm.eternaljourney.service.AudioPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    
    private var audioService: AudioPlaybackService? = null
    private var isServiceBound = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _autoPlayEnabled = MutableStateFlow(preferencesManager.isAutoPlayEnabled())
    val autoPlayEnabled: StateFlow<Boolean> = _autoPlayEnabled.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioPlaybackService.LocalBinder
            audioService = binder?.getService()
            isServiceBound = true
            updatePlaybackState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isServiceBound = false
        }
    }

    init {
        bindToService()
    }

    private fun bindToService() {
        val intent = Intent(getApplication(), AudioPlaybackService::class.java)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun playAudio(uri: String) {
        val intent = Intent(getApplication(), AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_PLAY
            putExtra(AudioPlaybackService.EXTRA_AUDIO_URI, uri)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().startService(intent)
        }
        updatePlaybackState()
    }

    fun pause() {
        val intent = Intent(getApplication(), AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_PAUSE
        }
        getApplication<Application>().startService(intent)
        updatePlaybackState()
    }

    fun resume() {
        audioService?.resume()
        updatePlaybackState()
    }

    fun stop() {
        val intent = Intent(getApplication(), AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
        updatePlaybackState()
    }

    private fun updatePlaybackState() {
        viewModelScope.launch {
            _isPlaying.value = audioService?.isPlaying() == true
        }
    }

    fun setAutoPlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoPlayEnabled(enabled)
            _autoPlayEnabled.value = enabled
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isServiceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}

