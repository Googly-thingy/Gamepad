package com.example.receiver

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TvReceiverService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var server: TvReceiverServer

    override fun onCreate() {
        super.onCreate()
        server = TvReceiverServer.getInstance(applicationContext, serviceScope)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TvReceiverService::WakeLock").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildNotification("Listening on ws://${server.getLocalIpAddress()}:8765")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    }
                    startForeground(NOTIFICATION_ID, notification, type)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                server.startServer()

                // Observe packet rate to update notification dynamically
                serviceScope.launch {
                    server.stats.collect { stats ->
                        if (stats.isRunning) {
                            val msg = "Active • ${stats.connectedClientsCount} player(s) • ${stats.packetsPerSecond} Hz"
                            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            mgr.notify(NOTIFICATION_ID, buildNotification(msg))
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, TvReceiverActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TvReceiverService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Gamepad Receiver Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Receiver", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stopServer()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "tv_receiver_channel"
        const val NOTIFICATION_ID = 9010
        const val ACTION_START = "com.example.receiver.ACTION_START"
        const val ACTION_STOP = "com.example.receiver.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, TvReceiverService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TvReceiverService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
