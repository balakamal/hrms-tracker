package com.balakamal.hrmstracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.*

/**
 * AttendanceWorker fetches real-time biometric logs from the official HRMS backend API,
 * recalculates attendance metrics, and synchronizes all active homescreen & lockscreen widgets.
 * 
 * Never returns Result.failure() to ensure background synchronization is never permanently terminated.
 */
class AttendanceWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("HRMS_PREFS", Context.MODE_PRIVATE)

    override fun doWork(): Result {
        Log.d(TAG, "Starting AttendanceWorker background sync...")
        var result = Result.success()

        try {
            var accessToken = TokenManager.getValidAccessToken(applicationContext)
            val cookies = TokenManager.getValidCookies(applicationContext)

            if (accessToken.isNullOrEmpty() && cookies.isNullOrEmpty()) {
                Log.w(TAG, "No valid AccessToken or Cookies found. Keeping worker alive.")
                AttendanceAppWidgetProvider.saveWidgetCacheError(applicationContext, "Please Login in App")
                AttendanceAppWidgetProvider.triggerWidgetUpdate(applicationContext)
                return Result.success()
            }

            var userId = sharedPrefs.getString("UserId", null)
            if (userId.isNullOrEmpty()) {
                val identity = TokenManager.ensureUserIdentity(applicationContext)
                if (identity != null) {
                    userId = identity.first
                } else {
                    Log.w(TAG, "User identity not resolved. Keeping worker alive.")
                    AttendanceAppWidgetProvider.saveWidgetCacheError(applicationContext, "User ID not found")
                    AttendanceAppWidgetProvider.triggerWidgetUpdate(applicationContext)
                    return Result.success()
                }
            }

            // Office-hours check: avoid battery drain outside of office hours unless actively clocked in
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            val isClockedIn = sharedPrefs.getBoolean("WidgetIsClockedIn", false)

            if (!isClockedIn && (isWeekend || hour < 7 || (hour == 7 && minute < 30) || hour >= 22)) {
                // Update widget locally without remote network call
                AttendanceAppWidgetProvider.triggerWidgetUpdate(applicationContext)
                return Result.success()
            }

            // 1. Get formatted local ISO date
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayStr = sdfDate.format(Date())
            val todayISO = "${todayStr}T00:00:00.000Z"

            // 2. Fetch daily attendance log from API
            val apiUrl = "https://apps.pal.tech/hrms-backend/api/Attendance/GetDailyLog?date=$todayISO&userId=$userId"

            var connection = TokenManager.openAuthenticatedConnection(applicationContext, apiUrl, accessToken)
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 401) {
                // Access token expired; silently rotate token and retry once
                val refreshResult = TokenManager.refreshTokens(applicationContext)
                when (refreshResult) {
                    is TokenManager.RefreshResult.Success -> {
                        accessToken = refreshResult.accessToken
                        connection.disconnect()
                        connection = TokenManager.openAuthenticatedConnection(applicationContext, apiUrl, accessToken)
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                    }
                    is TokenManager.RefreshResult.SessionExpired -> {
                        AttendanceAppWidgetProvider.saveWidgetCacheError(applicationContext, "Session Expired")
                        AttendanceAppWidgetProvider.triggerWidgetUpdate(applicationContext)
                        sendNotification("Session Expired", "Please open the HRMS Insights app to log in again.")
                        return Result.success() // Keep worker alive for next app login
                    }
                    is TokenManager.RefreshResult.NetworkError -> {
                        return Result.retry()
                    }
                }
            }

            if (connection.responseCode == 401) {
                // Retried token is still unauthorized
                AttendanceAppWidgetProvider.saveWidgetCacheError(applicationContext, "Session Expired")
                AttendanceAppWidgetProvider.triggerWidgetUpdate(applicationContext)
                sendNotification("Session Expired", "Please open the HRMS Insights app to log in again.")
                return Result.success()
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                // Refresh WFH status from official API
                val remoteWfh = AttendanceAppWidgetProvider.checkEmployeeWfhStatus(applicationContext, accessToken, userId)
                if (remoteWfh != null) {
                    sharedPrefs.edit().putBoolean("WfhMode", remoteWfh).apply()
                }

                // Fetch dynamic policy target hours & personalized 30-day average break
                val hasCustomTarget = sharedPrefs.getBoolean("HasCustomTargetHours", false)
                val policyHours = if (hasCustomTarget) null else AttendanceAppWidgetProvider.fetchRequiredWorkingHours(applicationContext, accessToken, userId)
                val effectiveTargetHours = if (hasCustomTarget) sharedPrefs.getFloat("TargetHours", 8.5f) else (policyHours ?: sharedPrefs.getFloat("TargetHours", 8.5f))
                val avgBreak = AttendanceAppWidgetProvider.fetchAverageBreakMinutes(applicationContext, accessToken, userId)

                // 3. Calculate and sync widget in the background
                val metrics = AttendanceAppWidgetProvider.calculateMetrics(response.toString(), effectiveTargetHours, avgBreak)
                if (metrics != null) {
                    AttendanceAppWidgetProvider.saveWidgetCache(
                        applicationContext,
                        metrics.workTime,
                        metrics.firstIn,
                        metrics.breakTime,
                        metrics.exitTime,
                        metrics.statusText,
                        metrics.progressPercent,
                        metrics.progressRemaining,
                        "Last updated: " + AttendanceAppWidgetProvider.getCurrentTime(),
                        metrics.breakCount,
                        metrics.actualExitTime,
                        baseWorkMinutes = metrics.baseWorkMinutes,
                        lastInTimestamp = metrics.lastInTimestamp,
                        isClockedIn = metrics.isClockedIn,
                        targetMinutes = metrics.targetMinutes,
                        baseBreakMinutes = metrics.baseBreakMinutes,
                        avgBreakMinutes = metrics.avgBreakMinutes,
                        lastOutTimestamp = metrics.lastOutTimestamp
                    )

                    // Record successful sync timestamp for screen-on freshness checks
                    sharedPrefs.edit()
                        .putLong(AttendanceSyncReceiver.KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
                        .apply()

                    AttendanceAppWidgetProvider.triggerWidgetUpdate(applicationContext)

                    // 4. Trigger Completed Shift Notification or Pre-Shift Pack-up Alert (respect user settings)
                    val notifyShiftComplete = sharedPrefs.getBoolean("NotifShiftComplete", true)
                    val notifyPreExit = sharedPrefs.getBoolean("NotifPreExit", true)
                    val isWfh = sharedPrefs.getBoolean("WfhMode", false)

                    val isCompleted = metrics.statusText == "Completed shift!" || metrics.statusText.contains("CONQUERED") || metrics.progressPercent >= 100

                    if (!isWfh && isCompleted) {
                        if (metrics.isClockedIn) {
                            if (notifyShiftComplete) {
                                val lastNotifiedDate = sharedPrefs.getString("LastNotificationDate", "")
                                if (lastNotifiedDate != todayStr) {
                                    sendNotification(
                                        "Shift Completed!", 
                                        "You have completed ${metrics.workTime} of biometric office time. Time to head home!"
                                    )
                                    sharedPrefs.edit().putString("LastNotificationDate", todayStr).apply()
                                }
                            }
                            // Start repeating leave office alerts if active & not dismissed
                            ShiftAlertManager.checkAndTriggerNudge(applicationContext, metrics.workTime)
                        } else {
                            // Employee already punched out at the turnstile/portal; permanently silence alerts
                            ShiftAlertManager.stopAlerts(applicationContext)
                        }
                    } else if (!isWfh) {
                        if (metrics.isClockedIn && metrics.lastInTimestamp > 0) {
                            // Schedule exact alarm for when shift goal will be reached while at desk
                            val remainingMinutes = Math.max(0.0, metrics.targetMinutes - metrics.baseWorkMinutes)
                            val targetExitTimestamp = metrics.lastInTimestamp + (remainingMinutes * 60000).toLong()
                            ShiftAlertManager.scheduleShiftTargetAlarm(applicationContext, targetExitTimestamp)
                        } else {
                            ShiftAlertManager.stopAlerts(applicationContext)
                        }

                        if (notifyPreExit && metrics.progressPercent in 92..99) {
                            val lastPreNotifiedDate = sharedPrefs.getString("LastPreNotificationDate", "")
                            if (lastPreNotifiedDate != todayStr) {
                                sendNotification(
                                    "Almost Done! (${metrics.progressRemaining})",
                                    "Shift is at ${metrics.progressPercent}%. Wrap up tasks and get ready to pack up!"
                                )
                                sharedPrefs.edit().putString("LastPreNotificationDate", todayStr).apply()
                            }
                        }
                    } else {
                        ShiftAlertManager.stopAlerts(applicationContext)
                    }
                } else {
                    ShiftAlertManager.stopAlerts(applicationContext)
                    val isWfh = sharedPrefs.getBoolean("WfhMode", false)
                    val isWeekendToday = AttendanceAppWidgetProvider.isWeekend()
                    val noLogsStatus = when {
                        isWfh -> "Working from Home"
                        isWeekendToday -> "Week Off"
                        else -> "No logs today"
                    }
                    val defaultWorkTime = if (isWeekendToday) "Week Off" else "0h 00m"
                    val defaultExitTime = if (isWeekendToday) "Enjoy Weekend" else "--:--"
                    val defaultRemaining = if (isWeekendToday) "Rest & Recharge" else "--h --m left"
                    AttendanceAppWidgetProvider.saveWidgetCache(
                        applicationContext,
                        defaultWorkTime,
                        "--:--",
                        "0h 00m",
                        defaultExitTime,
                        noLogsStatus,
                        0,
                        defaultRemaining,
                        "Last updated: " + AttendanceAppWidgetProvider.getCurrentTime(),
                        actualExitTime = defaultExitTime
                    )
                    sharedPrefs.edit()
                        .putLong(AttendanceSyncReceiver.KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
                        .apply()
                    AttendanceAppWidgetProvider.triggerWidgetUpdate(applicationContext)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in AttendanceWorker: ${e.message}", e)
            result = Result.retry()
        } finally {
            // Guarantee continuous adaptive sync scheduling across all scenarios
            AttendanceSyncManager.scheduleNextSync(applicationContext)
        }

        return result
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = NotificationHelper.CHANNEL_SHIFT_ALERTS
        NotificationHelper.initChannels(applicationContext)
        if (!NotificationHelper.isChannelEnabled(applicationContext, channelId)) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    companion object {
        private const val TAG = "AttendanceWorker"
        const val WORK_NAME = "HRMS_ATTENDANCE_CHECK"

        /**
         * Schedules the periodic background worker as a baseline fallback (every 15 mins).
         * Uses ExistingPeriodicWorkPolicy.KEEP so active schedules are never reset.
         * Also activates the adaptive AttendanceSyncManager.
         */
        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val workRequest = androidx.work.PeriodicWorkRequestBuilder<AttendanceWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            androidx.work.WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            // Activate adaptive real-time sync manager (3-5 min frequency)
            AttendanceSyncManager.scheduleNextSync(context.applicationContext)
        }

        /**
         * Triggers an immediate, expedited background sync via WorkManager.
         */
        fun syncNow(context: Context) {
            try {
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()

                val workRequest = androidx.work.OneTimeWorkRequestBuilder<AttendanceWorker>()
                    .setConstraints(constraints)
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

                androidx.work.WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue syncNow: ${e.message}")
            }
        }
    }
}
