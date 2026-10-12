package com.balakamal.hrmstracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TimesheetReminderWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "HRMS_PREFS"
        const val KEY_REMINDER_ENABLED = "TimesheetReminderEnabled"
        const val KEY_REMINDER_HOUR = "TimesheetReminderHour"
        const val KEY_REMINDER_MINUTE = "TimesheetReminderMinute"
        const val KEY_WEEKDAYS_ONLY = "TimesheetWeekdaysOnly"
        private const val KEY_LAST_REMINDER_DATE = "LastTimesheetReminderDate"
        private const val WORK_NAME = "HRMS_TIMESHEET_REMINDER"
        const val CHANNEL_ID = "hrms_timesheet_reminder"
        const val NOTIFICATION_ID = 1002

        /**
         * Schedules or reschedules the one-shot worker at the configured time (default 9:00 PM IST)
         */
        fun schedule(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, true)
            if (!isEnabled) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }

            val targetHour = prefs.getInt(KEY_REMINDER_HOUR, 21) // 9 PM default
            val targetMinute = prefs.getInt(KEY_REMINDER_MINUTE, 0)

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If configured time has already passed today, schedule for tomorrow
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delayMs = target.timeInMillis - now.timeInMillis

            val workRequest = OneTimeWorkRequestBuilder<TimesheetReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        /**
         * Sends an immediate test reminder for previewing notifications
         */
        fun sendTestReminder(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val workTime = prefs.getString("WidgetWorkTime", "8h 15m") ?: "8h 15m"
            sendTimesheetNotification(context, workTime, isTest = true)
        }

        fun sendTimesheetNotification(context: Context, workTime: String, isTest: Boolean = false) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Timesheet Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Daily reminders to submit your work timesheet"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Direct 1-tap deep link into Timesheet submission page
            val timesheetIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("shortcut_url", "https://apps.pal.tech/hrms/me/timesheet")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                2001,
                timesheetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (isTest) "Test: Submit Timesheet ⏱️" else "Submit Today's Timesheet ⏱️"
            val shortText = "You logged $workTime today. Tap to submit your daily timesheet."
            val bigText = "You logged $workTime of desk time today. Please submit your daily timesheet to keep your attendance records up to date."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(shortText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_timesheet, "Submit Timesheet", pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun doWork(): Result {
        try {
            val isEnabled = sharedPrefs.getBoolean(KEY_REMINDER_ENABLED, true)
            if (!isEnabled) {
                return Result.success()
            }

            val weekdaysOnly = sharedPrefs.getBoolean(KEY_WEEKDAYS_ONLY, true)
            val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val lastSent = sharedPrefs.getString(KEY_LAST_REMINDER_DATE, null)

            // Only fire if not on weekend (if weekdays only is enabled) and not already fired today
            if ((!weekdaysOnly || !isWeekend) && lastSent != todayStr) {
                val workTime = sharedPrefs.getString("WidgetWorkTime", "today's work") ?: "today's work"
                sendTimesheetNotification(applicationContext, workTime, isTest = false)
                sharedPrefs.edit().putString(KEY_LAST_REMINDER_DATE, todayStr).apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("TimesheetWorker", "Error in TimesheetReminderWorker: ${e.message}", e)
        } finally {
            // Re-schedule for the next cycle
            schedule(applicationContext)
        }

        return Result.success()
    }
}
