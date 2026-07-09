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

    private fun openConnection(context: Context, apiUrl: String): HttpURLConnection {
        val url = URL(apiUrl)
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                return activeNetwork.openConnection(url) as HttpURLConnection
            }
        }
        return url.openConnection() as HttpURLConnection
    }

    override fun doWork(): Result {
        val accessToken = sharedPrefs.getString("AccessToken", null) ?: return Result.failure()
        val userId = sharedPrefs.getString("UserId", null) ?: return Result.failure()
        val targetHours = sharedPrefs.getFloat("TargetHours", 8.5f)

        // 1. Get formatted local ISO date
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdfDate.format(Date())
        val todayISO = "${todayStr}T00:00:00.000Z"

        // 2. Fetch daily attendance log from API
        val apiUrl = "https://apps.pal.tech/hrms-backend/api/Attendance/GetDailyLog?date=$todayISO&userId=$userId"
        
        try {
            val connection = openConnection(applicationContext, apiUrl)
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 401) {
                // Token has expired; notify user to re-authenticate
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

                // 3. Calculate and sync widget in the background
                val metrics = AttendanceAppWidgetProvider.calculateMetrics(response.toString(), targetHours)
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
                        "Last updated: " + AttendanceAppWidgetProvider.getCurrentTime()
                    )
                    AttendanceAppWidgetProvider.triggerWidgetUpdate(applicationContext)

                    // 4. Trigger Completed Shift Notification (no clocked-in restriction)
                    if (metrics.statusText == "Completed shift!") {
                        val lastNotifiedDate = sharedPrefs.getString("LastNotificationDate", "")
                        if (lastNotifiedDate != todayStr) {
                            sendNotification(
                                "Shift Completed!", 
                                "You have completed ${metrics.workTime} of biometric office time. Time to head home!"
                            )
                            sharedPrefs.edit().putString("LastNotificationDate", todayStr).apply()
                        }
                    }
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
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}
