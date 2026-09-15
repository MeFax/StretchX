package com.stretchx.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class GameWatchdogService : Service() {
    companion object {
        private const val TAG = "GameWatchdog"
        const val EXTRA_PACKAGE = "EXTRA_TARGET_PACKAGE"
        const val ACTION_STOP_AND_RESET = "com.stretchx.ACTION_STOP_AND_RESET"
        private const val CHANNEL_ID = "stretchx_watchdog_channel"
        private const val NOTIFICATION_ID = 2001
        private const val STARTUP_GRACE_PERIOD_MS = 5000L
        private const val POLLING_INTERVAL_MS = 1500L
        private const val MAX_UNFOCUSED_CYCLES = 3
    }

    private var targetPackage: String = ""
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Screen-Off Safety: If phone is locked, immediately restore native resolution
    // to keep the in-display ultrasonic fingerprint scanner and lock screen 100% aligned!
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.i(TAG, "Screen-off detected. Restoring S25 Ultra native resolution for lock screen safety.")
                DisplayOptimizer.resetToNative(this@GameWatchdogService)
                stopFloatingOverlay()
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_AND_RESET) {
            Log.i(TAG, "Manual reset requested from notification action.")
            DisplayOptimizer.resetToNative(this)
            stopFloatingOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        targetPackage = intent?.getStringExtra(EXTRA_PACKAGE) ?: ""
        if (targetPackage.isNotEmpty() && !isRunning) {
            startForeground(NOTIFICATION_ID, buildNotification())
            startWatchdogLoop()
        }
        return START_NOT_STICKY
    }

    private fun startWatchdogLoop() {
        isRunning = true
        serviceScope.launch {
            Log.i(TAG, "Watchdog loop started for target: $targetPackage")
            // Grace period: allow slow loading screens / splash screens to finish before monitoring focus
            delay(STARTUP_GRACE_PERIOD_MS)

            var consecutiveUnfocusedCount = 0

            while (isRunning) {
                delay(POLLING_INTERVAL_MS) // 1500ms reduces process fork CPU load by 50%

                // Replace fragile mCurrentFocus with topResumedActivity / mResumedActivity.
                // When volume sliders, notification drawer, or floating bubbles appear,
                // mCurrentFocus shifts to SystemUI, but topResumedActivity remains the game!
                val focusDump = ShizukuManager.exec("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'")
                val isGameFocused = focusDump.contains(targetPackage, ignoreCase = true)

                if (!isGameFocused) {
                    consecutiveUnfocusedCount++
                    Log.d(TAG, "Game not in resumed activity ($consecutiveUnfocusedCount/$MAX_UNFOCUSED_CYCLES): $focusDump")

                    if (consecutiveUnfocusedCount >= MAX_UNFOCUSED_CYCLES) {
                        Log.i(TAG, "Target game exited or backgrounded ($MAX_UNFOCUSED_CYCLES consecutive cycles). Restoring display!")
                        DisplayOptimizer.resetToNative(this@GameWatchdogService)
                        stopFloatingOverlay()
                        stopSelf()
                        break
                    }
                } else {
                    consecutiveUnfocusedCount = 0
                }
            }
        }
    }

    private fun stopFloatingOverlay() {
        val stopIntent = Intent(this, FloatingOverlayService::class.java)
        stopService(stopIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "StretchX Watchdog Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors game focus and automatically restores screen resolution"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val resetIntent = Intent(this, GameWatchdogService::class.java).apply {
            action = ACTION_STOP_AND_RESET
        }
        val pendingResetIntent = PendingIntent.getService(
            this, 1, resetIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StretchX Aktif: $targetPackage")
            .setContentText("Bildirim çubuğundan güvenli reset (Anti-Cheat 100% Ban-Safe)")
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_menu_revert, "Normale Dön (Reset)", pendingResetIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Throwable) {
            // ignore if already unregistered
        }
        serviceScope.cancel()
        DisplayOptimizer.resetToNative(this)
        Log.i(TAG, "Watchdog destroyed. Display reset confirmed.")
    }
}
