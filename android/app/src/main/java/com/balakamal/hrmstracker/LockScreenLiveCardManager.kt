package com.balakamal.hrmstracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

object LockScreenLiveCardManager {

    private const val PREFS_NAME = "HRMS_PREFS"
    const val KEY_LOCKSCREEN_LIVE_ENABLED = "LockScreenLiveCardEnabled"
    const val CHANNEL_ID = "hrms_lockscreen_live_channel"
    const val NOTIFICATION_ID = 1004

    private const val REQ_REFRESH = 4001
    private const val REQ_TIMESHEET = 4002
    private const val REQ_OPEN_APP = 4003

    fun isLiveCardEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOCKSCREEN_LIVE_ENABLED, true)
    }

    fun setLiveCardEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOCKSCREEN_LIVE_ENABLED, enabled).apply()
        if (enabled) {
            updateLiveCard(context)
        } else {
            clearLiveCard(context)
        }
    }

    fun updateLiveCard(context: Context) {
        if (!isLiveCardEnabled(context)) {
            clearLiveCard(context)
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isWfh = prefs.getBoolean("WfhMode", false)
        val isWeekend = AttendanceAppWidgetProvider.isWeekend()

        val firstIn = prefs.getString("WidgetFirstIn", "--:--") ?: "--:--"
        val workTime = prefs.getString("WidgetWorkTime", "0h 00m") ?: "0h 00m"
        val exitTime = prefs.getString("WidgetExitTime", "--:--") ?: "--:--"
        val actualExitTime = prefs.getString("WidgetActualExitTime", "--:--") ?: "--:--"
        val statusTextRaw = prefs.getString("WidgetStatusText", "Clocked In") ?: "Clocked In"
        val progressPercent = prefs.getInt("WidgetProgressPercent", 0)
        val remaining = prefs.getString("WidgetProgressRemaining", "-- left") ?: "-- left"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create low-priority silent channel with zero vibrations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lock Screen Live Activity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live attendance status displayed on your Lock Screen and Always On Display"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val views = RemoteViews(context.packageName, R.layout.layout_lockscreen_live_card)

        // Status Determination & Theme Tinting
        val displayStatus: String
        val statusColor: Int
        when {
            isWfh -> {
                displayStatus = "REMOTE WORKSPACE • WFH"
                statusColor = Color.parseColor("#FFCE20") // Paltech Gold
            }
            isWeekend && (firstIn == "--:--" || firstIn.isBlank()) -> {
                displayStatus = "WEEK OFF • REST & RECHARGE"
                statusColor = Color.parseColor("#0F47C0") // Paltech Royal Blue
            }
            progressPercent >= 100 || statusTextRaw.contains("Complete", ignoreCase = true) -> {
                displayStatus = "SHIFT COMPLETE • GOAL MET 🚀"
                statusColor = Color.parseColor("#39B14A") // Paltech Green
            }
            statusTextRaw.contains("Clocked", ignoreCase = true) || workTime != "0h 00m" -> {
                displayStatus = "CLOCKED IN • IN-OFFICE"
                statusColor = Color.parseColor("#0576F8") // Paltech Sky Blue
            }
            else -> {
                displayStatus = statusTextRaw.uppercase()
                statusColor = Color.parseColor("#0576F8")
            }
        }

        views.setTextViewText(R.id.txt_ls_status, displayStatus)
        views.setTextColor(R.id.txt_ls_status, statusColor)
        views.setTextViewText(R.id.txt_ls_progress_pct, "$progressPercent%")
        views.setTextViewText(R.id.txt_ls_work_time, if (workTime.isBlank()) "--" else workTime)
        views.setTextViewText(R.id.txt_ls_exit_time, if (exitTime.isBlank()) "--:--" else exitTime)
        val remainingDisplay = if (actualExitTime != "--:--" && actualExitTime != exitTime && !isWeekend && !isWfh) {
            "$remaining (Act: $actualExitTime)"
        } else {
            remaining
        }
        views.setTextViewText(R.id.txt_ls_remaining, remainingDisplay)
        views.setProgressBar(R.id.pb_ls_progress, 100, progressPercent, false)

        // 1-Tap Refresh Action
        val refreshIntent = Intent(context, AttendanceAppWidgetProvider::class.java).apply {
            action = AttendanceAppWidgetProvider.ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_REFRESH,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_ls_refresh, refreshPendingIntent)

        // 1-Tap Timesheet Action
        val timesheetIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("shortcut_url", "https://apps.pal.tech/hrms/me/timesheet")
        }
        val timesheetPendingIntent = PendingIntent.getActivity(
            context,
            REQ_TIMESHEET,
            timesheetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_ls_timesheet, timesheetPendingIntent)

        // Card Content Tap opens main app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            REQ_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sparkle)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun clearLiveCard(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
