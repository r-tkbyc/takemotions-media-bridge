package com.takemotions.mediabridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that runs [BridgeHttpServer] on 127.0.0.1:[PORT] and shows the
 * ongoing "running" notification, so the glasses companion can reach /media even while
 * the screen is off or the app is backgrounded.
 *
 * Started/stopped by the toggle in [MainActivity].
 */
class BridgeService : Service() {

    companion object {
        const val ACTION_START = "com.takemotions.mediabridge.START"
        const val ACTION_STOP = "com.takemotions.mediabridge.STOP"
        const val PORT = 8766

        private const val CHANNEL_ID = "mediabridge"
        private const val NOTIF_ID = 1
        private const val TAG = "BridgeService"

        /** Polled by MainActivity to reflect the running state. */
        @Volatile
        var isRunning = false
            private set

        /** Last start-failure reason, surfaced in the UI. Null when OK. */
        @Volatile
        var lastError: String? = null
            private set
    }

    private var server: BridgeHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        lastError = null
        isRunning = true
        createChannel()
        startForegroundCompat()
        if (!startServer()) {
            stopEverything()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startServer(): Boolean {
        if (server != null) return true
        return try {
            server = BridgeHttpServer(applicationContext, PORT).apply { start() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "HTTP server failed to start on port $PORT", e)
            lastError = "Cannot bind port $PORT (${e.message ?: e.javaClass.simpleName})"
            false
        }
    }

    private fun stopEverything() {
        server?.stop()
        server = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    private fun createChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID, "Media Bridge", NotificationManager.IMPORTANCE_LOW
        )
        ch.description = "Serving now-playing media on 127.0.0.1:$PORT"
        mgr.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Media Bridge running")
            .setContentText("127.0.0.1:$PORT · media")
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun startForegroundCompat() {
        val n = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed; retrying without a type", e)
            try {
                startForeground(NOTIF_ID, n)
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground retry failed", e2)
            }
        }
    }
}
