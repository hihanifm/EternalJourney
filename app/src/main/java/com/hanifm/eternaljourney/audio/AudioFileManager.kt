package com.hanifm.eternaljourney.audio

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.hanifm.eternaljourney.data.PreferencesManager
import com.hanifm.eternaljourney.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class AudioFileManager(private val context: Context) {
    private val TAG = "${Constants.LOG_TAG}/AudioFileManager"
    private val preferencesManager = PreferencesManager(context)
    private val userAudioDir: File = File(context.filesDir, "audio")

    init {
        Log.d(TAG, "AudioFileManager initialized, userAudioDir=${userAudioDir.absolutePath}")
        // Create user audio directory if it doesn't exist
        if (!userAudioDir.exists()) {
            userAudioDir.mkdirs()
            Log.d(TAG, "Created user audio directory")
        }
    }

    suspend fun getBundledAudioFiles(): List<AudioFileInfo> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getBundledAudioFiles: Loading bundled audio files")
        val files = mutableListOf<AudioFileInfo>()
        try {
            val assets = context.assets.list("audio")
            Log.d(TAG, "Found ${assets?.size ?: 0} files in assets/audio")
            assets?.forEach { fileName ->
                if (isAudioFile(fileName)) {
                    Log.d(TAG, "Adding bundled audio file: $fileName")
                    files.add(
                        AudioFileInfo(
                            fileName = fileName,
                            displayName = fileName.removeSuffix(getFileExtension(fileName)),
                            isBundled = true,
                            uri = getBundledAudioUri(fileName)
                        )
                    )
                } else {
                    Log.d(TAG, "Skipping non-audio file: $fileName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bundled audio files", e)
            // assets/audio directory doesn't exist or can't be read
        }
        val sortedFiles = files.sortedBy { it.displayName }
        Log.d(TAG, "getBundledAudioFiles: Returning ${sortedFiles.size} bundled files")
        
        // Ensure default is set if none exists
        ensureDefaultAudioFile(sortedFiles)
        
        sortedFiles
    }

    suspend fun getUserAudioFiles(): List<AudioFileInfo> = withContext(Dispatchers.IO) {
        val files = mutableListOf<AudioFileInfo>()
        userAudioDir.listFiles()?.forEach { file ->
            if (file.isFile && isAudioFile(file.name)) {
                files.add(
                    AudioFileInfo(
                        fileName = file.name,
                        displayName = file.nameWithoutExtension,
                        isBundled = false,
                        uri = getFileProviderUri(file)
                    )
                )
            }
        }
        files.sortedBy { it.displayName }
    }

    suspend fun getAllAudioFiles(): List<AudioFileInfo> = withContext(Dispatchers.IO) {
        getBundledAudioFiles() + getUserAudioFiles()
    }

    suspend fun importAudioFile(sourceUri: Uri, originalFileName: String): AudioFileInfo? = withContext(Dispatchers.IO) {
        try {
            val extension = getFileExtension(originalFileName)
            val sanitizedFileName = sanitizeFileName(originalFileName)
            val destinationFile = File(userAudioDir, sanitizedFileName)
            
            // Copy file
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            AudioFileInfo(
                fileName = sanitizedFileName,
                displayName = destinationFile.nameWithoutExtension,
                isBundled = false,
                uri = getFileProviderUri(destinationFile)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteAudioFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(userAudioDir, fileName)
            if (file.exists() && file.isFile) {
                file.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getPlayableUri(audioFileInfo: AudioFileInfo): Uri? = withContext(Dispatchers.IO) {
        Log.d(TAG, "getPlayableUri: Getting playable URI for ${audioFileInfo.fileName}, bundled=${audioFileInfo.isBundled}")
        try {
            if (audioFileInfo.isBundled) {
                // For bundled files, copy to temp file and return FileProvider URI
                val tempFile = File(context.cacheDir, "temp_play_${audioFileInfo.fileName}")
                Log.d(TAG, "Copying bundled file to temp: ${tempFile.absolutePath}")
                context.assets.open("audio/${audioFileInfo.fileName}").use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val bytesCopied = input.copyTo(output)
                        Log.d(TAG, "Copied $bytesCopied bytes to temp file")
                    }
                }
                val uri = getFileProviderUri(tempFile)
                Log.d(TAG, "Created playable URI: $uri")
                uri
            } else {
                // For user files, return the existing FileProvider URI
                Log.d(TAG, "Using existing URI for user file: ${audioFileInfo.uri}")
                audioFileInfo.uri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting playable URI", e)
            null
        }
    }

    fun getAudioFileUri(fileName: String, isBundled: Boolean): Uri? {
        return if (isBundled) {
            getBundledAudioUri(fileName)
        } else {
            val file = File(userAudioDir, fileName)
            if (file.exists()) {
                getFileProviderUri(file)
            } else {
                null
            }
        }
    }

    fun getDefaultAudioFile(): String? {
        return preferencesManager.getDefaultAudioFile()
    }

    fun setDefaultAudioFile(fileName: String?) {
        preferencesManager.setDefaultAudioFile(fileName)
        Log.d(TAG, "setDefaultAudioFile: Set default to $fileName")
    }
    
    /**
     * Ensures a default audio file is set. If no default exists, sets the first bundled audio file as default.
     */
    private suspend fun ensureDefaultAudioFile(bundledFiles: List<AudioFileInfo>) {
        val currentDefault = getDefaultAudioFile()
        if (currentDefault == null && bundledFiles.isNotEmpty()) {
            val firstBundledFile = bundledFiles.first()
            Log.i(TAG, "No default audio file set, setting first bundled file as default: ${firstBundledFile.fileName}")
            setDefaultAudioFile(firstBundledFile.fileName)
        } else if (currentDefault != null) {
            Log.d(TAG, "Default audio file already set: $currentDefault")
        } else {
            Log.d(TAG, "No bundled audio files available to set as default")
        }
    }

    private fun getBundledAudioUri(fileName: String): Uri {
        // For assets, we'll use a custom scheme that the AudioPlaybackService will handle
        return Uri.parse("asset://audio/$fileName")
    }

    private fun getFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun isAudioFile(fileName: String): Boolean {
        val extension = getFileExtension(fileName).lowercase().removePrefix(".")
        return extension in listOf("mp3", "m4a", "wav", "ogg", "aac", "flac", "mp4")
    }

    private fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot)
        } else {
            ""
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}

data class AudioFileInfo(
    val fileName: String,
    val displayName: String,
    val isBundled: Boolean,
    val uri: Uri
)

