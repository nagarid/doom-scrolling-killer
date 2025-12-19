package com.example.doomscrollingkiller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class UsageMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 5_000L
    private val countdownDurationMs = 3 * 60 * 1000L

    private var windowManager: WindowManager? = null
    private var countdownView: TextView? = null
    private var blockingView: FrameLayout? = null
    private var countdownTimer: CountDownTimer? = null
    private var isWatchActive = false
    private var hasReachedLimit = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            val currentPackage = resolveCurrentPackage()
            if (WatchedApps.isWatched(currentPackage)) {
                Log.d(TAG, "Detected watched app: $currentPackage")
                updateNotification("Detected: $currentPackage")
                if (!isWatchActive) {
                    isWatchActive = true
                    hasReachedLimit = false
                    startCountdown()
                }
            } else {
                updateNotification("Monitoring in background")
                if (isWatchActive) {
                    stopCountdownAndOverlays()
                }
            }
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring in background"))
        handler.post(pollRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        stopCountdownAndOverlays()
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
                lastMoveToForeground = UsageEvents.Event()
            }
        }
        return lastMoveToForeground?.packageName
    }

    private fun startCountdown() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted; skipping countdown overlay.")
            return
        }
        if (countdownTimer != null) {
            return
        }
        showCountdownOverlay()
        countdownTimer = object : CountDownTimer(countdownDurationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                updateCountdownText(millisUntilFinished)
            }

            override fun onFinish() {
                hasReachedLimit = true
                removeCountdownOverlay()
                showBlockingOverlay()
            }
        }.start()
    }

    private fun stopCountdownAndOverlays() {
        isWatchActive = false
        hasReachedLimit = false
        countdownTimer?.cancel()
        countdownTimer = null
        removeCountdownOverlay()
        removeBlockingOverlay()
    }

    private fun showCountdownOverlay() {
        if (countdownView != null) {
            return
        }
        val textView = TextView(this).apply {
            setTextColor(getColor(android.R.color.holo_red_dark))
            setBackgroundColor(0xB0000000.toInt())
            textSize = 18f
            setPadding(24, 16, 24, 16)
            text = formatTime(countdownDurationMs)
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 24
        }
        windowManager?.addView(textView, layoutParams)
        countdownView = textView
    }

    private fun updateCountdownText(millisUntilFinished: Long) {
        countdownView?.text = formatTime(millisUntilFinished)
    }

    private fun removeCountdownOverlay() {
        countdownView?.let { view ->
            windowManager?.removeView(view)
        }
        countdownView = null
    }

    private fun showBlockingOverlay() {
        if (blockingView != null || !isWatchActive || !hasReachedLimit) {
            return
        }
        val container = FrameLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt())
        }
        val message = TextView(this).apply {
            setTextColor(getColor(android.R.color.white))
            textSize = 28f
            text = getString(R.string.stop_scrolling_message)
            gravity = Gravity.CENTER
        }
        container.addView(
            message,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(container, layoutParams)
        blockingView = container
    }

    private fun removeBlockingOverlay() {
        blockingView?.let { view ->
            windowManager?.removeView(view)
        }
        blockingView = null
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun formatTime(millisUntilFinished: Long): String {
        val totalSeconds = millisUntilFinished.coerceAtLeast(0L) / 1000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
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
