package com.yue.browser.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.yue.browser.MainActivity
import com.yue.browser.domain.model.DownloadItem
import com.yue.browser.domain.model.DownloadStatus

object DownloadNotifier {

    const val CHANNEL_ID = "yue_browser_downloads"
    private const val CHANNEL_NAME = "Unduhan"
    private const val CHANNEL_DESC = "Notifikasi unduhan file"

    private var notificationManager: NotificationManager? = null

    fun initialize(context: Context) {
        if (notificationManager != null) return
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                enableLights(false)
                enableVibration(false)
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun getNotificationId(downloadId: String): Int {
        // Generate stable notification ID per download
        return (downloadId.hashCode() and 0xfffffff)
    }

    fun buildProgressNotification(
        context: Context,
        item: DownloadItem
    ): android.app.Notification {
        initialize(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            getNotificationId(item.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activeChunks = item.chunks.count { it.status == com.yue.browser.domain.model.ChunkStatus.DOWNLOADING }
        val speedText = ""
        val sizeText = formatSize(item.downloadedSize) + " / " + formatSize(item.totalSize)
        val statusText = when (item.status) {
            DownloadStatus.DOWNLOADING -> if (activeChunks > 0) "Mengunduh... ($activeChunks koneksi) $sizeText" else "Mengunduh... $sizeText"
            DownloadStatus.PAUSED -> "Dijeda - $sizeText"
            else -> "Menyiapkan..."
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.fileName)
            .setContentText(statusText)
            .setContentIntent(pendingIntent)
            .setOngoing(item.status == DownloadStatus.DOWNLOADING)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(
                if (item.totalSize > 0) 100 else 0,
                item.progress,
                item.totalSize <= 0
            )
            .build()
    }

    fun updateProgress(context: Context, item: DownloadItem) {
        initialize(context)
        val notification = buildProgressNotification(context, item)
        notificationManager?.notify(getNotificationId(item.id), notification)
    }

    fun showCompleted(context: Context, item: DownloadItem) {
        initialize(context)
        notificationManager?.cancel(getNotificationId(item.id))

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            getNotificationId(item.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(item.fileName)
            .setContentText("Unduhan selesai - ${formatSize(item.totalSize)}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager?.notify(getNotificationId(item.id), notification)
    }

    fun showFailed(context: Context, item: DownloadItem, errorMsg: String = "Gagal mengunduh") {
        initialize(context)
        notificationManager?.cancel(getNotificationId(item.id))

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            getNotificationId(item.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(item.fileName)
            .setContentText(errorMsg)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager?.notify(getNotificationId(item.id), notification)
    }

    fun showPaused(context: Context, item: DownloadItem) {
        initialize(context)
        val notification = buildProgressNotification(context, item.copy(status = DownloadStatus.PAUSED))
        notificationManager?.notify(getNotificationId(item.id), notification)
    }

    fun cancel(context: Context, downloadId: String) {
        initialize(context)
        notificationManager?.cancel(getNotificationId(downloadId))
    }

    fun cancelAll(context: Context) {
        initialize(context)
        notificationManager?.cancelAll()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
