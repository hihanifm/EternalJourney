package com.hanifm.eternaljourney.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanifm.eternaljourney.audio.AudioFileInfo
import com.hanifm.eternaljourney.audio.AudioFileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioFilesViewModel(application: Application) : AndroidViewModel(application) {
    private val audioFileManager = AudioFileManager(application)

    private val _bundledAudioFiles = MutableStateFlow<List<AudioFileInfo>>(emptyList())
    val bundledAudioFiles: StateFlow<List<AudioFileInfo>> = _bundledAudioFiles.asStateFlow()

    private val _userAudioFiles = MutableStateFlow<List<AudioFileInfo>>(emptyList())
    val userAudioFiles: StateFlow<List<AudioFileInfo>> = _userAudioFiles.asStateFlow()

    private val _defaultAudioFile = MutableStateFlow<String?>(null)
    val defaultAudioFile: StateFlow<String?> = _defaultAudioFile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAudioFiles()
        loadDefaultAudioFile()
    }

    fun loadAudioFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _bundledAudioFiles.value = audioFileManager.getBundledAudioFiles()
                _userAudioFiles.value = audioFileManager.getUserAudioFiles()
                // Reload default in case it was automatically set
                loadDefaultAudioFile()
            } catch (e: Exception) {
                // Error loading audio files - handled silently
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadDefaultAudioFile() {
        _defaultAudioFile.value = audioFileManager.getDefaultAudioFile()
    }

    fun importAudioFile(uri: Uri, originalFileName: String, onSuccess: (AudioFileInfo) -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch {
            try {
                val importedFile = audioFileManager.importAudioFile(uri, originalFileName)
                if (importedFile != null) {
                    loadAudioFiles()
                    onSuccess(importedFile)
                } else {
                    onError(Exception("Failed to import audio file"))
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteAudioFile(fileName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch {
            try {
                val deleted = audioFileManager.deleteAudioFile(fileName)
                if (deleted) {
                    loadAudioFiles()
                    // If deleted file was default, clear default
                    if (_defaultAudioFile.value == fileName) {
                        setDefaultAudioFile(null)
                    }
                    onSuccess()
                } else {
                    onError(Exception("Failed to delete audio file"))
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun setDefaultAudioFile(fileName: String?) {
        viewModelScope.launch {
            audioFileManager.setDefaultAudioFile(fileName)
            _defaultAudioFile.value = fileName
        }
    }

    fun getAudioFileUri(fileName: String, isBundled: Boolean): Uri? {
        return audioFileManager.getAudioFileUri(fileName, isBundled)
    }
}

