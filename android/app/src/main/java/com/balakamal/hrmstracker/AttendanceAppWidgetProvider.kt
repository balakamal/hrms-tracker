package com.balakamal.hrmstracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import android.net.ConnectivityManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// Top-level data classes to simplify type inference and cross-file package visibility
data class LogEntry(val time: Date, val isIn: Int)

data class WidgetMetrics(
    val workTime: String,
    val firstIn: String,
    val breakTime: String,
    val exitTime: String,
    val statusText: String,
    val progressPercent: Int,
    val progressRemaining: String
)

open class AttendanceAppWidgetProvider : AppWidgetProvider() {

    open val defaultLayoutId: Int = R.layout.attendance_widget

    companion object {
        const val ACTION_REFRESH = "com.balakamal.hrmstracker.ACTION_REFRESH"
        private const val PREFS_NAME = "HRMS_PREFS"

        // Companion-level helper to update any widget instance with dynamic size checks
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, defaultLayoutId: Int) {
            try {
                val layoutId = getLayoutForWidgetSize(appWidgetManager, appWidgetId, defaultLayoutId)
                val views = RemoteViews(context.packageName, layoutId)
                
                // Bind Click on Widget to Launch App
                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val appPendingIntent = PendingIntent.getActivity(
                    context, 
                    0, 
                    appIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

                // Bind 1-Tap Home-Screen Refresh FAB to trigger background sync without opening app
                val refreshIntent = Intent(context, AttendanceAppWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context, 
                    appWidgetId, 
                    refreshIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)
                
                // Restore cached values from SharedPreferences
                val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val workTime = sharedPrefs.getString("WidgetWorkTime", "0h 00m")
                val firstIn = sharedPrefs.getString("WidgetFirstIn", "--:--")
                val breakTime = sharedPrefs.getString("WidgetBreakTime", "0h 00m")
                val exitTime = sharedPrefs.getString("WidgetExitTime", "--:--")
                val statusText = sharedPrefs.getString("WidgetStatusText", "Not Synced")
                val progressPercent = sharedPrefs.getInt("WidgetProgressPercent", 0)
                val progressRemaining = sharedPrefs.getString("WidgetProgressRemaining", "--h --m left")
                val lastUpdated = sharedPrefs.getString("WidgetLastUpdated", "Last updated: --:--")
                val isWfh = sharedPrefs.getBoolean("WfhMode", false)
                
                val isCompleted = progressPercent >= 100 || statusText?.contains("Complete", ignoreCase = true) == true
                val isWeekOff = statusText?.contains("Week Off", ignoreCase = true) == true || (isWeekend() && (statusText == "Not Clocked In" || statusText == "No logs today" || statusText == "Not Synced"))
                val displayStatus = when {
                    isWfh && (statusText == "Not Clocked In" || statusText == "No logs today" || statusText == "Not Synced") -> "Working from Home"
                    isWeekOff -> "Week Off"
                    else -> statusText ?: "Not Synced"
                }

                // Apply User's Custom Background & Transparency
                views.setInt(R.id.widget_root, "setBackgroundResource", getWidgetBackground(context))

                // Common view updates
                views.setTextViewText(R.id.widget_status_text, displayStatus)
                views.setTextViewText(R.id.widget_work_time_value, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                views.setViewVisibility(R.id.widget_wfh_badge, if (isWfh) android.view.View.VISIBLE else android.view.View.GONE)
                
                // Dynamic status color & chip styling
                val statusColor = when {
                    isCompleted -> 0xFF38BDF8.toInt() // Luminous sky cyan
                    isWfh -> 0xFFFBBF24.toInt() // Warm golden amber
                    isWeekOff -> 0xFFA78BFA.toInt() // Samsung One UI lavender
                    displayStatus.contains("Break", ignoreCase = true) -> 0xFFF59E0B.toInt() // Warm amber
                    displayStatus.contains("Clocked In", ignoreCase = true) -> 0xFF00D09C.toInt() // Samsung One UI emerald mint
                    displayStatus.contains("Sync", ignoreCase = true) -> 0xFF60A5FA.toInt() // Soft blue
                    else -> 0xFF94A3B8.toInt() // Slate grey
                }
                views.setTextColor(R.id.widget_status_text, statusColor)

                // Layout-specific bindings
                if (layoutId == R.layout.attendance_widget_small) {
                    val smallStatus = when {
                        isWfh && (exitTime == "--:--" || exitTime == "Completed") -> "Remote Day"
                        isWeekOff -> "Week Off"
                        else -> "Exit: $exitTime"
                    }
                    views.setTextViewText(R.id.widget_status_text, smallStatus)
                    views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)
                } else if (layoutId == R.layout.attendance_widget_medium) {
                    views.setTextViewText(R.id.widget_exit_time_value, if (isWeekOff && exitTime == "--:--") "Enjoy Weekend" else exitTime)
                    views.setTextViewText(R.id.widget_last_updated, "Updated: " + getCurrentTime())
                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent%")
                } else if (layoutId == R.layout.attendance_widget_countdown) {
                    views.setTextViewText(R.id.widget_exit_time_value, if (isWeekOff && exitTime == "--:--") "Week Off" else exitTime)
                    views.setTextViewText(R.id.widget_progress_remaining, if (isWfh) "Remote Workspace" else if (isWeekOff) "Enjoy Weekend" else progressRemaining)
                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent%")
                    views.setTextViewText(R.id.widget_status_text, displayStatus)
                } else if (layoutId == R.layout.attendance_widget_timesheet) {
                    views.setTextViewText(R.id.widget_work_time_value, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                    views.setTextViewText(R.id.widget_status_text, if (isCompleted) "Goal Met • Submit" else if (isWeekOff) "Week Off" else "$displayStatus • $progressPercent%")

                    // 1-Tap direct deep-link launcher to HRMS Timesheet
                    val timesheetIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("shortcut_url", "https://apps.pal.tech/hrms/me/timesheet")
                    }
                    val timesheetPendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId * 10 + 1,
                        timesheetIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_timesheet, timesheetPendingIntent)
                } else if (layoutId == R.layout.attendance_widget_dial) {
                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent%")
                    views.setTextViewText(R.id.widget_work_time_value, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                    views.setTextViewText(R.id.widget_status_text, if (isCompleted) "Shift Complete" else if (isWeekOff) "Week Off" else progressRemaining)
                } else if (layoutId == R.layout.attendance_widget) {
                    val subtitle = when {
                        isWfh -> "REMOTE WORKDAY"
                        isWeekOff -> "WEEK OFF"
                        else -> "TIME AT DESK"
                    }
                    views.setTextViewText(R.id.widget_work_subtitle, subtitle)
                    val formattedStatus = when {
                        isCompleted -> "Shift Complete • 100%"
                        isWfh -> "Remote Workspace"
                        isWeekOff -> "Week Off • Recharge"
                        else -> "$displayStatus • $progressPercent%"
                    }
                    views.setTextViewText(R.id.widget_status_text, formattedStatus)
                    views.setTextViewText(R.id.widget_exit_time_value, if (isWeekOff && exitTime == "--:--") "Enjoy Weekend" else exitTime)
                    views.setTextViewText(R.id.widget_first_in_value, firstIn)
                    views.setTextViewText(R.id.widget_break_time_value, breakTime)
                    views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)
                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent% Goal")
                    views.setTextViewText(R.id.widget_progress_remaining, if (isWfh) "Biometrics Paused" else if (isWeekOff) "Rest & Recharge" else progressRemaining)
                }
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                android.util.Log.e("HRMSWidget", "updateAppWidget crash: ${e.message}", e)
            }
        }

        fun getLayoutForWidgetSize(appWidgetManager: AppWidgetManager, appWidgetId: Int, defaultLayoutId: Int): Int {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val category = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
            
            // If it is on Lockscreen/Keyguard:
            // Large widget falls back to Medium layout to fit keyguard height;
            // all other compact widgets use their own designated layouts.
            if (category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) {
                return if (defaultLayoutId == R.layout.attendance_widget) {
                    R.layout.attendance_widget_medium
                } else {
                    defaultLayoutId
                }
            }
            
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            
            if (minWidth == 0 || maxHeight == 0) {
                return defaultLayoutId
            }
            
            // Respect the provider's designated layout, only resizing when dragged to smaller breakpoints
            if (defaultLayoutId == R.layout.attendance_widget) {
                if (maxHeight < 85 || minWidth < 160) {
                    return R.layout.attendance_widget_small
                } else if (maxHeight < 110 || minWidth < 220) {
                    return R.layout.attendance_widget_medium
                }
                return R.layout.attendance_widget
            }
            
            if (defaultLayoutId == R.layout.attendance_widget_medium) {
                if (minWidth < 160) {
                    return R.layout.attendance_widget_small
                }
                return R.layout.attendance_widget_medium
            }
            
            if (defaultLayoutId == R.layout.attendance_widget_countdown ||
                defaultLayoutId == R.layout.attendance_widget_timesheet ||
                defaultLayoutId == R.layout.attendance_widget_dial) {
                return defaultLayoutId
            }

            return defaultLayoutId
        }

        fun saveWidgetCache(
            context: Context,
            workTime: String,
            firstIn: String,
            breakTime: String,
            exitTime: String,
            statusText: String,
            progressPercent: Int,
            progressRemaining: String,
            lastUpdated: String
        ) {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putString("WidgetWorkTime", workTime)
                .putString("WidgetFirstIn", firstIn)
                .putString("WidgetBreakTime", breakTime)
                .putString("WidgetExitTime", exitTime)
                .putString("WidgetStatusText", statusText)
                .putInt("WidgetProgressPercent", progressPercent)
                .putString("WidgetProgressRemaining", progressRemaining)
                .putString("WidgetLastUpdated", lastUpdated)
                .apply()
        }

        fun triggerWidgetUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            
            // Large Widgets
            val largeComponent = ComponentName(context, AttendanceAppWidgetProvider::class.java)
            val largeIds = appWidgetManager.getAppWidgetIds(largeComponent)
            for (id in largeIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget)
            }

            // Medium Widgets
            val mediumComponent = ComponentName(context, AttendanceAppWidgetProviderMedium::class.java)
            val mediumIds = appWidgetManager.getAppWidgetIds(mediumComponent)
            for (id in mediumIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget_medium)
            }

            // Small Widgets
            val smallComponent = ComponentName(context, AttendanceAppWidgetProviderSmall::class.java)
            val smallIds = appWidgetManager.getAppWidgetIds(smallComponent)
            for (id in smallIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget_small)
            }

            // Countdown Pack-up Clock
            val countdownComponent = ComponentName(context, WidgetCountdownProvider::class.java)
            val countdownIds = appWidgetManager.getAppWidgetIds(countdownComponent)
            for (id in countdownIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget_countdown)
            }

            // Timesheet Companion
            val timesheetComponent = ComponentName(context, WidgetTimesheetProvider::class.java)
            val timesheetIds = appWidgetManager.getAppWidgetIds(timesheetComponent)
            for (id in timesheetIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget_timesheet)
            }

            // Dial Widget
            val dialComponent = ComponentName(context, WidgetDialProvider::class.java)
            val dialIds = appWidgetManager.getAppWidgetIds(dialComponent)
            for (id in dialIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget_dial)
            }
        }

        fun getCurrentTime(): String {
            return SimpleDateFormat("hh:mm a", Locale.US).format(Date())
        }

        fun formatMinutes(totalMin: Double): String {
            val h = (totalMin / 60).toInt()
            val m = (totalMin % 60).toInt()
            return "${h}h ${String.format("%02d", m)}m"
        }

        fun calculateMetrics(jsonStr: String, targetHours: Float): WidgetMetrics? {
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

                val progressPercent = Math.min(100, ((totalWorkMinutes / targetMinutes) * 100).toInt())
                val progressRemaining = if (completed) "0m left" else "${formatMinutes(remainingMinutes)} left"

                val statusText = when {
                    completed -> "Shift Complete"
                    isClockedIn -> "Clocked In"
                    progressPercent > 0 -> "On Break"
                    else -> "Not Clocked In"
                }

                return WidgetMetrics(workTimeStr, firstInStr, breakTimeStr, exitTimeStr, statusText, progressPercent, progressRemaining)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        fun saveWidgetCacheError(context: Context, errorText: String) {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val workTime = sharedPrefs.getString("WidgetWorkTime", "0h 00m") ?: "0h 00m"
            val firstIn = sharedPrefs.getString("WidgetFirstIn", "--:--") ?: "--:--"
            val breakTime = sharedPrefs.getString("WidgetBreakTime", "0h 00m") ?: "0h 00m"
            val exitTime = sharedPrefs.getString("WidgetExitTime", "--:--") ?: "--:--"
            val progressPercent = sharedPrefs.getInt("WidgetProgressPercent", 0)
            val progressRemaining = sharedPrefs.getString("WidgetProgressRemaining", "--h --m left") ?: "--h --m left"
            val lastUpdated = sharedPrefs.getString("WidgetLastUpdated", "Last updated: --:--") ?: "Last updated: --:--"
            
            val currentStatus = sharedPrefs.getString("WidgetStatusText", "Not Synced") ?: "Not Synced"
            val finalStatus = if (errorText.contains("Offline")) {
                if (currentStatus.contains("Offline")) currentStatus else "$currentStatus (Offline)"
            } else {
                errorText
            }

            saveWidgetCache(
                context,
                workTime,
                firstIn,
                breakTime,
                exitTime,
                finalStatus,
                progressPercent,
                progressRemaining,
                lastUpdated
            )
        }

        fun checkEmployeeWfhStatus(context: Context, accessToken: String, userId: String): Boolean? {
            try {
                val statusUrl = "https://apps.pal.tech/hrms-backend/api/Employee/GetEmployeeStatus?employeeIds=$userId"
                val connection = TokenManager.openConnection(context, statusUrl)
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.connect()

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()
                    val json = JSONObject(sb.toString())
                    val dataObj = json.optJSONObject("data")
                    if (dataObj != null) {
                        val userObj = dataObj.optJSONObject(userId)
                            ?: if (dataObj.keys().hasNext()) dataObj.optJSONObject(dataObj.keys().next()) else null
                        if (userObj != null && userObj.has("WFH")) {
                            return userObj.optInt("WFH", 0) == 1
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HRMSWidget", "checkEmployeeWfhStatus failed: ${e.message}")
            }
            return null
        }

        fun isWeekend(): Boolean {
            val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            return day == Calendar.SATURDAY || day == Calendar.SUNDAY
        }

        fun getWidgetBackground(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val theme = prefs.getString("WidgetTheme", "DARK") ?: "DARK"
            val opacity = prefs.getInt("WidgetOpacity", 100)

            return when (theme) {
                "AMOLED" -> R.drawable.widget_bg_amoled
                "TRANSPARENT" -> R.drawable.widget_bg_transparent
                "LIGHT" -> when {
                    opacity >= 90 -> R.drawable.widget_bg_light_100
                    opacity >= 65 -> R.drawable.widget_bg_light_75
                    opacity >= 40 -> R.drawable.widget_bg_light_50
                    opacity >= 15 -> R.drawable.widget_bg_light_25
                    else -> R.drawable.widget_bg_transparent
                }
                else -> when { // DARK
                    opacity >= 90 -> R.drawable.widget_bg_dark_100
                    opacity >= 65 -> R.drawable.widget_bg_dark_75
                    opacity >= 40 -> R.drawable.widget_bg_dark_50
                    opacity >= 15 -> R.drawable.widget_bg_dark_25
                    else -> R.drawable.widget_bg_transparent
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, defaultLayoutId)
            }
            // Use goAsync() to prevent Android from terminating broadcast receiver process during background network fetch
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            Thread {
                try {
                    fetchAndRefreshWidget(appContext)
                } catch (e: Exception) {
                    android.util.Log.e("HRMSWidget", "onUpdate fetch error: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }.start()
        } catch (e: Exception) {
            android.util.Log.e("HRMSWidget", "onUpdate crash: ${e.message}", e)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId, defaultLayoutId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            
            // Set loading state on all sizes of widgets
            val providers = listOf(
                AttendanceAppWidgetProvider::class.java to R.layout.attendance_widget,
                AttendanceAppWidgetProviderMedium::class.java to R.layout.attendance_widget_medium,
                AttendanceAppWidgetProviderSmall::class.java to R.layout.attendance_widget_small,
                WidgetCountdownProvider::class.java to R.layout.attendance_widget_countdown,
                WidgetTimesheetProvider::class.java to R.layout.attendance_widget_timesheet,
                WidgetDialProvider::class.java to R.layout.attendance_widget_dial
            )

            for ((providerClass, defaultLayout) in providers) {
                val componentName = ComponentName(context, providerClass.name)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                for (appWidgetId in ids) {
                    val layoutId = getLayoutForWidgetSize(appWidgetManager, appWidgetId, defaultLayout)
                    val views = RemoteViews(context.packageName, layoutId)
                    views.setTextViewText(R.id.widget_status_text, "Syncing...")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
            
            // Run network fetch in background thread using applicationContext and goAsync
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            Thread {
                try {
                    fetchAndRefreshWidget(appContext)
                } catch (e: Exception) {
                    android.util.Log.e("HRMSWidget", "Refresh thread error: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }.start()
        }
    }

    private fun fetchAndRefreshWidget(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var accessToken = TokenManager.getValidAccessToken(context)
        val userId = sharedPrefs.getString("UserId", null)
        val targetHours = sharedPrefs.getFloat("TargetHours", 8.5f)
        val isWfh = sharedPrefs.getBoolean("WfhMode", false)
        
        if (accessToken == null || userId == null) {
            val isWeekendToday = isWeekend()
            val defaultStatus = when {
                isWfh -> "Working from Home"
                isWeekendToday -> "Week Off"
                else -> "Please Login in App"
            }
            val defaultWork = if (isWeekendToday) "Week Off" else "0h 00m"
            val defaultExit = if (isWeekendToday) "Enjoy Weekend" else "--:--"
            val defaultRem = if (isWeekendToday) "Rest & Recharge" else "--h --m left"
            saveWidgetCache(context, defaultWork, "--:--", "0h 00m", defaultExit, defaultStatus, 0, defaultRem, "Last updated: " + getCurrentTime())
            triggerWidgetUpdate(context)
            return
        }

        // Connectivity pre-check to distinguish true offline from process death
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetworkInfo
            if (activeNetwork == null || !activeNetwork.isConnected) {
                saveWidgetCacheError(context, "Offline (No Network)")
                triggerWidgetUpdate(context)
                return
            }
        } catch (e: Exception) {
            // Ignore connectivity check failure and proceed to network call
        }

        // Fetch logs from API (Uses local device timezone for local date query)
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdfDate.format(Date())
        val todayISO = "${todayStr}T00:00:00.000Z"
        val apiUrl = "https://apps.pal.tech/hrms-backend/api/Attendance/GetDailyLog?date=$todayISO&userId=$userId"

        try {
            var connection = TokenManager.openConnection(context, apiUrl)
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()

            if (connection.responseCode == 401) {
                // Access token expired; silently rotate token and retry once
                val refreshResult = TokenManager.refreshTokens(context)
                if (refreshResult is TokenManager.RefreshResult.Success) {
                    accessToken = refreshResult.accessToken
                    connection.disconnect()
                    connection = TokenManager.openConnection(context, apiUrl)
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.connect()
                } else if (refreshResult is TokenManager.RefreshResult.SessionExpired) {
                    saveWidgetCache(context, "0h 00m", "--:--", "0h 00m", "--:--", "Session Expired", 0, "--h --m left", "Last updated: " + getCurrentTime())
                    triggerWidgetUpdate(context)
                    return
                } else {
                    saveWidgetCacheError(context, "Sync Error (401)")
                    triggerWidgetUpdate(context)
                    return
                }
            }

            if (connection.responseCode == 401) {
                saveWidgetCache(context, "0h 00m", "--:--", "0h 00m", "--:--", "Session Expired", 0, "--h --m left", "Last updated: " + getCurrentTime())
                triggerWidgetUpdate(context)
                return
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val remoteWfh = checkEmployeeWfhStatus(context, accessToken, userId)
                if (remoteWfh != null) {
                    sharedPrefs.edit().putBoolean("WfhMode", remoteWfh).apply()
                }

                val metrics = calculateMetrics(response.toString(), targetHours)
                if (metrics != null) {
                    saveWidgetCache(
                        context,
                        metrics.workTime,
                        metrics.firstIn,
                        metrics.breakTime,
                        metrics.exitTime,
                        metrics.statusText,
                        metrics.progressPercent,
                        metrics.progressRemaining,
                        "Last updated: " + getCurrentTime()
                    )
                    // Check and trigger Completed notification if complete
                    checkAndSendNotification(context, response.toString(), targetHours)
                } else {
                    val isWfh = sharedPrefs.getBoolean("WfhMode", false)
                    val isWeekendToday = isWeekend()
                    val noLogsStatus = when {
                        isWfh -> "Working from Home"
                        isWeekendToday -> "Week Off"
                        else -> "No logs today"
                    }
                    val defaultWorkTime = if (isWeekendToday) "Week Off" else "0h 00m"
                    val defaultExitTime = if (isWeekendToday) "Enjoy Weekend" else "--:--"
                    val defaultRemaining = if (isWeekendToday) "Rest & Recharge" else "--h --m left"
                    saveWidgetCache(context, defaultWorkTime, "--:--", "0h 00m", defaultExitTime, noLogsStatus, 0, defaultRemaining, "Last updated: " + getCurrentTime())
                }
            } else {
                saveWidgetCacheError(context, "Sync Error (${connection.responseCode})")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("HRMSWidget", "Fetch failed: ${e.message}", e)
            val errorDetails = e.javaClass.simpleName + if (e.message != null) ": ${e.message}" else ""
            val statusMessage = if (errorDetails.length > 25) errorDetails.substring(0, 22) + "..." else errorDetails
            saveWidgetCacheError(context, "Offline ($statusMessage)")
        }
        
        triggerWidgetUpdate(context)
    }

    private fun checkAndSendNotification(context: Context, jsonStr: String, targetHours: Float) {
        try {
            val jsonObject = JSONObject(jsonStr)
            val data = jsonObject.optJSONObject("data") ?: return
            val logsArray = data.optJSONArray("attendanceDailyLogs") ?: return
            if (logsArray.length() == 0) return

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
            val completed = totalWorkMinutes >= targetMinutes

            if (completed) {
                val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val notifyShiftComplete = sharedPrefs.getBoolean("NotifShiftComplete", true)
                val lastNotifiedDate = sharedPrefs.getString("LastNotificationDate", "")
                if (notifyShiftComplete && lastNotifiedDate != todayStr) {
                    sendNotification(
                        context,
                        "Shift Completed!",
                        "You have completed ${formatMinutes(totalWorkMinutes)} of biometric office time. Time to head home!"
                    )
                    sharedPrefs.edit().putString("LastNotificationDate", todayStr).apply()
                }
                // Start 2-minute repeating leave office alerts if active & not dismissed
                ShiftAlertManager.checkAndTriggerNudge(context, formatMinutes(totalWorkMinutes))
            }
        } catch (e: Exception) {
            android.util.Log.e("HRMSWidget", "checkAndSendNotification error: ${e.message}", e)
        }
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hrms_tracker_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Shift Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}

class AttendanceAppWidgetProviderMedium : AttendanceAppWidgetProvider() {
    override val defaultLayoutId: Int = R.layout.attendance_widget_medium
}

class AttendanceAppWidgetProviderSmall : AttendanceAppWidgetProvider() {
    override val defaultLayoutId: Int = R.layout.attendance_widget_small
}

class WidgetCountdownProvider : AttendanceAppWidgetProvider() {
    override val defaultLayoutId: Int = R.layout.attendance_widget_countdown
}

class WidgetTimesheetProvider : AttendanceAppWidgetProvider() {
    override val defaultLayoutId: Int = R.layout.attendance_widget_timesheet
}

class WidgetDialProvider : AttendanceAppWidgetProvider() {
    override val defaultLayoutId: Int = R.layout.attendance_widget_dial
}
