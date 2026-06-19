package com.yue.browser.data.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object MediaSessionManager {
    private const val CHANNEL_ID = "media_playback_channel"
    private const val NOTIFICATION_ID = 2026

    private var mediaSession: MediaSession? = null
    private var activeSessionId: String? = null
    private var activeSession: SystemWebViewSession? = null
    private var isPlayingState: Boolean = false
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentArtworkUrl: String = ""
    private var currentArtworkBitmap: Bitmap? = null

    fun isMediaPlaying(): Boolean = isPlayingState

    private var isReceiverRegistered = false
    private val mediaButtonReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                "com.yue.browser.MEDIA_PLAY_PAUSE" -> {
                    if (isPlayingState) onPauseTriggered() else onPlayTriggered()
                }
                "com.yue.browser.MEDIA_NEXT" -> onNextTriggered()
                "com.yue.browser.MEDIA_PREV" -> onPrevTriggered()
            }
        }
    }

    private fun getNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getNotificationManager(context)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Media Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Controls media playback in Yue Browser"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun registerReceiverIfNeeded(context: Context) {
        if (!isReceiverRegistered) {
            val filter = android.content.IntentFilter().apply {
                addAction("com.yue.browser.MEDIA_PLAY_PAUSE")
                addAction("com.yue.browser.MEDIA_NEXT")
                addAction("com.yue.browser.MEDIA_PREV")
            }
            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(mediaButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(mediaButtonReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    private fun getOrCreateMediaSession(context: Context, session: SystemWebViewSession): MediaSession {
        val currentSession = mediaSession
        if (currentSession != null && activeSessionId == session.id) {
            return currentSession
        }

        currentSession?.release()

        val newMediaSession = MediaSession(context.applicationContext, "YueBrowserMediaSession")
        newMediaSession.isActive = true
        newMediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                activeSession?.evaluateJavascript(
                    "(function() { var v = document.querySelector('video'); if (v) v.play(); })();",
                    null
                )
            }

            override fun onPause() {
                activeSession?.evaluateJavascript(
                    "(function() { var v = document.querySelector('video'); if (v) v.pause(); })();",
                    null
                )
            }

            override fun onSkipToNext() {
                activeSession?.evaluateJavascript(
                    """
                    (function() {
                        if (window.navigator.mediaSession && window.navigator.mediaSession._actionHandlers && window.navigator.mediaSession._actionHandlers['nexttrack']) {
                            window.navigator.mediaSession._actionHandlers['nexttrack']();
                        } else {
                            var nextBtn = document.querySelector('.ytp-next-button') || document.querySelector('.ytm-next-button');
                            if (nextBtn) nextBtn.click();
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }

            override fun onSkipToPrevious() {
                activeSession?.evaluateJavascript(
                    """
                    (function() {
                        if (window.navigator.mediaSession && window.navigator.mediaSession._actionHandlers && window.navigator.mediaSession._actionHandlers['previoustrack']) {
                            window.navigator.mediaSession._actionHandlers['previoustrack']();
                        } else {
                            var prevBtn = document.querySelector('.ytp-prev-button') || document.querySelector('.ytm-prev-button');
                            if (prevBtn) prevBtn.click();
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        })

        mediaSession = newMediaSession
        activeSessionId = session.id
        activeSession = session
        registerReceiverIfNeeded(context)
        return newMediaSession
    }

    fun onPlayTriggered() {
        mediaSession?.controller?.transportControls?.play()
    }

    fun onPauseTriggered() {
        mediaSession?.controller?.transportControls?.pause()
    }

    fun onNextTriggered() {
        mediaSession?.controller?.transportControls?.skipToNext()
    }

    fun onPrevTriggered() {
        mediaSession?.controller?.transportControls?.skipToPrevious()
    }

    fun updatePlaybackState(context: Context, session: SystemWebViewSession, isPlaying: Boolean) {
        if (session.isDestroyed) {
            if (!isPlaying && activeSessionId == session.id) {
                releaseSession(context, session.id)
            }
            return
        }
        android.util.Log.d("MediaSessionManager", "updatePlaybackState called for sessionId: ${session.id}, isPlaying: $isPlaying, activeSessionId: $activeSessionId")
        val currentSession = mediaSession
        if (currentSession == null) {
            if (!isPlaying) {
                return
            }
        } else if (activeSessionId != session.id) {
            if (!isPlaying) {
                return
            }
        }

        val mSession = getOrCreateMediaSession(context, session)
        isPlayingState = isPlaying

        if (session.isPrivate) {
            currentTitle = "Private Playback"
            currentArtist = "Yue Browser"
            currentArtworkUrl = ""
            currentArtworkBitmap = null
        }

        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                1.0f
            )
        mSession.setPlaybackState(stateBuilder.build())

        showOrUpdateNotification(context)

        // Update PiP window action buttons if we are currently in PiP mode
        com.yue.browser.MainActivity.getActiveActivity()?.let { activity ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity.isInPictureInPictureMode) {
                activity.updatePipParams()
            }
        }
    }

    fun updateMetadata(
        context: Context,
        session: SystemWebViewSession,
        title: String,
        artist: String,
        album: String,
        artworkUrl: String
    ) {
        val currentSession = mediaSession
        if (currentSession == null || activeSessionId != session.id) {
            return
        }

        val mSession = currentSession

        if (session.isPrivate) {
            currentTitle = "Private Playback"
            currentArtist = "Yue Browser"
            currentArtworkUrl = ""
            currentArtworkBitmap = null

            val metaBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Private Playback")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Yue Browser")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "")
            
            mSession.setMetadata(metaBuilder.build())
            showOrUpdateNotification(context)
            return
        }

        currentTitle = title
        currentArtist = artist

        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, album)

        if (artworkUrl != currentArtworkUrl) {
            currentArtworkUrl = artworkUrl
            currentArtworkBitmap = null
            if (artworkUrl.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = downloadBitmap(artworkUrl)
                    if (bitmap != null) {
                        currentArtworkBitmap = bitmap
                        withContext(Dispatchers.Main) {
                            if (activeSessionId == session.id) {
                                metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
                                metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, bitmap)
                                mSession.setMetadata(metaBuilder.build())
                                showOrUpdateNotification(context)
                            }
                        }
                    }
                }
            }
        } else if (currentArtworkBitmap != null) {
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, currentArtworkBitmap)
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, currentArtworkBitmap)
        }

        mSession.setMetadata(metaBuilder.build())
        showOrUpdateNotification(context)
    }

    fun releaseSession(context: Context, sessionId: String) {
        android.util.Log.d("MediaSessionManager", "releaseSession called for sessionId: $sessionId, activeSessionId: $activeSessionId")
        if (activeSessionId == sessionId || activeSession?.id == sessionId) {
            mediaSession?.release()
            mediaSession = null
            activeSessionId = null
            activeSession = null
            isPlayingState = false
            currentTitle = ""
            currentArtist = ""
            currentArtworkBitmap = null
            currentArtworkUrl = ""
            cancelNotification(context)
            android.util.Log.d("MediaSessionManager", "Media session released and notification cancelled for sessionId: $sessionId")
        }
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return try {
            val safeUrlStr = if (urlStr.startsWith("//")) {
                "https:$urlStr"
            } else {
                urlStr
            }
            val url = URL(safeUrlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            null
        }
    }

    private fun showOrUpdateNotification(context: Context) {
        ensureChannelCreated(context)
        val mSession = mediaSession ?: return

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val isPrivate = activeSession?.isPrivate ?: false
        val colorHex = if (isPrivate) "#FF002C" else "#EC4899"

        builder
            .setContentTitle(if (currentTitle.isNotEmpty()) currentTitle else "Yue Browser Video")
            .setContentText(if (currentArtist.isNotEmpty()) currentArtist else activeSession?.url ?: "")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlayingState)
            .setColor(android.graphics.Color.parseColor(colorHex))
            .setColorized(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        val prevIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent("com.yue.browser.MEDIA_PREV"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent("com.yue.browser.MEDIA_PLAY_PAUSE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getBroadcast(
            context,
            3,
            Intent("com.yue.browser.MEDIA_NEXT"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                "Previous",
                prevIntent
            ).build()
        )

        val playPauseIcon = if (isPlayingState) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        builder.addAction(
            Notification.Action.Builder(
                playPauseIcon,
                if (isPlayingState) "Pause" else "Play",
                playPauseIntent
            ).build()
        )

        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                "Next",
                nextIntent
            ).build()
        )

        if (currentArtworkBitmap != null) {
            builder.setLargeIcon(currentArtworkBitmap)
        }

        getNotificationManager(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun cancelNotification(context: Context) {
        getNotificationManager(context).cancel(NOTIFICATION_ID)
    }
}
