package dev.clankyard.feature.workspacepicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

class CopyInForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_DONE, false) == true) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val copied = intent?.getLongExtra(EXTRA_COPIED, 0L) ?: 0L
        val total = intent?.getLongExtra(EXTRA_TOTAL, 0L) ?: 0L
        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            notification(copied, total),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        return START_STICKY
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workshop copy",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun notification(copied: Long, total: Long): Notification {
        val max = if (total > 0) 100 else 0
        val progress = if (total > 0) ((copied * 100L) / total).toInt() else 0
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Copying into workshop")
            .setContentText(if (total > 0) "$copied / $total bytes" else "Copying…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, total <= 0)
            .build()
    }

    companion object {
        const val EXTRA_COPIED = "copied"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_DONE = "done"
        private const val CHANNEL_ID = "workshop_copy"
        private const val NOTIFICATION_ID = 1001
    }
}
