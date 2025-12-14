package com.hanifm.eternaljourney.service

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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.NotificationStyles
import com.hanifm.eternaljourney.MainActivity
import com.hanifm.eternaljourney.R
import com.hanifm.eternaljourney.audio.AudioFileManager
import java.io.File

class AudioPlaybackService : MediaSessionService(), AudioManager.OnAudioFocusChangeListener {
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
        const val ACTION_PLAY = "com.hanifm.eternaljourney.ACTION_PLAY"
        const val ACTION_PAUSE = "com.hanifm.eternaljourney.ACTION_PAUSE"
        const val ACTION_STOP = "com.hanifm.eternaljourney.ACTION_STOP"
        const val EXTRA_AUDIO_URI = "audio_uri"
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initializePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val audioUri = intent.getStringExtra(EXTRA_AUDIO_URI)
                if (audioUri != null) {
                    playAudio(Uri.parse(audioUri))
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopPlayback()
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
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateNotification()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateNotification()
                }
            })
        }

        mediaSession = MediaSession.Builder(this, exoPlayer!!).build()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun playAudio(uri: Uri) {
        requestAudioFocus()
        
        val mediaItem = try {
            if (uri.scheme == "asset") {
                // Handle asset files - copy to temp file for ExoPlayer
                val fileName = uri.pathSegments.lastOrNull() ?: return
                val assetUri = createAssetUri(fileName)
                if (assetUri != Uri.EMPTY) {
                    MediaItem.fromUri(assetUri)
                } else {
                    return
                }
            } else {
                MediaItem.fromUri(uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                true
            )
        }
    }

    private fun createAssetUri(fileName: String): Uri {
        // Copy asset to temp file and return file URI
        return try {
            val tempFile = File(cacheDir, "temp_audio_$fileName")
            // Always copy to ensure file is fresh
            assets.open("audio/$fileName").use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Uri.EMPTY
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        requestAudioFocus()
        exoPlayer?.play()
    }

    fun stopPlayback() {
        exoPlayer?.stop()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioFocusRequest.AUDIOFOCUS_GAIN)
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
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return

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
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingWhenFocusLost) {
                    resume()
                }
                wasPlayingWhenFocusLost = false
                hasAudioFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                wasPlayingWhenFocusLost = isPlaying()
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
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
                androidx.media3.ui.NotificationStyles.MediaStyle()
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
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        abandonAudioFocus()
    }
}

