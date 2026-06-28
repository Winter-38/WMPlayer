package com.winter.muplayer.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/**
 * 前台媒体播放服务。
 *
 * 当播放开始时启动，在通知栏显示媒体控制。
 * 通过 Foreground Service 机制防止进程被系统杀死，实现后台播放。
 */
class MusicPlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.winter.muplayer.action.START"
        private const val ACTION_STOP = "com.winter.muplayer.action.STOP"

        /** 启动前台播放服务 */
        fun start(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止前台播放服务 */
        fun stop(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /** 创建通知渠道 */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "播放控制",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "显示播放控制通知"
                    setShowBadge(false)
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val player = getExoPlayer()
                if (player != null && mediaSession == null) {
                    mediaSession = MediaSession.Builder(this, player).build()
                }
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_STOP -> {
                mediaSession?.release()
                mediaSession = null
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = getExoPlayer()
        if (player == null || !player.playWhenReady) {
            mediaSession?.release()
            mediaSession = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 通过单例获取 ExoPlayer */
    private fun getExoPlayer(): Player? {
        return try {
            val core = MusicPlayerCore.getInstance(applicationContext)
            (core.engine as? com.winter.muplayer.core.engine.ExoPlayerEngine)?.exoPlayer
        } catch (_: Exception) {
            null
        }
    }

    /** 构建媒体通知 */
    private fun buildNotification(): Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WinterMuPlayer")
            .setContentText("正在播放")
            .setContentIntent(contentPendingIntent)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
