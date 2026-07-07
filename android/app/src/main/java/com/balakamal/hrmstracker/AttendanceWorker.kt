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
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class AttendanceWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("HRMS_PREFS", Context.MODE_PRIVATE)

    override fun doWork(): Result {
        val accessToken = sharedPrefs.getString("AccessToken", null) ?: return Result.failure()
        val userId = sharedPrefs.getString("UserId", null) ?: return Result.failure()
        val targetHours = sharedPrefs.getFloat("TargetHours", 8.5f)

        // 1. Get formatted local ISO date
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdfDate.timeZone = TimeZone.getTimeZone("UTC")
        val todayStr = sdfDate.format(Date())
        val todayISO = "${todayStr}T00:00:00.000Z"

        // 2. Fetch daily attendance log from API
        val apiUrl = "https://apps.pal.tech/hrms-backend/api/Attendance/GetDailyLog?date=$todayISO&userId=$userId"
        
        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
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

                // 3. Process logs and calculate work hours
                val parsedData = parseAndCalculateAttendance(response.toString(), targetHours)
                if (parsedData != null) {
                    val completed = parsedData.first
                    val workMin = parsedData.second
                    val isClockedIn = parsedData.third

                    if (completed && isClockedIn) {
                        // Check if we already notified the user today
                        val lastNotifiedDate = sharedPrefs.getString("LastNotificationDate", "")
                        if (lastNotifiedDate != todayStr) {
                            sendNotification(
                                "Shift Completed!", 
                                "You have completed ${formatMinutes(workMin)} of biometric office time. Time to head home!"
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

    private fun parseAndCalculateAttendance(jsonStr: String, targetHours: Float): Triple<Boolean, Double, Boolean>? {
        try {
            val jsonObject = JSONObject(jsonStr)
            val data = jsonObject.optJSONObject("data") ?: return null
            val logsArray = data.optJSONArray("attendanceDailyLogs") ?: return null
            if (logsArray.length() == 0) return null

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val sdfTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

            val logs = mutableListOf<LogEntry>()
            for (i in 0 until logsArray.length()) {
                val logObj = logsArray.getJSONObject(i)
                val timeStr = logObj.getString("time") // "HH:mm:ss"
                val isIn = logObj.getInt("isIn")       // 0=In, 1=Out
                val parsedTime = sdfTime.parse("${todayStr}T${timeStr}") ?: continue
                logs.add(LogEntry(parsedTime, isIn))
            }
            logs.sortBy { it.time }

            var workDurationMs = 0L
            var lastInTime: Date? = null

            for (log in logs) {
                if (log.isIn == 0) {
                    lastInTime = log.time
                } else if (log.isIn == 1 && lastInTime != null) {
                    workDurationMs += (log.time.time - lastInTime.time)
                    lastInTime = null
                }
            }

            val isClockedIn = lastInTime != null
            if (isClockedIn) {
                workDurationMs += (Date().time - lastInTime!!.time)
            }

            val totalWorkMinutes = workDurationMs / 60000.0
            val targetMinutes = targetHours * 60.0

            return Triple(totalWorkMinutes >= targetMinutes, totalWorkMinutes, isClockedIn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun formatMinutes(totalMin: Double): String {
        val h = (totalMin / 60).toInt()
        val m = (totalMin % 60).toInt()
        return "${h}h ${String.format("%02d", m)}m"
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

    data class LogEntry(val time: Date, val isIn: Int)
}
