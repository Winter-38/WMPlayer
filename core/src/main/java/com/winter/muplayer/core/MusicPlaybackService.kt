package com.winter.muplayer.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 后台播放 Service —— 基于 Media3 MediaSessionService 的 ForegroundService。
 *
 * 职责：
 * - 持有 MediaSession（与 ExoPlayer 绑定，自动同步播放状态与锁屏/通知控件）
 * - 显示带播放控制按钮的媒体通知
 * - 管理前台生命周期（播放时前台，停止时退前台）
 *
 * 控制流：
 * - 通知栏按钮 → 通过 [onStartCommand] 中的自定义 Action 路由到 [MusicPlayerCore]
 * - 锁屏 / 蓝牙 / Wear OS 等外部控制器 → Media3 自动将命令下发到 ExoPlayer
 * - MusicPlayerCore 通过 Player.Listener 同步状态，保证一致性
 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var notificationManager: NotificationManager

    // ====================== 生命周期 ======================

    override fun onCreate() {
        super.onCreate()
        currentService = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val player = obtainPlayer() ?: return

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildSessionPendingIntent())
            .build()

        addSession(mediaSession!!)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = obtainPlayer()
        if (player == null || (!player.playWhenReady && player.playbackState != Player.STATE_BUFFERING)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (currentService === this) currentService = null
        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** 处理通知按钮点击和自定义 Action */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                val core = MusicPlayerCore.getInstance(applicationContext)
                if (core.playerState.value.state == com.winter.muplayer.model.PlayerState.PLAYING) {
                    core.pause()
                } else {
                    core.play()
                }
            }
            ACTION_SKIP_NEXT -> MusicPlayerCore.getInstance(applicationContext).playNext()
            ACTION_SKIP_PREVIOUS -> MusicPlayerCore.getInstance(applicationContext).playPrevious()
            ACTION_STOP -> {
                stopForegroundPlayback()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // ====================== 通知 ======================

    /** 构建带播放控制的媒体通知 */
    private fun buildNotification(): android.app.Notification {
        val core = MusicPlayerCore.getInstance(applicationContext)
        val state = core.playerState.value
        val isPlaying = state.state == com.winter.muplayer.model.PlayerState.PLAYING
        val track = state.currentTrack

        val title = track?.title ?: getString(R.string.app_name)
        val artist = track?.artist ?: ""
        val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.winter.muplayer.core.R.drawable.ic_notification_music_note)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(buildSessionPendingIntent())
            .setDeleteIntent(buildPendingIntent(ACTION_STOP))
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.previous), buildPendingIntent(ACTION_SKIP_PREVIOUS))
            .addAction(icon, getString(if (isPlaying) R.string.pause else R.string.play), buildPendingIntent(ACTION_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, getString(R.string.next), buildPendingIntent(ACTION_SKIP_NEXT))
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .build()
    }

    // ====================== 前台管理 ======================

    /** 开始播放 → 进入前台并显示通知 */
    fun startForegroundPlayback() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    /** 暂停 → 退前台但保留通知（用户可展开继续播放） */
    fun pauseForegroundPlayback() {
        stopForeground(STOP_FOREGROUND_DETACH)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    /** 停止 → 移除通知 */
    fun stopForegroundPlayback() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** 更新现有通知（例如曲目切换、播放状态变化） */
    fun updateNotification() {
        if (mediaSession != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    // ====================== 内部工具 ======================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.playback_channel_desc)
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun obtainPlayer(): Player? {
        return try {
            val core = MusicPlayerCore.getInstance(applicationContext)
            (core.engine as com.winter.muplayer.core.engine.ExoPlayerEngine).exoPlayer
        } catch (_: Exception) { null }
    }

    private fun buildSessionPendingIntent(): PendingIntent {
        val target = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        } ?: Intent().apply {
            setClassName(packageName, "com.winter.muplayer.ui.activity.MusicUIActivity")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, REQUEST_CODE_OPEN_APP, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        var currentService: MusicPlaybackService? = null
            private set

        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_OPEN_APP = 100

        private const val ACTION_PLAY_PAUSE = "com.winter.muplayer.action.PLAY_PAUSE"
        private const val ACTION_SKIP_NEXT = "com.winter.muplayer.action.SKIP_NEXT"
        private const val ACTION_SKIP_PREVIOUS = "com.winter.muplayer.action.SKIP_PREVIOUS"
        private const val ACTION_STOP = "com.winter.muplayer.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MusicPlaybackService::class.java))
        }
    }
}
