package com.winter.muplayer.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class MusicPlaybackService : MediaSessionService() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MusicPlaybackService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel("music_playback", context.getString(R.string.playback_channel_name), NotificationManager.IMPORTANCE_LOW)
                ch.setShowBadge(false)
                ch.setSound(null, null)
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
            }
        }
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        val player = getExoPlayer()
        if (player != null) {
            mediaSession = MediaSession.Builder(this, player).build()
            addSession(mediaSession!!)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = getExoPlayer()
        if (player == null || (!player.playWhenReady && player.playbackState != Player.STATE_BUFFERING)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.let { removeSession(it); it.release() }
        mediaSession = null
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun getExoPlayer(): Player? {
        return try {
            val core = MusicPlayerCore.getInstance(applicationContext)
            (core.engine as com.winter.muplayer.core.engine.ExoPlayerEngine).exoPlayer
        } catch (_: Exception) { null }
    }
}
