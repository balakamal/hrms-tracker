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
import android.content.res.Configuration
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
    val breakCount: String,
    val exitTime: String,
    val actualExitTime: String,
    val statusText: String,
    val progressPercent: Int,
    val progressRemaining: String,
    val baseWorkMinutes: Double = 0.0,
    val lastInTimestamp: Long = 0L,
    val isClockedIn: Boolean = false,
    val targetMinutes: Double = 510.0,
    val baseBreakMinutes: Double = 0.0,
    val avgBreakMinutes: Int = 60
)

open class AttendanceAppWidgetProvider : AppWidgetProvider() {

    open val defaultLayoutId: Int = R.layout.attendance_widget

    companion object {
        const val ACTION_REFRESH = "com.balakamal.hrmstracker.ACTION_REFRESH"
        const val ACTION_CYCLE_PAGE = "com.balakamal.hrmstracker.ACTION_CYCLE_PAGE"
        const val ACTION_NEXT_PAGE = "com.balakamal.hrmstracker.ACTION_NEXT_PAGE"
        const val ACTION_PREV_PAGE = "com.balakamal.hrmstracker.ACTION_PREV_PAGE"
        const val EXTRA_WIDGET_ID = "appWidgetId"
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
                val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val cacheDate = sharedPrefs.getString("WidgetCacheDate", "")
                val isCacheFromToday = cacheDate == todayDateStr

                val rawWorkTime = sharedPrefs.getString("WidgetWorkTime", "0h 00m") ?: "0h 00m"
                val firstIn = sharedPrefs.getString("WidgetFirstIn", "--:--") ?: "--:--"
                val breakTime = sharedPrefs.getString("WidgetBreakTime", "0h 00m") ?: "0h 00m"
                val breakCount = sharedPrefs.getString("WidgetBreakCount", "0 Breaks Today") ?: "0 Breaks Today"
                val rawExitTime = sharedPrefs.getString("WidgetExitTime", "--:--") ?: "--:--"
                val rawActualExitTime = sharedPrefs.getString("WidgetActualExitTime", "--:--") ?: "--:--"
                val rawStatusText = sharedPrefs.getString("WidgetStatusText", "Not Synced") ?: "Not Synced"
                val rawProgressPercent = sharedPrefs.getInt("WidgetProgressPercent", 0)
                val rawProgressRemaining = sharedPrefs.getString("WidgetProgressRemaining", "--h --m left") ?: "--h --m left"
                val lastUpdated = sharedPrefs.getString("WidgetLastUpdated", "Last updated: --:--") ?: "Last updated: --:--"
                val isWfh = sharedPrefs.getBoolean("WfhMode", false)

                // Zero-spam live local extrapolation: dynamically calculate elapsed work minutes while clocked in
                val isClockedIn = sharedPrefs.getBoolean("WidgetIsClockedIn", false)
                val lastInTimestamp = sharedPrefs.getLong("WidgetLastInTimestamp", 0L)
                val baseWorkMinutes = sharedPrefs.getFloat("WidgetBaseWorkMinutes", -1f)
                val targetMinutes = sharedPrefs.getFloat("WidgetTargetMinutes", 510f)

                val workTime: String
                val progressPercent: Int
                val progressRemaining: String
                val isCompleted: Boolean
                val statusText: String
                val exitTime: String
                val actualExitTime: String

                if (isClockedIn && lastInTimestamp > 0 && baseWorkMinutes >= 0 && isCacheFromToday) {
                    val elapsedSinceLastIn = Math.max(0L, System.currentTimeMillis() - lastInTimestamp) / 60000.0
                    val liveWorkMinutes = baseWorkMinutes + elapsedSinceLastIn
                    val remainingMinutes = Math.max(0.0, targetMinutes - liveWorkMinutes)
                    isCompleted = liveWorkMinutes >= targetMinutes
                    progressPercent = Math.min(100, ((liveWorkMinutes / targetMinutes) * 100).toInt())
                    workTime = formatMinutes(liveWorkMinutes)
                    progressRemaining = if (isCompleted) "0m left" else "${formatMinutes(remainingMinutes)} left"
                    statusText = if (isCompleted) "Shift Complete" else "Clocked In"
                    exitTime = if (isCompleted) "Completed" else rawExitTime
                    actualExitTime = if (isCompleted) "Completed" else rawActualExitTime
                } else {
                    workTime = rawWorkTime
                    progressPercent = rawProgressPercent
                    progressRemaining = rawProgressRemaining
                    isCompleted = rawProgressPercent >= 100 || rawStatusText.contains("Complete", ignoreCase = true)
                    statusText = rawStatusText
                    exitTime = rawExitTime
                    actualExitTime = rawActualExitTime
                }

                val isWeekOff = statusText.contains("Week Off", ignoreCase = true) || (isWeekend() && (statusText == "Not Clocked In" || statusText == "No logs today" || statusText == "Not Synced"))
                val displayStatus = when {
                    isWfh && (statusText == "Not Clocked In" || statusText == "No logs today" || statusText == "Not Synced") -> "Working from Home"
                    isWeekOff -> "Week Off"
                    else -> statusText
                }

                // Determine Theme & Effective Night Mode
                val isSystemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val theme = sharedPrefs.getString("WidgetTheme", "SYSTEM") ?: "SYSTEM"
                val isEffectiveDark = when (theme) {
                    "LIGHT" -> false
                    "DARK", "AMOLED" -> true
                    "TRANSPARENT" -> isSystemNight
                    else -> isSystemNight // "SYSTEM"
                }

                // Adaptive Typography & Inset Surfaces
                val colorTextPrimary = if (isEffectiveDark) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
                val colorTextLabel = if (isEffectiveDark) 0xFF94A3B8.toInt() else 0xFF64748B.toInt()
                val colorTextStat = if (isEffectiveDark) 0xFFF1F5F9.toInt() else 0xFF1E293B.toInt()
                val colorTextSubtle = if (isEffectiveDark) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
                val colorTextMuted = if (isEffectiveDark) 0xFF64748B.toInt() else 0xFF94A3B8.toInt()
                val colorSkyAccent = if (isEffectiveDark) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()

                val heroCardBg = if (isEffectiveDark) R.drawable.widget_card_hero_dark else R.drawable.widget_card_hero_light
                val statCellBg = if (isEffectiveDark) R.drawable.widget_card_cell_dark else R.drawable.widget_card_cell_light
                val refreshBtnBg = if (isEffectiveDark) R.drawable.widget_btn_refresh_dark else R.drawable.widget_btn_refresh_light

                // Apply User's Custom Background & Transparency
                val isOneUiLayout = layoutId == R.layout.attendance_widget_oneui_capsule ||
                                    layoutId == R.layout.attendance_widget_oneui_dualpod ||
                                    layoutId == R.layout.attendance_widget_oneui_matrix
                if (!isOneUiLayout) {
                    views.setInt(R.id.widget_root, "setBackgroundResource", getWidgetBackground(context))
                } else {
                    views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_transparent)
                }

                // Common view updates
                views.setTextViewText(R.id.widget_status_text, displayStatus)
                views.setTextViewText(R.id.widget_work_time_value, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                views.setViewVisibility(R.id.widget_wfh_badge, if (isWfh) android.view.View.VISIBLE else android.view.View.GONE)

                // Refresh button background & header branding
                views.setInt(R.id.widget_btn_refresh, "setBackgroundResource", refreshBtnBg)
                views.setTextColor(R.id.widget_title, colorTextLabel)

                // Adaptive WFH Badge
                views.setInt(R.id.widget_wfh_badge, "setBackgroundResource", if (isEffectiveDark) R.drawable.bg_widget_wfh_badge else R.drawable.bg_widget_wfh_badge_light)
                views.setTextColor(R.id.widget_wfh_text, if (isEffectiveDark) 0xFFFBBF24.toInt() else 0xFFA16207.toInt())

                // Dynamic status color & chip styling
                val statusColor = if (isEffectiveDark) {
                    when {
                        isCompleted -> 0xFF10B981.toInt()
                        isWfh -> 0xFFFBBF24.toInt()
                        isWeekOff -> 0xFF38BDF8.toInt()
                        displayStatus.contains("Break", ignoreCase = true) -> 0xFFF59E0B.toInt()
                        displayStatus.contains("Clocked In", ignoreCase = true) -> 0xFF10B981.toInt()
                        displayStatus.contains("Sync", ignoreCase = true) -> 0xFF38BDF8.toInt()
                        else -> 0xFF94A3B8.toInt()
                    }
                } else {
                    when {
                        isCompleted -> 0xFF15803D.toInt()
                        isWfh -> 0xFFA16207.toInt()
                        isWeekOff -> 0xFF0369A1.toInt()
                        displayStatus.contains("Break", ignoreCase = true) -> 0xFFB45309.toInt()
                        displayStatus.contains("Clocked In", ignoreCase = true) -> 0xFF15803D.toInt()
                        displayStatus.contains("Sync", ignoreCase = true) -> 0xFF0284C7.toInt()
                        else -> 0xFF64748B.toInt()
                    }
                }
                views.setTextColor(R.id.widget_status_text, statusColor)
                views.setInt(R.id.widget_status_text, "setBackgroundResource", if (isEffectiveDark) R.drawable.bg_widget_status_chip else R.drawable.bg_widget_status_chip_light)

                // Layout-specific bindings
                if (layoutId == R.layout.attendance_widget_small) {
                    val page = sharedPrefs.getInt("WidgetPage_$appWidgetId", 0)

                    // Bind header to open app
                    views.setOnClickPendingIntent(R.id.widget_title, appPendingIntent)

                    // Bind 1-Tap Pod to cycle forward
                    val cycleIntent = Intent(context, AttendanceAppWidgetProvider::class.java).apply {
                        action = ACTION_CYCLE_PAGE
                        putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    }
                    val cyclePendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 10 + 2,
                        cycleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_card_pod, cyclePendingIntent)

                    // Bind Prev button
                    val prevIntent = Intent(context, AttendanceAppWidgetProvider::class.java).apply {
                        action = ACTION_PREV_PAGE
                        putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    }
                    val prevPendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 10 + 3,
                        prevIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

                    // Bind Next button
                    val nextIntent = Intent(context, AttendanceAppWidgetProvider::class.java).apply {
                        action = ACTION_NEXT_PAGE
                        putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    }
                    val nextPendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 10 + 4,
                        nextIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

                    // Determine Slide Content based on page
                    val metricLabel: String
                    val metricValue: String
                    val metricValueColor: Int
                    val metricSubtext: String
                    val metricSubtextColor: Int

                    when (page) {
                        1 -> {
                            // Slide 1: Estimated Exit Time (When Can I Leave?)
                            metricLabel = "ESTIMATED EXIT"
                            metricValue = if (isWeekOff) "Enjoy Weekend" else if (isCompleted) "Ready to Leave" else exitTime ?: "--:--"
                            metricValueColor = if (isCompleted) (if (isEffectiveDark) 0xFF10B981.toInt() else 0xFF15803D.toInt()) else colorSkyAccent
                            val actualSubtext = if (actualExitTime != "--:--" && actualExitTime != exitTime) " • Act: $actualExitTime" else ""
                            metricSubtext = if (isWeekOff) "Week Off" else if (isWfh) "Flexible Exit" else if (isCompleted) "Shift Completed 🚀" else "$progressRemaining left$actualSubtext"
                            metricSubtextColor = metricValueColor
                        }
                        2 -> {
                            // Slide 2: Total Breaks Taken & Count
                            metricLabel = "BREAKS TAKEN"
                            metricValue = if (isWeekOff) "0m" else breakTime ?: "0h 00m"
                            metricValueColor = if (isEffectiveDark) 0xFFF59E0B.toInt() else 0xFFB45309.toInt()
                            metricSubtext = if (isWeekOff) "Enjoy Weekend" else if (isWfh) "Remote Breaks" else breakCount
                            metricSubtextColor = metricValueColor
                        }
                        3 -> {
                            // Slide 3: First In & Daily Summary
                            metricLabel = "FIRST CLOCK-IN"
                            metricValue = if (isWeekOff) "--:--" else firstIn ?: "--:--"
                            metricValueColor = colorSkyAccent
                            metricSubtext = if (isWeekOff) "Week Off" else "Target: 8.5h • $progressPercent%"
                            metricSubtextColor = colorTextSubtle
                        }
                        else -> {
                            // Slide 0: Time at Desk (Total Hours Spent)
                            metricLabel = "TIME AT DESK"
                            metricValue = if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime ?: "0h 00m"
                            metricValueColor = colorTextPrimary
                            metricSubtext = if (isWeekOff) "Rest & Recharge" else if (isWfh) "Remote Workspace" else if (isCompleted) "Goal Met • 100%" else "$displayStatus • $progressPercent%"
                            metricSubtextColor = statusColor
                        }
                    }

                    views.setInt(R.id.widget_card_pod, "setBackgroundResource", heroCardBg)
                    views.setTextViewText(R.id.widget_metric_label, metricLabel)
                    views.setTextColor(R.id.widget_metric_label, colorTextLabel)
                    views.setTextViewText(R.id.widget_work_time_value, metricValue)
                    views.setTextColor(R.id.widget_work_time_value, metricValueColor)
                    views.setTextViewText(R.id.widget_status_text, metricSubtext)
                    views.setTextColor(R.id.widget_status_text, metricSubtextColor)

                    // 4 Indicator Dots
                    views.setImageViewResource(R.id.dot_1, if (page == 0) R.drawable.widget_dot_active else R.drawable.widget_dot_inactive)
                    views.setImageViewResource(R.id.dot_2, if (page == 1) R.drawable.widget_dot_active else R.drawable.widget_dot_inactive)
                    views.setImageViewResource(R.id.dot_3, if (page == 2) R.drawable.widget_dot_active else R.drawable.widget_dot_inactive)
                    views.setImageViewResource(R.id.dot_4, if (page == 3) R.drawable.widget_dot_active else R.drawable.widget_dot_inactive)

                    views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)
                } else if (layoutId == R.layout.attendance_widget_medium) {
                    views.setInt(R.id.widget_hero_card, "setBackgroundResource", heroCardBg)
                    views.setInt(R.id.widget_cell_exit, "setBackgroundResource", statCellBg)

                    views.setTextColor(R.id.widget_work_subtitle, colorTextLabel)
                    views.setTextColor(R.id.widget_work_time_value, colorTextPrimary)
                    views.setTextColor(R.id.widget_progress_percent, colorSkyAccent)

                    views.setTextColor(R.id.widget_label_exit, colorTextLabel)
                    views.setTextViewText(R.id.widget_exit_time_value, if (isWeekOff && exitTime == "--:--") "Enjoy Weekend" else exitTime)
                    views.setTextColor(R.id.widget_exit_time_value, colorTextStat)

                    views.setTextViewText(R.id.widget_actual_exit_time_value, if (isWeekOff && actualExitTime == "--:--") "" else "• Act: $actualExitTime")
                    views.setTextColor(R.id.widget_actual_exit_time_value, colorSkyAccent)

                    views.setTextViewText(R.id.widget_last_updated, "Updated: " + getCurrentTime())
                    views.setTextColor(R.id.widget_last_updated, colorTextMuted)
                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent%")
                } else if (layoutId == R.layout.attendance_widget_countdown) {
                    views.setInt(R.id.widget_hero_card, "setBackgroundResource", heroCardBg)
                    views.setInt(R.id.widget_cell_status, "setBackgroundResource", statCellBg)

                    views.setTextColor(R.id.widget_label_countdown, colorTextLabel)
                    views.setTextViewText(R.id.widget_exit_time_value, if (isWeekOff && exitTime == "--:--") "Week Off" else exitTime)
                    views.setTextColor(R.id.widget_exit_time_value, colorTextPrimary)

                    views.setTextViewText(R.id.widget_progress_remaining, if (isWfh) "Remote Workspace" else if (isWeekOff) "Enjoy Weekend" else progressRemaining)
                    views.setTextColor(R.id.widget_progress_remaining, colorSkyAccent)

                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent%")
                    views.setTextColor(R.id.widget_progress_percent, if (isEffectiveDark) 0xFF10B981.toInt() else 0xFF15803D.toInt())

                    views.setTextViewText(R.id.widget_status_text, displayStatus)
                    views.setTextColor(R.id.widget_status_text, colorTextSubtle)
                } else if (layoutId == R.layout.attendance_widget_timesheet) {
                    views.setInt(R.id.widget_hero_card, "setBackgroundResource", heroCardBg)
                    views.setTextColor(R.id.widget_label_timesheet, colorTextLabel)
                    views.setTextViewText(R.id.widget_work_time_value, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                    views.setTextColor(R.id.widget_work_time_value, colorTextPrimary)
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
                    views.setTextColor(R.id.widget_label_wellbeing, colorTextLabel)
                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent%")
                    views.setTextColor(R.id.widget_progress_percent, if (isEffectiveDark) 0xFF10B981.toInt() else 0xFF15803D.toInt())
                    views.setTextViewText(R.id.widget_work_time_value, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                    views.setTextColor(R.id.widget_work_time_value, colorTextPrimary)
                    views.setTextViewText(R.id.widget_status_text, if (isCompleted) "Shift Complete" else if (isWeekOff) "Week Off" else progressRemaining)
                    views.setTextColor(R.id.widget_status_text, colorSkyAccent)
                } else if (layoutId == R.layout.attendance_widget_lockscreen) {
                    views.setTextViewText(R.id.txt_widget_work_time, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                    views.setTextViewText(R.id.txt_widget_exit_time, if (isWeekOff) "Enjoy Weekend" else "Exit: $exitTime")
                    views.setTextViewText(R.id.txt_widget_status, if (isCompleted) "GOAL MET" else if (isWfh) "WFH" else if (isWeekOff) "WEEK OFF" else "IN-OFFICE")
                } else if (layoutId == R.layout.attendance_widget) {
                    // Large 4x2 Dashboard
                    views.setInt(R.id.widget_hero_card, "setBackgroundResource", heroCardBg)
                    views.setInt(R.id.widget_cell_first_in, "setBackgroundResource", statCellBg)
                    views.setInt(R.id.widget_cell_break, "setBackgroundResource", statCellBg)
                    views.setInt(R.id.widget_cell_exit, "setBackgroundResource", statCellBg)
                    views.setInt(R.id.widget_cell_actual, "setBackgroundResource", statCellBg)

                    val subtitle = when {
                        isWfh -> "REMOTE WORKDAY"
                        isWeekOff -> "WEEK OFF"
                        else -> "TIME AT DESK"
                    }
                    views.setTextViewText(R.id.widget_work_subtitle, subtitle)
                    views.setTextColor(R.id.widget_work_subtitle, colorTextLabel)

                    val formattedStatus = when {
                        isCompleted -> "Shift Complete • 100%"
                        isWfh -> "Remote Workspace"
                        isWeekOff -> "Week Off • Recharge"
                        else -> "$displayStatus • $progressPercent%"
                    }
                    views.setTextViewText(R.id.widget_status_text, formattedStatus)

                    views.setTextColor(R.id.widget_work_time_value, colorTextPrimary)

                    views.setTextViewText(R.id.widget_exit_time_value, if (isWeekOff && exitTime == "--:--") "Enjoy Weekend" else exitTime)
                    views.setTextColor(R.id.widget_exit_time_value, colorTextStat)

                    views.setTextViewText(R.id.widget_actual_exit_time_value, if (isWeekOff && actualExitTime == "--:--") "--:--" else actualExitTime)
                    views.setTextColor(R.id.widget_actual_exit_time_value, colorSkyAccent)

                    views.setTextViewText(R.id.widget_first_in_value, firstIn)
                    views.setTextColor(R.id.widget_first_in_value, colorTextStat)

                    views.setTextViewText(R.id.widget_break_time_value, breakTime)
                    views.setTextColor(R.id.widget_break_time_value, colorTextStat)

                    views.setTextColor(R.id.widget_label_first_in, colorTextLabel)
                    views.setTextColor(R.id.widget_label_break, colorTextLabel)
                    views.setTextColor(R.id.widget_label_exit, colorTextLabel)
                    views.setTextColor(R.id.widget_label_actual, colorTextLabel)

                    views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)

                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent% Goal")
                    views.setTextColor(R.id.widget_progress_percent, colorSkyAccent)

                    views.setTextViewText(R.id.widget_progress_remaining, if (isWfh) "Biometrics Paused" else if (isWeekOff) "Rest & Recharge" else progressRemaining)
                    views.setTextColor(R.id.widget_progress_remaining, colorTextSubtle)
                } else if (layoutId == R.layout.attendance_widget_oneui_capsule) {
                    views.setTextViewText(R.id.widget_work_time_value, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent%")
                    views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)
                    views.setTextViewText(R.id.widget_exit_time_value, if (isWeekOff && exitTime == "--:--") "Weekend" else exitTime)
                    views.setTextViewText(R.id.widget_progress_remaining, if (isWfh) "Remote" else if (isWeekOff) "Enjoy" else progressRemaining)
                    val capsuleStatus = when {
                        isWfh -> "WFH ACTIVE"
                        isCompleted -> "GOAL MET"
                        isWeekOff -> "WEEK OFF"
                        else -> displayStatus.uppercase()
                    }
                    views.setTextViewText(R.id.widget_status_text, capsuleStatus)
                    views.setTextColor(R.id.widget_status_text, statusColor)
                    views.setInt(R.id.widget_status_text, "setBackgroundResource", 0)
                    views.setInt(R.id.img_oneui_status_dot, "setColorFilter", statusColor)
                } else if (layoutId == R.layout.attendance_widget_oneui_dualpod) {
                    val dualPodStatus = when {
                        isWfh -> "WFH"
                        isWeekOff -> "WEEK OFF"
                        isCompleted -> "COMPLETE"
                        else -> displayStatus.uppercase()
                    }
                    views.setTextViewText(R.id.widget_status_text, dualPodStatus)
                    views.setInt(R.id.widget_status_text, "setBackgroundResource", if (isWfh) R.drawable.widget_oneui_status_pill_wfh else R.drawable.widget_oneui_status_pill)
                    views.setTextColor(R.id.widget_status_text, if (isWfh) 0xFFF59E0B.toInt() else 0xFF10B981.toInt())
                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent%")
                    views.setTextViewText(R.id.widget_work_time_value, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                    views.setTextViewText(R.id.widget_work_subtitle, if (isCompleted) "Goal Met 🚀" else if (isWfh) "Remote Shift" else "Target: 8.5h")
                    views.setTextViewText(R.id.widget_exit_time_value, if (isWeekOff) "Enjoy Weekend" else "Exit: $exitTime")
                    views.setTextViewText(R.id.widget_progress_remaining, if (isWfh) "Biometrics Paused" else if (isWeekOff) "Rest & Recharge" else progressRemaining)
                } else if (layoutId == R.layout.attendance_widget_oneui_matrix) {
                    views.setTextViewText(R.id.widget_work_subtitle, if (isWfh) "REMOTE WORKDAY" else if (isWeekOff) "WEEK OFF" else "TIME AT DESK")
                    val matrixStatus = when {
                        isWfh -> "WFH ACTIVE"
                        isWeekOff -> "WEEK OFF"
                        isCompleted -> "GOAL MET 🚀"
                        else -> displayStatus.uppercase()
                    }
                    views.setTextViewText(R.id.widget_status_text, matrixStatus)
                    views.setInt(R.id.widget_status_text, "setBackgroundResource", if (isWfh) R.drawable.widget_oneui_status_pill_wfh else R.drawable.widget_oneui_status_pill)
                    views.setTextColor(R.id.widget_status_text, if (isWfh) 0xFFF59E0B.toInt() else 0xFF10B981.toInt())
                    views.setTextViewText(R.id.widget_work_time_value, if (isWeekOff && workTime == "0h 00m") "Week Off" else workTime)
                    views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)
                    views.setTextViewText(R.id.widget_progress_percent, "$progressPercent% Goal")
                    views.setTextViewText(R.id.widget_progress_remaining, if (isWfh) "Remote Workspace" else if (isWeekOff) "Rest & Recharge" else progressRemaining)
                    views.setTextViewText(R.id.widget_first_in_value, firstIn)
                    views.setTextViewText(R.id.widget_break_time_value, breakTime)
                    views.setTextViewText(R.id.widget_exit_time_value, if (isWeekOff && exitTime == "--:--") "Weekend" else exitTime)
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
                defaultLayoutId == R.layout.attendance_widget_dial ||
                defaultLayoutId == R.layout.attendance_widget_lockscreen ||
                defaultLayoutId == R.layout.attendance_widget_oneui_capsule ||
                defaultLayoutId == R.layout.attendance_widget_oneui_dualpod ||
                defaultLayoutId == R.layout.attendance_widget_oneui_matrix) {
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
            lastUpdated: String,
            breakCount: String = "0 Breaks Today",
            actualExitTime: String = "--:--",
            baseWorkMinutes: Double = -1.0,
            lastInTimestamp: Long = 0L,
            isClockedIn: Boolean = false,
            targetMinutes: Double = 510.0,
            baseBreakMinutes: Double = 0.0,
            avgBreakMinutes: Int = 60
        ) {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val editor = sharedPrefs.edit()
                .putString("WidgetWorkTime", workTime)
                .putString("WidgetFirstIn", firstIn)
                .putString("WidgetBreakTime", breakTime)
                .putString("WidgetBreakCount", breakCount)
                .putString("WidgetExitTime", exitTime)
                .putString("WidgetActualExitTime", actualExitTime)
                .putString("WidgetStatusText", statusText)
                .putInt("WidgetProgressPercent", progressPercent)
                .putString("WidgetProgressRemaining", progressRemaining)
                .putString("WidgetLastUpdated", lastUpdated)
                .putString("WidgetCacheDate", todayDateStr)

            if (baseWorkMinutes >= 0) {
                editor.putFloat("WidgetBaseWorkMinutes", baseWorkMinutes.toFloat())
                editor.putLong("WidgetLastInTimestamp", lastInTimestamp)
                editor.putBoolean("WidgetIsClockedIn", isClockedIn)
                editor.putFloat("WidgetTargetMinutes", targetMinutes.toFloat())
                editor.putFloat("WidgetBaseBreakMinutes", baseBreakMinutes.toFloat())
                editor.putInt("WidgetAvgBreakMinutes", avgBreakMinutes)
            }
            editor.apply()

            LockScreenLiveCardManager.updateLiveCard(context)
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

            // Lock Screen Compact Widget
            val lockscreenComponent = ComponentName(context, WidgetLockScreenProvider::class.java)
            val lockscreenIds = appWidgetManager.getAppWidgetIds(lockscreenComponent)
            for (id in lockscreenIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget_lockscreen)
            }

            // One UI 9 Live Island Capsule
            val oneUiCapsuleComponent = ComponentName(context, WidgetOneUiCapsuleProvider::class.java)
            val oneUiCapsuleIds = appWidgetManager.getAppWidgetIds(oneUiCapsuleComponent)
            for (id in oneUiCapsuleIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget_oneui_capsule)
            }

            // One UI 9 Dual-Pod Radial Glass Card
            val oneUiDualPodComponent = ComponentName(context, WidgetOneUiDualPodProvider::class.java)
            val oneUiDualPodIds = appWidgetManager.getAppWidgetIds(oneUiDualPodComponent)
            for (id in oneUiDualPodIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget_oneui_dualpod)
            }

            // One UI 9 Glass Matrix Dashboard
            val oneUiMatrixComponent = ComponentName(context, WidgetOneUiMatrixProvider::class.java)
            val oneUiMatrixIds = appWidgetManager.getAppWidgetIds(oneUiMatrixComponent)
            for (id in oneUiMatrixIds) {
                updateAppWidget(context, appWidgetManager, id, R.layout.attendance_widget_oneui_matrix)
            }

            // Sync Universal Lock Screen Live Activity Card
            LockScreenLiveCardManager.updateLiveCard(context)
        }

        fun getCurrentTime(): String {
            return SimpleDateFormat("hh:mm a", Locale.US).format(Date())
        }

        fun formatMinutes(totalMin: Double): String {
            val h = (totalMin / 60).toInt()
            val m = (totalMin % 60).toInt()
            return "${h}h ${String.format("%02d", m)}m"
        }

        fun calculateMetrics(jsonStr: String, targetHours: Float, avgBreakMinutes: Int = 60): WidgetMetrics? {
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
                var breakCount = 0
                var inBreak = false
                for (log in logs) {
                    if (log.isIn == 0) {
                        if (inBreak) {
                            breakCount++
                            inBreak = false
                        }
                        lastInTime = log.time
                    } else if (log.isIn == 1 && lastInTime != null) {
                        workDurationMs += (log.time.time - lastInTime.time)
                        lastOutTime = log.time
                        lastInTime = null
                        inBreak = true
                    }
                }
                if (inBreak) {
                    breakCount++
                }

                val isClockedIn = lastInTime != null
                val baseWorkMinutes = workDurationMs / 60000.0
                val lastInTimestamp = lastInTime?.time ?: 0L

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
                val breakCountStr = when (breakCount) {
                    0 -> "No Breaks Today"
                    1 -> "1 Break Today"
                    else -> "$breakCount Breaks Today"
                }
                
                var exitTimeStr = "Completed"
                var actualExitTimeStr = "Completed"
                if (!completed) {
                    // Actual Exit: direct remaining work time without extra breaks
                    val actualExitDate = Date(Date().time + (remainingMinutes * 60000).toLong())
                    actualExitTimeStr = SimpleDateFormat("hh:mm a", Locale.US).format(actualExitDate)

                    // Estimated Exit: includes buffer for remaining portion of average break
                    val remainingBreakMinutes = Math.max(0.0, avgBreakMinutes - totalBreakMinutes)
                    val estExitDate = Date(Date().time + ((remainingMinutes + remainingBreakMinutes) * 60000).toLong())
                    exitTimeStr = SimpleDateFormat("hh:mm a", Locale.US).format(estExitDate)
                }

                val progressPercent = Math.min(100, ((totalWorkMinutes / targetMinutes) * 100).toInt())
                val progressRemaining = if (completed) "0m left" else "${formatMinutes(remainingMinutes)} left"

                val statusText = when {
                    completed -> "Shift Complete"
                    isClockedIn -> "Clocked In"
                    progressPercent > 0 -> "On Break"
                    else -> "Not Clocked In"
                }

                return WidgetMetrics(
                    workTimeStr,
                    firstInStr,
                    breakTimeStr,
                    breakCountStr,
                    exitTimeStr,
                    actualExitTimeStr,
                    statusText,
                    progressPercent,
                    progressRemaining,
                    baseWorkMinutes = baseWorkMinutes,
                    lastInTimestamp = lastInTimestamp,
                    isClockedIn = isClockedIn,
                    targetMinutes = targetMinutes,
                    baseBreakMinutes = totalBreakMinutes,
                    avgBreakMinutes = avgBreakMinutes
                )
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
            val actualExitTime = sharedPrefs.getString("WidgetActualExitTime", "--:--") ?: "--:--"
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
                lastUpdated,
                actualExitTime = actualExitTime
            )
        }

        fun checkEmployeeWfhStatus(context: Context, accessToken: String, userId: String): Boolean? {
            try {
                val statusUrl = "https://apps.pal.tech/hrms-backend/api/Employee/GetEmployeeStatus?employeeIds=$userId"
                val connection = TokenManager.openAuthenticatedConnection(context, statusUrl, accessToken)
                connection.requestMethod = "GET"
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

        fun fetchRequiredWorkingHours(context: Context, accessToken: String, userId: String): Float? {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedTarget = sharedPrefs.getFloat("TargetHours", -1f)
            val lastFetch = sharedPrefs.getLong("LastPolicyFetchTimestamp", 0L)
            val oneDayMs = 24 * 60 * 60 * 1000L
            if (cachedTarget > 0 && (System.currentTimeMillis() - lastFetch) < oneDayMs) {
                return cachedTarget
            }

            try {
                val policyUrl = "https://apps.pal.tech/hrms-backend/api/OrganizationPolicy/GetMyPolicies?schemeId=3&policyId=0&employeeId=$userId"
                val connection = TokenManager.openAuthenticatedConnection(context, policyUrl, accessToken)
                connection.requestMethod = "GET"
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
                    val effHoursObj = dataObj?.optJSONObject("effectiveHours")
                    if (effHoursObj != null && effHoursObj.has("requiredWorkingRate")) {
                        val rate = effHoursObj.optDouble("requiredWorkingRate", 100.0)
                        // 100% requiredWorkingRate corresponds to 8h 30m (8.5h)
                        val targetHours = ((rate / 100.0) * 8.5).toFloat()
                        sharedPrefs.edit()
                            .putFloat("TargetHours", targetHours)
                            .putInt("PolicyWorkingRate", rate.toInt())
                            .putLong("LastPolicyFetchTimestamp", System.currentTimeMillis())
                            .apply()
                        return targetHours
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HRMSWidget", "fetchRequiredWorkingHours failed: ${e.message}")
            }
            return if (cachedTarget > 0) cachedTarget else null
        }

        fun fetchAverageBreakMinutes(context: Context, accessToken: String, userId: String): Int {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedAvgBreak = sharedPrefs.getInt("AvgBreakMinutes", -1)
            val lastFetch = sharedPrefs.getLong("LastAvgBreakFetchTimestamp", 0L)
            val oneDayMs = 24 * 60 * 60 * 1000L
            if (cachedAvgBreak > 0 && (System.currentTimeMillis() - lastFetch) < oneDayMs) {
                return cachedAvgBreak
            }

            try {
                val cal = Calendar.getInstance()
                val sdfIso = SimpleDateFormat("yyyy-MM-dd'T'00:00:00.000'Z'", Locale.US)
                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val todayStr = sdfDate.format(cal.time)
                val endIso = sdfIso.format(cal.time)

                cal.add(Calendar.DAY_OF_YEAR, -30)
                val startIso = sdfIso.format(cal.time)

                val summaryUrl = "https://apps.pal.tech/hrms-backend/api/Attendance/GetDailySummaryLog?startDate=$startIso&endDate=$endIso&userId=$userId"
                val connection = TokenManager.openAuthenticatedConnection(context, summaryUrl, accessToken)
                connection.requestMethod = "GET"
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
                    val dataArray = json.optJSONArray("data")
                    if (dataArray != null && dataArray.length() > 0) {
                        var totalBreakSeconds = 0L
                        var validDaysCount = 0

                        for (i in 0 until dataArray.length()) {
                            val dayObj = dataArray.getJSONObject(i)
                            val dateStr = dayObj.optString("date", "")
                            val grossHours = dayObj.optLong("grossHours", 0L)
                            val effectiveHours = dayObj.optLong("effectiveHours", 0L)

                            // Only consider completed work days with recorded time, excluding today
                            if (grossHours > 0 && effectiveHours > 0 && !dateStr.startsWith(todayStr)) {
                                val breakSeconds = grossHours - effectiveHours
                                if (breakSeconds >= 0) {
                                    totalBreakSeconds += breakSeconds
                                    validDaysCount++
                                }
                            }
                        }

                        if (validDaysCount > 0) {
                            val avgSeconds = totalBreakSeconds / validDaysCount
                            val avgMinutes = Math.round(avgSeconds / 60.0).toInt()
                            val result = if (avgMinutes > 0) avgMinutes else 60
                            sharedPrefs.edit()
                                .putInt("AvgBreakMinutes", result)
                                .putLong("LastAvgBreakFetchTimestamp", System.currentTimeMillis())
                                .apply()
                            return result
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HRMSWidget", "fetchAverageBreakMinutes failed: ${e.message}")
            }
            return if (cachedAvgBreak > 0) cachedAvgBreak else 60
        }

        fun isWeekend(): Boolean {
            val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            return day == Calendar.SATURDAY || day == Calendar.SUNDAY
        }

        fun getWidgetBackground(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val theme = prefs.getString("WidgetTheme", "SYSTEM") ?: "SYSTEM"
            val opacity = prefs.getInt("WidgetOpacity", 100)

            val isSystemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isEffectiveDark = when (theme) {
                "LIGHT" -> false
                "DARK", "AMOLED" -> true
                "TRANSPARENT" -> isSystemNight
                else -> isSystemNight // "SYSTEM"
            }

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
                "DARK" -> when {
                    opacity >= 90 -> R.drawable.widget_bg_dark_100
                    opacity >= 65 -> R.drawable.widget_bg_dark_75
                    opacity >= 40 -> R.drawable.widget_bg_dark_50
                    opacity >= 15 -> R.drawable.widget_bg_dark_25
                    else -> R.drawable.widget_bg_transparent
                }
                else -> { // SYSTEM
                    if (isEffectiveDark) {
                        when {
                            opacity >= 90 -> R.drawable.widget_bg_dark_100
                            opacity >= 65 -> R.drawable.widget_bg_dark_75
                            opacity >= 40 -> R.drawable.widget_bg_dark_50
                            opacity >= 15 -> R.drawable.widget_bg_dark_25
                            else -> R.drawable.widget_bg_transparent
                        }
                    } else {
                        when {
                            opacity >= 90 -> R.drawable.widget_bg_light_100
                            opacity >= 65 -> R.drawable.widget_bg_light_75
                            opacity >= 40 -> R.drawable.widget_bg_light_50
                            opacity >= 15 -> R.drawable.widget_bg_light_25
                            else -> R.drawable.widget_bg_transparent
                        }
                    }
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
        if (intent.action == ACTION_CYCLE_PAGE || intent.action == ACTION_NEXT_PAGE || intent.action == ACTION_PREV_PAGE) {
            val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val pageKey = "WidgetPage_$appWidgetId"
                val currentPage = sharedPrefs.getInt(pageKey, 0)
                val newPage = when (intent.action) {
                    ACTION_PREV_PAGE -> (currentPage + 3) % 4
                    else -> (currentPage + 1) % 4
                }
                sharedPrefs.edit().putInt(pageKey, newPage).apply()
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, appWidgetManager, appWidgetId, defaultLayoutId)
            }
            return
        }

        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            
            // Set loading state on all sizes of widgets
            val providers = listOf(
                AttendanceAppWidgetProvider::class.java to R.layout.attendance_widget,
                AttendanceAppWidgetProviderMedium::class.java to R.layout.attendance_widget_medium,
                AttendanceAppWidgetProviderSmall::class.java to R.layout.attendance_widget_small,
                WidgetCountdownProvider::class.java to R.layout.attendance_widget_countdown,
                WidgetTimesheetProvider::class.java to R.layout.attendance_widget_timesheet,
                WidgetDialProvider::class.java to R.layout.attendance_widget_dial,
                WidgetLockScreenProvider::class.java to R.layout.attendance_widget_lockscreen,
                WidgetOneUiCapsuleProvider::class.java to R.layout.attendance_widget_oneui_capsule,
                WidgetOneUiDualPodProvider::class.java to R.layout.attendance_widget_oneui_dualpod,
                WidgetOneUiMatrixProvider::class.java to R.layout.attendance_widget_oneui_matrix
            )

            for ((providerClass, defaultLayout) in providers) {
                val componentName = ComponentName(context, providerClass.name)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                for (appWidgetId in ids) {
                    val layoutId = getLayoutForWidgetSize(appWidgetManager, appWidgetId, defaultLayout)
                    val views = RemoteViews(context.packageName, layoutId)
                    views.setTextViewText(R.id.widget_status_text, "Syncing...")
                    views.setTextViewText(R.id.txt_widget_status, "SYNCING...")
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
        val cookies = TokenManager.getValidCookies(context)
        var userId = sharedPrefs.getString("UserId", null)
        val targetHours = sharedPrefs.getFloat("TargetHours", 8.5f)
        val isWfh = sharedPrefs.getBoolean("WfhMode", false)

        if (userId.isNullOrEmpty()) {
            val identity = TokenManager.ensureUserIdentity(context)
            if (identity != null) {
                userId = identity.first
            }
        }
        
        if ((accessToken == null && cookies == null) || userId == null) {
            val isWeekendToday = isWeekend()
            val defaultStatus = when {
                isWfh -> "Working from Home"
                isWeekendToday -> "Week Off"
                else -> "Please Login in App"
            }
            val defaultWork = if (isWeekendToday) "Week Off" else "0h 00m"
            val defaultExit = if (isWeekendToday) "Enjoy Weekend" else "--:--"
            val defaultRem = if (isWeekendToday) "Rest & Recharge" else "--h --m left"
            saveWidgetCache(context, defaultWork, "--:--", "0h 00m", defaultExit, defaultStatus, 0, defaultRem, "Last updated: " + getCurrentTime(), actualExitTime = defaultExit)
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
            var connection = TokenManager.openAuthenticatedConnection(context, apiUrl, accessToken)
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()

            if (connection.responseCode == 401) {
                // Access token expired; silently rotate token and retry once
                val refreshResult = TokenManager.refreshTokens(context)
                if (refreshResult is TokenManager.RefreshResult.Success) {
                    accessToken = refreshResult.accessToken
                    connection.disconnect()
                    connection = TokenManager.openAuthenticatedConnection(context, apiUrl, accessToken)
                    connection.requestMethod = "GET"
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

                // Fetch dynamic policy target hours & personalized 30-day average break
                val policyHours = fetchRequiredWorkingHours(context, accessToken, userId)
                val effectiveTargetHours = policyHours ?: sharedPrefs.getFloat("TargetHours", 8.5f)
                val avgBreak = fetchAverageBreakMinutes(context, accessToken, userId)

                val metrics = calculateMetrics(response.toString(), effectiveTargetHours, avgBreak)
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
                        "Last updated: " + getCurrentTime(),
                        metrics.breakCount,
                        metrics.actualExitTime,
                        baseWorkMinutes = metrics.baseWorkMinutes,
                        lastInTimestamp = metrics.lastInTimestamp,
                        isClockedIn = metrics.isClockedIn,
                        targetMinutes = metrics.targetMinutes,
                        baseBreakMinutes = metrics.baseBreakMinutes,
                        avgBreakMinutes = metrics.avgBreakMinutes
                    )
                    // Check and trigger Completed notification if complete
                    checkAndSendNotification(context, response.toString(), effectiveTargetHours)
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
                    saveWidgetCache(
                        context,
                        defaultWorkTime,
                        "--:--",
                        "0h 00m",
                        defaultExitTime,
                        noLogsStatus,
                        0,
                        defaultRemaining,
                        "Last updated: " + getCurrentTime(),
                        actualExitTime = defaultExitTime
                    )
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
            val baseWorkMinutes = workDurationMs / 60000.0
            if (isClockedIn) {
                workDurationMs += (Date().time - lastInTime!!.time)
            }

            val totalWorkMinutes = workDurationMs / 60000.0
            val targetMinutes = targetHours * 60.0
            val completed = totalWorkMinutes >= targetMinutes

            if (completed) {
                if (isClockedIn) {
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
                    // Start repeating leave office alerts if active & not dismissed
                    ShiftAlertManager.checkAndTriggerNudge(context, formatMinutes(totalWorkMinutes))
                } else {
                    // Employee already punched out at the turnstile/portal; permanently silence alerts
                    ShiftAlertManager.stopAlerts(context)
                }
            } else {
                if (isClockedIn && lastInTime != null) {
                    // Schedule exact alarm for when shift goal will be reached while at desk
                    val remainingMinutes = Math.max(0.0, targetMinutes - baseWorkMinutes)
                    val targetExitTimestamp = lastInTime.time + (remainingMinutes * 60000).toLong()
                    ShiftAlertManager.scheduleShiftTargetAlarm(context, targetExitTimestamp)
                } else {
                    ShiftAlertManager.stopAlerts(context)
                }
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

class WidgetLockScreenProvider : AttendanceAppWidgetProvider() {
    override val defaultLayoutId: Int = R.layout.attendance_widget_lockscreen
}

class WidgetOneUiCapsuleProvider : AttendanceAppWidgetProvider() {
    override val defaultLayoutId: Int = R.layout.attendance_widget_oneui_capsule
}

class WidgetOneUiDualPodProvider : AttendanceAppWidgetProvider() {
    override val defaultLayoutId: Int = R.layout.attendance_widget_oneui_dualpod
}

class WidgetOneUiMatrixProvider : AttendanceAppWidgetProvider() {
    override val defaultLayoutId: Int = R.layout.attendance_widget_oneui_matrix
}

