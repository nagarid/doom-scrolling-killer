package com.example.doomscrollingkiller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class UsageMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 5_000L

    private val pollRunnable = object : Runnable {
        override fun run() {
            val currentPackage = resolveCurrentPackage()
            if (WatchedApps.isWatched(currentPackage)) {
                Log.d(TAG, "Detected watched app: $currentPackage")
                updateNotification("Detected: $currentPackage")
            } else {
                updateNotification("Monitoring in background")
            }
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring in background"))
        handler.post(pollRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resolveCurrentPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 15_000L
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        var lastMoveToForeground: UsageEvents.Event? = null
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastMoveToForeground = UsageEvents.Event(event)
            }
        }
        return lastMoveToForeground?.packageName
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Usage monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Doom Scrolling Killer")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    companion object {
        private const val TAG = "UsageMonitorService"
        private const val CHANNEL_ID = "usage_monitor"
        private const val NOTIFICATION_ID = 1001
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
