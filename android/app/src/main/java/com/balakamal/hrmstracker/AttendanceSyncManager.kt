package com.balakamal.hrmstracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * AttendanceSyncManager coordinates real-time biometric synchronization.
 * It dynamically adapts sync intervals:
 * - 3-minute intervals during morning arrival (7:30 AM - 12:00 PM) before first punch
 * - 5-minute intervals during active workday while clocked in or taking breaks
 * - Sleep mode outside office hours and on weekends to preserve battery
 * 
 * Uses a dual-layer strategy:
 * 1. AlarmManager (setAndAllowWhileIdle with RTC_WAKEUP) to break through Doze mode
 * 2. WorkManager OneTimeWorkRequest for managed, wake-locked execution
 */
object AttendanceSyncManager {

    private const val TAG = "AttendanceSyncManager"
    private const val PREFS_NAME = "HRMS_PREFS"

    const val ACTION_ATTENDANCE_SYNC_ALARM = "com.balakamal.hrmstracker.ACTION_ATTENDANCE_SYNC_ALARM"
    const val ACTION_MIDNIGHT_ROLLOVER = "com.balakamal.hrmstracker.ACTION_MIDNIGHT_ROLLOVER"
    private const val ALARM_REQ_CODE = 5001
    private const val MIDNIGHT_ALARM_REQ_CODE = 5002
    const val ONE_TIME_WORK_NAME = "HRMS_ATTENDANCE_EXPEDITED_SYNC"

    // Interval constants
    const val INTERVAL_ARRIVAL_MINUTES = 3L      // Fast detection for first turnstile punch
    const val INTERVAL_WORKDAY_MINUTES = 5L      // Active punch-in / punch-out / break monitoring
    const val INTERVAL_SHIFT_DONE_MINUTES = 20L  // Low-frequency check once shift is conquered
    const val START_HOUR_MORNING = 7            // 7:30 AM
    const val START_MINUTE_MORNING = 30
    const val END_HOUR_OFFICE = 21              // 9:30 PM (21:30)
    const val END_MINUTE_OFFICE = 30

    /**
     * Calculates the optimal next sync delay and schedules both AlarmManager and WorkManager.
     */
    fun scheduleNextSync(context: Context) {
        AttendanceAppWidgetProvider.clearStaleCacheIfDateChanged(context)

        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val isCacheFromToday = (prefs.getString("WidgetCacheDate", "") ?: "") == todayStr

        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val isClockedIn = isCacheFromToday && prefs.getBoolean("WidgetIsClockedIn", false)
        val firstIn = if (isCacheFromToday) (prefs.getString("WidgetFirstIn", "--:--") ?: "--:--") else "--:--"
        val hasFirstInToday = firstIn != "--:--" && firstIn.isNotBlank()
        val progressPercent = if (isCacheFromToday) prefs.getInt("WidgetProgressPercent", 0) else 0
        val statusText = if (isCacheFromToday) (prefs.getString("WidgetStatusText", "") ?: "") else ""
        val isShiftComplete = progressPercent >= 100 || statusText.contains("Complete", ignoreCase = true)

        val isBeforeMorningStart = hour < START_HOUR_MORNING || (hour == START_HOUR_MORNING && minute < START_MINUTE_MORNING)
        val isAfterOfficeEnd = hour > END_HOUR_OFFICE || (hour == END_HOUR_OFFICE && minute >= END_MINUTE_OFFICE)

        val delayMillis: Long

        when {
            // 1. Weekend and not clocked in: sleep until Monday 7:30 AM
            isWeekend && !isClockedIn -> {
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, START_HOUR_MORNING)
                    set(Calendar.MINUTE, START_MINUTE_MORNING)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || timeInMillis <= calendar.timeInMillis) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                delayMillis = Math.max(60_000L, target.timeInMillis - calendar.timeInMillis)
                Log.d(TAG, "Weekend detected. Next sync scheduled for Monday 7:30 AM (${delayMillis / 3600000}h away)")
            }

            // 2. Weekday before 7:30 AM and not clocked in: sleep until 7:30 AM today
            !isWeekend && isBeforeMorningStart && !isClockedIn -> {
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, START_HOUR_MORNING)
                    set(Calendar.MINUTE, START_MINUTE_MORNING)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                delayMillis = Math.max(60_000L, target.timeInMillis - calendar.timeInMillis)
                Log.d(TAG, "Pre-office hours. Next sync scheduled for 7:30 AM (${delayMillis / 60000}m away)")
            }

            // 3. Weekday morning arrival window (7:30 AM - 12:00 PM) and no clock-in yet:
            // High frequency 3-minute poll to catch first turnstile punch within minutes!
            !isWeekend && !hasFirstInToday && !isClockedIn && hour < 12 -> {
                delayMillis = INTERVAL_ARRIVAL_MINUTES * 60_000L
                Log.d(TAG, "Morning arrival window: polling in $INTERVAL_ARRIVAL_MINUTES mins for first biometric punch")
            }

            // 4. Actively clocked in (or currently on break):
            // Poll every 5 minutes to track breaks, return from breaks, and pack-up punches
            isClockedIn || (hasFirstInToday && !isShiftComplete && !isAfterOfficeEnd) -> {
                delayMillis = INTERVAL_WORKDAY_MINUTES * 60_000L
                Log.d(TAG, "Active workday mode: polling in $INTERVAL_WORKDAY_MINUTES mins")
            }

            // 5. Shift complete and employee clocked out:
            // Low-frequency check or sleep until next workday 7:30 AM if late
            isShiftComplete && !isClockedIn -> {
                if (isAfterOfficeEnd) {
                    val target = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, START_HOUR_MORNING)
                        set(Calendar.MINUTE, START_MINUTE_MORNING)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    delayMillis = Math.max(60_000L, target.timeInMillis - calendar.timeInMillis)
                    Log.d(TAG, "Shift complete & evening: sleep until tomorrow 7:30 AM")
                } else {
                    delayMillis = INTERVAL_SHIFT_DONE_MINUTES * 60_000L
                    Log.d(TAG, "Shift complete: relaxed poll in $INTERVAL_SHIFT_DONE_MINUTES mins")
                }
            }

            // 6. After 9:30 PM on a weekday and not clocked in: sleep until tomorrow 7:30 AM
            isAfterOfficeEnd && !isClockedIn -> {
                val target = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, START_HOUR_MORNING)
                    set(Calendar.MINUTE, START_MINUTE_MORNING)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                delayMillis = Math.max(60_000L, target.timeInMillis - calendar.timeInMillis)
                Log.d(TAG, "After-hours: next sync scheduled for tomorrow 7:30 AM")
            }

            // Default fallback: 5 minutes
            else -> {
                delayMillis = INTERVAL_WORKDAY_MINUTES * 60_000L
                Log.d(TAG, "Default sync window: polling in $INTERVAL_WORKDAY_MINUTES mins")
            }
        }

        // Schedule AlarmManager watchdog (wakes device from Doze)
        scheduleAlarm(context, delayMillis)

        // Schedule dedicated midnight rollover alarm (00:01 AM) to clear yesterday's widget cache
        scheduleMidnightRollover(context)

        // Schedule WorkManager OneTimeWorkRequest
        scheduleWorkManager(context, delayMillis)
    }

    private fun scheduleAlarm(context: Context, delayMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = System.currentTimeMillis() + delayMillis

        val intent = Intent(context, AttendanceSyncReceiver::class.java).apply {
            action = ACTION_ATTENDANCE_SYNC_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d(TAG, "AlarmManager set for trigger in ${delayMillis / 1000}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule AlarmManager: ${e.message}", e)
        }
    }

    private fun scheduleWorkManager(context: Context, delayMillis: Long) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<AttendanceWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "WorkManager enqueued for delay ${delayMillis / 1000}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue WorkManager request: ${e.message}", e)
        }
    }

    /**
     * Schedules an exact 00:01 AM alarm for the next calendar day so homescreen & lockscreen widgets
     * immediately clear yesterday's data at midnight.
     */
    fun scheduleMidnightRollover(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(context, AttendanceSyncReceiver::class.java).apply {
            action = ACTION_MIDNIGHT_ROLLOVER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnight.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextMidnight.timeInMillis, pendingIntent)
            }
            Log.d(TAG, "Midnight rollover alarm scheduled for ${nextMidnight.time}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule midnight rollover alarm: ${e.message}", e)
        }
    }

    fun cancelSync(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, AttendanceSyncReceiver::class.java).apply {
                action = ACTION_ATTENDANCE_SYNC_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQ_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null && alarmManager != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
            val midnightIntent = Intent(context, AttendanceSyncReceiver::class.java).apply {
                action = ACTION_MIDNIGHT_ROLLOVER
            }
            val midnightPending = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQ_CODE,
                midnightIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (midnightPending != null && alarmManager != null) {
                alarmManager.cancel(midnightPending)
                midnightPending.cancel()
            }
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(ONE_TIME_WORK_NAME)
            Log.d(TAG, "Cancelled active attendance sync schedules")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling sync: ${e.message}")
        }
    }
}
