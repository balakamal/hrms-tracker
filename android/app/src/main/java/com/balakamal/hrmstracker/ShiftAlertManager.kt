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
    const val KEY_NUDGE_INTERVAL_MINUTES = "ShiftNudgeIntervalMinutes"
    const val DEFAULT_INTERVAL_MINUTES = 5
    const val KEY_NUDGE_DISMISSED_DATE = "ShiftNudgeDismissedDate"
    private const val KEY_NUDGE_COUNT = "ShiftNudgeCount"
    private const val KEY_NUDGE_LAST_WORK_TIME = "ShiftNudgeLastWorkTime"

    const val CHANNEL_ID = "hrms_shift_nudge_channel"
    const val NOTIFICATION_ID = 1003
    const val ACTION_ALARM_TICK = "com.balakamal.hrmstracker.ACTION_SHIFT_NUDGE_TICK"
    const val ACTION_SHIFT_TARGET_REACHED = "com.balakamal.hrmstracker.ACTION_SHIFT_TARGET_REACHED"
    const val ACTION_DISMISS_NUDGE = "com.balakamal.hrmstracker.ACTION_SHIFT_NUDGE_DISMISS"
    const val ACTION_NUDGE_STATE_CHANGED = "com.balakamal.hrmstracker.ACTION_SHIFT_NUDGE_STATE_CHANGED"

    private const val ALARM_REQ_CODE = 3001
    private const val DISMISS_REQ_CODE = 3002
    private const val OPEN_APP_REQ_CODE = 3003
    private const val TIMESHEET_REQ_CODE = 3004
    private const val TARGET_ALARM_REQ_CODE = 3005

    fun getIntervalMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_NUDGE_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
    }

    fun setIntervalMinutes(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_NUDGE_INTERVAL_MINUTES, minutes).apply()
        if (isNudgeActive(context)) {
            scheduleNextAlarm(context)
        }
    }

    fun getIntervalMs(context: Context): Long {
        return getIntervalMinutes(context) * 60_000L
    }

    fun isNudgeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NUDGE_ENABLED, true)
    }

    fun setNudgeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NUDGE_ENABLED, enabled).apply()
        if (!enabled) {
            stopAlerts(context)
        } else {
            prefs.edit().remove(KEY_NUDGE_DISMISSED_DATE).apply()
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

        // Presence check: The employee must be actively clocked in to be nudged to leave the office.
        // If they clocked out at the biometric device or portal, they have left—suppress all alerts!
        val isClockedIn = prefs.getBoolean("WidgetIsClockedIn", false)
        if (!isClockedIn) {
            return false
        }

        val progressPercent = prefs.getInt("WidgetProgressPercent", 0)
        val statusText = prefs.getString("WidgetStatusText", "") ?: ""

        val lastInTimestamp = prefs.getLong("WidgetLastInTimestamp", 0L)
        val baseWorkMinutes = prefs.getFloat("WidgetBaseWorkMinutes", -1f)
        val targetMinutes = prefs.getFloat("WidgetTargetMinutes", 510f)
        val isExtrapolatedComplete = if (lastInTimestamp > 0 && baseWorkMinutes >= 0) {
            val liveWork = baseWorkMinutes + (System.currentTimeMillis() - lastInTimestamp) / 60000.0
            liveWork >= targetMinutes
        } else false

        return progressPercent >= 100 || statusText.contains("Complete", ignoreCase = true) || isExtrapolatedComplete
    }

    /**
     * Schedules an alarm for the exact predicted shift completion timestamp.
     * When the employee reaches 8.5 hours while at their desk, this fires on the second.
     */
    fun scheduleShiftTargetAlarm(context: Context, targetExitTimestamp: Long) {
        if (!isNudgeEnabled(context) || isDismissedToday(context)) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("WfhMode", false) || AttendanceAppWidgetProvider.isWeekend()) return

        // If shift target time already arrived or passed, trigger nudge immediately
        if (targetExitTimestamp <= System.currentTimeMillis()) {
            if (isNudgeActive(context)) {
                checkAndTriggerNudge(context)
            }
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ShiftAlertReceiver::class.java).apply {
            action = ACTION_SHIFT_TARGET_REACHED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TARGET_ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetExitTimestamp, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, targetExitTimestamp, pendingIntent)
            }
            android.util.Log.i("ShiftAlert", "Scheduled exact shift target alarm at: $targetExitTimestamp")
        } catch (e: Exception) {
            android.util.Log.e("ShiftAlert", "Error scheduling shift target alarm: ${e.message}")
        }
    }

    /**
     * Checks if shift target is reached and begins the repeating alert loop if not already dismissed.
     */
    fun checkAndTriggerNudge(context: Context, workTime: String = "") {
        if (!isNudgeActive(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (workTime.isNotBlank()) {
            prefs.edit().putString(KEY_NUDGE_LAST_WORK_TIME, workTime).apply()
        }

        // Fire initial notification and schedule next repeating alarm
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

        val triggerAtMillis = System.currentTimeMillis() + getIntervalMs(context)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("ShiftAlert", "Error scheduling next alarm tick: ${e.message}")
        }
    }

    fun sendNudgeNotification(context: Context) {
        if (!isNudgeActive(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var count = prefs.getInt(KEY_NUDGE_COUNT, 0) + 1
        prefs.edit().putInt(KEY_NUDGE_COUNT, count).apply()

        val workTime = prefs.getString(KEY_NUDGE_LAST_WORK_TIME, null)
            ?: prefs.getString("WidgetWorkTime", "8h 30m") ?: "8h 30m"

        val interval = getIntervalMinutes(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Leave Office Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "$interval-minute repeating alerts reminding you to pack up and leave the office"
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
        val overtimeMinutes = (count - 1) * interval
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
            .setOnlyAlertOnce(false)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_check_circle, "I'm Leaving • Stop Reminding", dismissPendingIntent)
            .addAction(R.drawable.ic_timesheet, "Timesheet", timesheetPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Stops repeating alerts for today.
     * Cancels both repeating ticks and target alarm, clears notification, and sets today as dismissed.
     */
    fun stopAlerts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        prefs.edit()
            .putString(KEY_NUDGE_DISMISSED_DATE, todayStr)
            .putInt(KEY_NUDGE_COUNT, 0)
            .apply()

        // 1. Cancel Repeating Alarm Tick & Target Alarm
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            
            // Cancel repeating tick
            val tickIntent = Intent(context, ShiftAlertReceiver::class.java).apply {
                action = ACTION_ALARM_TICK
            }
            val tickPending = PendingIntent.getBroadcast(
                context,
                ALARM_REQ_CODE,
                tickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager?.cancel(tickPending)

            // Cancel shift target alarm
            val targetIntent = Intent(context, ShiftAlertReceiver::class.java).apply {
                action = ACTION_SHIFT_TARGET_REACHED
            }
            val targetPending = PendingIntent.getBroadcast(
                context,
                TARGET_ALARM_REQ_CODE,
                targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager?.cancel(targetPending)
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

    /**
     * Sends an instant preview alert to test notification visual and vibrations with the configured interval.
     */
    fun sendTestAlert(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val workTime = prefs.getString(KEY_NUDGE_LAST_WORK_TIME, null)
            ?: prefs.getString("WidgetWorkTime", "8h 30m") ?: "8h 30m"
        val interval = getIntervalMinutes(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Leave Office Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "$interval-minute repeating alerts reminding you to pack up and leave the office"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 150, 350)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val dismissIntent = Intent(context, ShiftAlertReceiver::class.java).apply {
            action = ACTION_DISMISS_NUDGE
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            DISMISS_REQ_CODE,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val title = "Shift Completed! Time to Leave 🚀"
        val message = "You've met your daily target ($workTime). Wrap up tasks and head home! Alerts repeat every $interval minutes."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_exit)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_check_circle, "I'm Leaving • Stop Reminding", dismissPendingIntent)
            .addAction(R.drawable.ic_timesheet, "Timesheet", timesheetPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun getOvertimeMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_NUDGE_COUNT, 0)
        val interval = getIntervalMinutes(context)
        return if (count > 1) (count - 1) * interval else 0
    }
}

