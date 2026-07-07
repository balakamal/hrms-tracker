package com.balakamal.hrmstracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class AttendanceAppWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.balakamal.hrmstracker.ACTION_REFRESH"
        private const val PREFS_NAME = "HRMS_PREFS"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AttendanceAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            // Set loading state on all widgets
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.attendance_widget)
                views.setTextViewText(R.id.widget_status_text, "Refreshing...")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
            
            // Run network fetch in background thread
            Thread {
                fetchAndRefreshWidget(context)
            }.start()
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.attendance_widget)
        
        // Bind Refresh Button
        val intent = Intent(context, AttendanceAppWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            appWidgetId, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, pendingIntent)
        
        // Restore cached values from SharedPreferences
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val workTime = sharedPrefs.getString("WidgetWorkTime", "0h 00m")
        val firstIn = sharedPrefs.getString("WidgetFirstIn", "--:--")
        val breakTime = sharedPrefs.getString("WidgetBreakTime", "0h 00m")
        val exitTime = sharedPrefs.getString("WidgetExitTime", "--:--")
        val statusText = sharedPrefs.getString("WidgetStatusText", "Not Synced")
        val lastUpdated = sharedPrefs.getString("WidgetLastUpdated", "Last updated: --:--")
        
        // Update views
        views.setTextViewText(R.id.widget_work_time_value, workTime)
        views.setTextViewText(R.id.widget_first_in_value, firstIn)
        views.setTextViewText(R.id.widget_break_time_value, breakTime)
        views.setTextViewText(R.id.widget_exit_time_value, exitTime)
        views.setTextViewText(R.id.widget_status_text, statusText)
        views.setTextViewText(R.id.widget_last_updated, lastUpdated)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun fetchAndRefreshWidget(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("AccessToken", null)
        val userId = sharedPrefs.getString("UserId", null)
        val targetHours = sharedPrefs.getFloat("TargetHours", 8.5f)
        
        if (accessToken == null || userId == null) {
            saveWidgetCache(context, "0h 00m", "--:--", "0h 00m", "--:--", "Please Login in App", "Last updated: " + getCurrentTime())
            triggerWidgetUpdate(context)
            return
        }

        // Fetch logs from API
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdfDate.timeZone = TimeZone.getTimeZone("UTC")
        val todayStr = sdfDate.format(Date())
        val todayISO = "${todayStr}T00:00:00.000Z"
        val apiUrl = "https://apps.pal.tech/hrms-backend/api/Attendance/GetDailyLog?date=$todayISO&userId=$userId"

        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val metrics = calculateMetrics(response.toString(), targetHours)
                if (metrics != null) {
                    saveWidgetCache(
                        context,
                        metrics.workTime,
                        metrics.firstIn,
                        metrics.breakTime,
                        metrics.exitTime,
                        metrics.statusText,
                        "Last updated: " + getCurrentTime()
                    )
                } else {
                    saveWidgetCache(context, "0h 00m", "--:--", "0h 00m", "--:--", "No logs today", "Last updated: " + getCurrentTime())
                }
            } else if (connection.responseCode == 401) {
                saveWidgetCache(context, "0h 00m", "--:--", "0h 00m", "--:--", "Session Expired", "Last updated: " + getCurrentTime())
            } else {
                saveWidgetCache(context, "0h 00m", "--:--", "0h 00m", "--:--", "Sync Error (${connection.responseCode})", "Last updated: " + getCurrentTime())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            saveWidgetCache(context, "0h 00m", "--:--", "0h 00m", "--:--", "Offline", "Last updated: " + getCurrentTime())
        }
        
        triggerWidgetUpdate(context)
    }

    private fun calculateMetrics(jsonStr: String, targetHours: Float): WidgetMetrics? {
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
                val timeStr = logObj.getString("time")
                val isIn = logObj.getInt("isIn")
                val parsedTime = sdfTime.parse("${todayStr}T${timeStr}") ?: continue
                logs.add(LogEntry(parsedTime, isIn))
            }
            logs.sortBy { it.time }

            var workDurationMs = 0L
            var lastInTime: Date? = null
            val firstClockIn = logs.firstOrNull { it.isIn == 0 }?.time ?: logs.first().time

            var lastOutTime: Date? = null
            for (log in logs) {
                if (log.isIn == 0) {
                    lastInTime = log.time
                } else if (log.isIn == 1 && lastInTime != null) {
                    workDurationMs += (log.time.time - lastInTime.time)
                    lastOutTime = log.time
                    lastInTime = null
                }
            }

            val isClockedIn = lastInTime != null
            if (isClockedIn) {
                workDurationMs += (Date().time - lastInTime!!.time)
            }

            val totalWorkMinutes = workDurationMs / 60000.0
            val targetMinutes = targetHours * 60.0
            val remainingMinutes = Math.max(0.0, targetMinutes - totalWorkMinutes)
            val completed = totalWorkMinutes >= targetMinutes

            // Break time
            val endTimestamp = if (isClockedIn) Date() else lastOutTime ?: firstClockIn
            val totalElapsedMs = endTimestamp.time - firstClockIn.time
            val totalBreakMinutes = Math.max(0.0, (totalElapsedMs - workDurationMs) / 60000.0)

            // Formats
            val firstInStr = SimpleDateFormat("hh:mm a", Locale.US).format(firstClockIn)
            val workTimeStr = formatMinutes(totalWorkMinutes)
            val breakTimeStr = formatMinutes(totalBreakMinutes)
            
            var exitTimeStr = "Completed"
            if (!completed) {
                val exitDate = Date(Date().time + (remainingMinutes * 60000).toLong())
                exitTimeStr = SimpleDateFormat("hh:mm a", Locale.US).format(exitDate)
            }

            val statusText = when {
                completed -> "Completed shift!"
                isClockedIn -> "Clocked In"
                else -> "Clocked Out / Break"
            }

            return WidgetMetrics(workTimeStr, firstInStr, breakTimeStr, exitTimeStr, statusText)
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

    private fun getCurrentTime(): String {
        return SimpleDateFormat("hh:mm a", Locale.US).format(Date())
    }

    private fun saveWidgetCache(context: Context, workTime: String, firstIn: String, breakTime: String, exitTime: String, statusText: String, lastUpdated: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("WidgetWorkTime", workTime)
            .putString("WidgetFirstIn", firstIn)
            .putString("WidgetBreakTime", breakTime)
            .putString("WidgetExitTime", exitTime)
            .putString("WidgetStatusText", statusText)
            .putString("WidgetLastUpdated", lastUpdated)
            .apply()
    }

    private fun triggerWidgetUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, AttendanceAppWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    data class LogEntry(val time: Date, val isIn: Int)
    data class WidgetMetrics(val workTime: String, val firstIn: String, val breakTime: String, val exitTime: String, val statusText: String)
}
