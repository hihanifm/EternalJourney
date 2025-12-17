package com.dito.travelbuddy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.widget.Toast
import com.dito.travelbuddy.MainActivity
import com.dito.travelbuddy.R
import com.dito.travelbuddy.audio.AudioFileManager
import com.dito.travelbuddy.util.LogUtils
import java.io.File

class AudioPlaybackService : MediaSessionService(), AudioManager.OnAudioFocusChangeListener {
    private val TAG = "/AudioService"
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val binder = LocalBinder()
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var wasPlayingWhenFocusLost = false

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_playback_channel"
        const val ACTION_PLAY = "com.dito.travelbuddy.ACTION_PLAY"
        const val ACTION_PAUSE = "com.dito.travelbuddy.ACTION_PAUSE"
        const val ACTION_STOP = "com.dito.travelbuddy.ACTION_STOP"
        const val EXTRA_AUDIO_URI = "audio_uri"
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        LogUtils.d(TAG, "onCreate: Service created")
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initializePlayer()
        LogUtils.d(TAG, "onCreate: Service initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtils.d(TAG, "onStartCommand: action=${intent?.action}, startId=$startId")
        when (intent?.action) {
            ACTION_PLAY -> {
                val audioUri = intent.getStringExtra(EXTRA_AUDIO_URI)
                LogUtils.d(TAG, "ACTION_PLAY received, URI: $audioUri")
                if (audioUri != null) {
                    playAudio(Uri.parse(audioUri))
                } else {
                    LogUtils.w(TAG, "ACTION_PLAY received but no URI provided")
                }
            }
            ACTION_PAUSE -> {
                LogUtils.d(TAG, "ACTION_PAUSE received")
                pause()
            }
            ACTION_STOP -> {
                LogUtils.d(TAG, "ACTION_STOP received")
                stopPlayback()
            }
            else -> {
                LogUtils.d(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun initializePlayer() {
        LogUtils.d(TAG, "initializePlayer: Creating ExoPlayer")
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    LogUtils.d(TAG, "Playback state changed: $playbackState")
                    updateNotification()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    LogUtils.d(TAG, "Playing state changed: isPlaying=$isPlaying")
                    updateNotification()
                }
            })
        }

        mediaSession = MediaSession.Builder(this, exoPlayer!!).build()
        LogUtils.d(TAG, "MediaSession created")
        startForeground(NOTIFICATION_ID, createNotification())
        LogUtils.d(TAG, "Service started in foreground")
    }

    fun playAudio(uri: Uri) {
        LogUtils.i(TAG, "playAudio: Starting playback, URI=$uri")
        requestAudioFocus()
        
        val mediaItem = try {
            if (uri.scheme == "asset") {
                LogUtils.d(TAG, "Handling asset URI")
                val fileName = uri.pathSegments.lastOrNull()
                LogUtils.d(TAG, "Asset file name: $fileName")
                if (fileName == null) {
                    LogUtils.e(TAG, "No file name in asset URI")
                    Toast.makeText(this, "Invalid audio file", Toast.LENGTH_SHORT).show()
                    return
                }
                val assetUri = createAssetUri(fileName)
                if (assetUri != Uri.EMPTY) {
                    LogUtils.d(TAG, "Created asset URI: $assetUri")
                    MediaItem.fromUri(assetUri)
                } else {
                    LogUtils.e(TAG, "Failed to create asset URI")
                    Toast.makeText(this, "Failed to load audio file", Toast.LENGTH_SHORT).show()
                    return
                }
            } else {
                LogUtils.d(TAG, "Handling regular URI")
                MediaItem.fromUri(uri)
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error creating MediaItem", e)
            Toast.makeText(this, "Error loading audio: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        exoPlayer?.apply {
            LogUtils.d(TAG, "Setting media item and starting playback")
            setMediaItem(mediaItem)
            prepare()
            play()
            // Let this service manage audio focus explicitly via AudioManager.
            // Disable ExoPlayer's built-in audio focus handling to avoid
            // double-registration and immediate AUDIOFOCUS_LOSS callbacks.
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false
            )
            LogUtils.i(TAG, "Playback started successfully")
            Toast.makeText(this@AudioPlaybackService, "Playing audio", Toast.LENGTH_SHORT).show()
        } ?: run {
            LogUtils.e(TAG, "ExoPlayer is null, cannot play audio")
            Toast.makeText(this, "Player not initialized", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createAssetUri(fileName: String): Uri {
        LogUtils.d(TAG, "createAssetUri: Copying asset to temp file: $fileName")
        return try {
            val tempFile = File(cacheDir, "temp_audio_$fileName")
            LogUtils.d(TAG, "Temp file path: ${tempFile.absolutePath}")
            assets.open("audio/$fileName").use { input ->
                tempFile.outputStream().use { output ->
                    val bytesCopied = input.copyTo(output)
                    LogUtils.d(TAG, "Copied $bytesCopied bytes from asset to temp file")
                }
            }
            val uri = Uri.fromFile(tempFile)
            LogUtils.d(TAG, "Created URI from temp file: $uri")
            uri
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error copying asset to temp file", e)
            Uri.EMPTY
        }
    }

    fun pause() {
        LogUtils.d(TAG, "pause: Pausing playback")
        exoPlayer?.pause()
        Toast.makeText(this, "Playback paused", Toast.LENGTH_SHORT).show()
    }

    fun resume() {
        LogUtils.d(TAG, "resume: Resuming playback")
        requestAudioFocus()
        exoPlayer?.play()
        Toast.makeText(this, "Playback resumed", Toast.LENGTH_SHORT).show()
    }

    fun stopPlayback() {
        LogUtils.i(TAG, "stopPlayback: Stopping playback and service")
        exoPlayer?.stop()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) {
            LogUtils.d(TAG, "requestAudioFocus: Already has audio focus")
            return
        }

        LogUtils.d(TAG, "requestAudioFocus: Requesting audio focus")
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        LogUtils.d(TAG, "requestAudioFocus: Result=${if (hasAudioFocus) "GRANTED" else "DENIED"}")
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) {
            LogUtils.d(TAG, "abandonAudioFocus: No audio focus to abandon")
            return
        }

        LogUtils.d(TAG, "abandonAudioFocus: Abandoning audio focus")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(this)
        }
        hasAudioFocus = false
        LogUtils.d(TAG, "abandonAudioFocus: Audio focus abandoned")
    }

    override fun onAudioFocusChange(focusChange: Int) {
        LogUtils.d(TAG, "onAudioFocusChange: focusChange=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                LogUtils.d(TAG, "Audio focus gained")
                if (wasPlayingWhenFocusLost) {
                    LogUtils.d(TAG, "Resuming playback after focus gain")
                    resume()
                }
                wasPlayingWhenFocusLost = false
                hasAudioFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                LogUtils.d(TAG, "Audio focus lost (transient)")
                wasPlayingWhenFocusLost = isPlaying()
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                LogUtils.d(TAG, "Audio focus lost (permanent)")
                wasPlayingWhenFocusLost = isPlaying()
                pause()
                hasAudioFocus = false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for audio playback"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying()) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                createActionPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                createActionPendingIntent(ACTION_PLAY)
            )
        }

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            createActionPendingIntent(ACTION_STOP)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eternal Journey")
            .setContentText(if (isPlaying()) "Playing audio" else "Audio paused")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1)
                    .setMediaSession(mediaSession?.sessionCompatToken)
            )
            .build()
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        LogUtils.d(TAG, "onDestroy: Service being destroyed")
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        abandonAudioFocus()
        LogUtils.d(TAG, "onDestroy: Service destroyed")
    }
}

