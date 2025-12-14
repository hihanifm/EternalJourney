package com.hanifm.eternaljourney.audio

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.hanifm.eternaljourney.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class AudioFileManager(private val context: Context) {
    private val preferencesManager = PreferencesManager(context)
    private val userAudioDir: File = File(context.filesDir, "audio")

    init {
        // Create user audio directory if it doesn't exist
        if (!userAudioDir.exists()) {
            userAudioDir.mkdirs()
        }
    }

    suspend fun getBundledAudioFiles(): List<AudioFileInfo> = withContext(Dispatchers.IO) {
        val files = mutableListOf<AudioFileInfo>()
        try {
            val assets = context.assets.list("audio")
            assets?.forEach { fileName ->
                if (isAudioFile(fileName)) {
                    files.add(
                        AudioFileInfo(
                            fileName = fileName,
                            displayName = fileName.removeSuffix(getFileExtension(fileName)),
                            isBundled = true,
                            uri = getBundledAudioUri(fileName)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // assets/audio directory doesn't exist or can't be read
        }
        files.sortedBy { it.displayName }
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
        val extension = getFileExtension(fileName).lowercase()
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

