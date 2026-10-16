package com.balakamal.hrmstracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import android.net.ConnectivityManager
import androidx.work.WorkerParameters
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class AttendanceWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("HRMS_PREFS", Context.MODE_PRIVATE)

    override fun doWork(): Result {
        var accessToken = TokenManager.getValidAccessToken(applicationContext) ?: return Result.failure()
        var userId = sharedPrefs.getString("UserId", null)
        if (userId.isNullOrEmpty()) {
            val identity = TokenManager.fetchUserIdentity(applicationContext, accessToken)
            if (identity != null) {
                userId = identity.first
            } else {
                return Result.failure()
            }
        }
        val targetHours = sharedPrefs.getFloat("TargetHours", 8.5f)

        // Office-hours budget check: avoid battery drain & backend spam outside of office hours unless actively clocked in
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val isClockedIn = sharedPrefs.getBoolean("WidgetIsClockedIn", false)

        if (!isClockedIn && (isWeekend || hour < 7 || (hour == 7 && minute < 30) || hour >= 21)) {
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
        
        try {
            var connection = TokenManager.openConnection(applicationContext, apiUrl)
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 401) {
                // Access token expired; silently rotate token and retry once
                val refreshResult = TokenManager.refreshTokens(applicationContext)
                when (refreshResult) {
                    is TokenManager.RefreshResult.Success -> {
                        accessToken = refreshResult.accessToken
                        connection.disconnect()
                        connection = TokenManager.openConnection(applicationContext, apiUrl)
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $accessToken")
                        connection.setRequestProperty("Accept", "application/json")
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                    }
                    is TokenManager.RefreshResult.SessionExpired -> {
                        sendNotification("Session Expired", "Please open the HRMS Insights app to log in again.")
                        return Result.failure()
                    }
                    is TokenManager.RefreshResult.NetworkError -> {
                        return Result.retry()
                    }
                }
            }

            if (connection.responseCode == 401) {
                // Retried token is still unauthorized
                sendNotification("Session Expired", "Please open the HRMS Insights app to log in again.")
                return Result.failure()
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
                val policyHours = AttendanceAppWidgetProvider.fetchRequiredWorkingHours(applicationContext, accessToken, userId)
                val effectiveTargetHours = policyHours ?: sharedPrefs.getFloat("TargetHours", 8.5f)
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
                        avgBreakMinutes = metrics.avgBreakMinutes
                    )
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
                    AttendanceAppWidgetProvider.triggerWidgetUpdate(applicationContext)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }

        return Result.success()
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hrms_tracker_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Shift Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

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
        const val WORK_NAME = "HRMS_ATTENDANCE_CHECK"

        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val workRequest = androidx.work.PeriodicWorkRequestBuilder<AttendanceWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            androidx.work.WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
