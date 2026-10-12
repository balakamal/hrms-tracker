package com.balakamal.hrmstracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ShiftAlertManager {

    private const val PREFS_NAME = "HRMS_PREFS"
    const val KEY_NUDGE_ENABLED = "ShiftNudgeEnabled"
    const val KEY_NUDGE_DISMISSED_DATE = "ShiftNudgeDismissedDate"
    private const val KEY_NUDGE_COUNT = "ShiftNudgeCount"
    private const val KEY_NUDGE_LAST_WORK_TIME = "ShiftNudgeLastWorkTime"

    const val CHANNEL_ID = "hrms_shift_nudge_channel"
    const val NOTIFICATION_ID = 1003
    const val ACTION_ALARM_TICK = "com.balakamal.hrmstracker.ACTION_SHIFT_NUDGE_TICK"
    const val ACTION_DISMISS_NUDGE = "com.balakamal.hrmstracker.ACTION_SHIFT_NUDGE_DISMISS"
    const val ACTION_NUDGE_STATE_CHANGED = "com.balakamal.hrmstracker.ACTION_SHIFT_NUDGE_STATE_CHANGED"

    private const val ALARM_REQ_CODE = 3001
    private const val DISMISS_REQ_CODE = 3002
    private const val OPEN_APP_REQ_CODE = 3003
    private const val TIMESHEET_REQ_CODE = 3004

    private const val INTERVAL_MS = 120_000L // 2 Minutes

    fun isNudgeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NUDGE_ENABLED, true)
    }

    fun setNudgeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NUDGE_ENABLED, enabled).apply()
        if (!enabled) {
            stopAlerts(context)
        }
    }

    fun isDismissedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissedDate = prefs.getString(KEY_NUDGE_DISMISSED_DATE, null)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return dismissedDate == todayStr
    }

    fun isNudgeActive(context: Context): Boolean {
        if (!isNudgeEnabled(context)) return false
        if (isDismissedToday(context)) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isWfh = prefs.getBoolean("WfhMode", false)
        if (isWfh) return false
        if (AttendanceAppWidgetProvider.isWeekend()) return false

        val progressPercent = prefs.getInt("WidgetProgressPercent", 0)
        val statusText = prefs.getString("WidgetStatusText", "") ?: ""
        val isCompleted = progressPercent >= 100 || statusText.contains("Complete", ignoreCase = true)
        return isCompleted
    }

    /**
     * Checks if shift target is reached and begins the 2-minute repeating alert loop if not already dismissed.
     */
    fun checkAndTriggerNudge(context: Context, workTime: String = "") {
        if (!isNudgeActive(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (workTime.isNotBlank()) {
            prefs.edit().putString(KEY_NUDGE_LAST_WORK_TIME, workTime).apply()
        }

        // Fire initial notification and schedule 2-minute repeating alarm
        sendNudgeNotification(context)
        scheduleNextAlarm(context)
    }

    fun scheduleNextAlarm(context: Context) {
        if (!isNudgeActive(context)) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ShiftAlertReceiver::class.java).apply {
            action = ACTION_ALARM_TICK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + INTERVAL_MS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun sendNudgeNotification(context: Context) {
        if (!isNudgeActive(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var count = prefs.getInt(KEY_NUDGE_COUNT, 0) + 1
        prefs.edit().putInt(KEY_NUDGE_COUNT, count).apply()

        val workTime = prefs.getString(KEY_NUDGE_LAST_WORK_TIME, null)
            ?: prefs.getString("WidgetWorkTime", "8h 30m") ?: "8h 30m"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Leave Office Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "2-minute repeating alerts reminding you to pack up and leave the office"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 150, 350)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action 1: Dismiss / "I'm Leaving • Stop Reminding"
        val dismissIntent = Intent(context, ShiftAlertReceiver::class.java).apply {
            action = ACTION_DISMISS_NUDGE
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            DISMISS_REQ_CODE,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Timesheet quick launcher
        val timesheetIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("shortcut_url", "https://apps.pal.tech/hrms/me/timesheet")
        }
        val timesheetPendingIntent = PendingIntent.getActivity(
            context,
            TIMESHEET_REQ_CODE,
            timesheetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Tap Content Intent
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_shift_nudge", true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            OPEN_APP_REQ_CODE,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Engaging Dynamic Copy Sequence
        val overtimeMinutes = (count - 1) * 2
        val title = when (count) {
            1 -> "Shift Completed! Time to Leave 🚀"
            2 -> "Overtime Alert ⏰ Pack up and go!"
            3 -> "Still at Your Desk? 🏃‍♂️ Time to Head Out"
            else -> "Time to Leave Office! 🏡 (+${overtimeMinutes}m)"
        }

        val message = when (count) {
            1 -> "You've met your daily target ($workTime). Wrap up tasks and head home!"
            2 -> "You completed your full shift hours. Don't stay late—pack your bags!"
            3 -> "Reminder: Your workday is done ($workTime). Enjoy your evening!"
            else -> "You've been in overtime for $overtimeMinutes min. Tap 'I'm Leaving' to silence."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_exit)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(false) // Buzz and chime every 2 minutes
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_check_circle, "I'm Leaving • Stop Reminding", dismissPendingIntent)
            .addAction(R.drawable.ic_timesheet, "Timesheet", timesheetPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Stops the 2-minute repeating alerts for today.
     * Cancels AlarmManager, clears notification, and sets today as dismissed.
     */
    fun stopAlerts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        prefs.edit()
            .putString(KEY_NUDGE_DISMISSED_DATE, todayStr)
            .putInt(KEY_NUDGE_COUNT, 0)
            .apply()

        // 1. Cancel AlarmManager
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, ShiftAlertReceiver::class.java).apply {
                action = ACTION_ALARM_TICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQ_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager?.cancel(pendingIntent)
        } catch (e: Exception) {
            android.util.Log.e("ShiftAlert", "Error cancelling alarm: ${e.message}")
        }

        // 2. Cancel Notification
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            android.util.Log.e("ShiftAlert", "Error cancelling notification: ${e.message}")
        }

        // 3. Broadcast state change so MainActivity can dismiss the in-app overlay simultaneously
        try {
            val updateIntent = Intent(ACTION_NUDGE_STATE_CHANGED)
            context.sendBroadcast(updateIntent)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun getOvertimeMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_NUDGE_COUNT, 0)
        return if (count > 1) (count - 1) * 2 else 0
    }
}

