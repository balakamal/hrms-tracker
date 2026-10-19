package com.balakamal.hrmstracker

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val GROUP_LIVE_TRACKING = "hrms_group_live_tracking"
    const val GROUP_ALERTS = "hrms_group_alerts"

    const val CHANNEL_LIVE_CARD = "hrms_lockscreen_live_channel"
    const val CHANNEL_LEAVE_OFFICE = "hrms_shift_nudge_channel"
    const val CHANNEL_SHIFT_ALERTS = "hrms_tracker_channel"
    const val CHANNEL_TIMESHEET = "hrms_timesheet_reminder"

    /**
     * Registers all notification channel groups and channels upfront.
     * This ensures all categories are immediately visible and configurable
     * in native Android settings (Settings -> Apps -> HRMS Tracker -> Notifications -> Notification categories).
     */
    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // 1. Channel Groups
        val groupLive = NotificationChannelGroup(GROUP_LIVE_TRACKING, "Live Shift Tracking")
        val groupAlerts = NotificationChannelGroup(GROUP_ALERTS, "Alerts & Reminders")
        manager.createNotificationChannelGroups(listOf(groupLive, groupAlerts))

        // 2. Lock Screen & Notification Shade Live Activity (Silent, Low Importance)
        val liveChannel = NotificationChannel(
            CHANNEL_LIVE_CARD,
            "Lock Screen & Live Status Activity",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_LIVE_TRACKING
            description = "Real-time shift timer, progress bar, and quick actions on Lock Screen, Always-On Display, and Notification Shade"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        // 3. Leave Office Nudges (High Importance, periodic alerts)
        val leaveChannel = NotificationChannel(
            CHANNEL_LEAVE_OFFICE,
            "Leave Office Nudges",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_ALERTS
            description = "Periodic nudges when your target shift hours are reached to remind you to punch out and leave office"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 150, 350)
            setShowBadge(true)
        }

        // 4. Shift Milestone Alerts (High Importance, 15m pre-exit & completed)
        val shiftAlertsChannel = NotificationChannel(
            CHANNEL_SHIFT_ALERTS,
            "Shift Milestones & Target Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_ALERTS
            description = "Notifications for 15-minute pre-exit warning and target shift completion"
            enableVibration(true)
            setShowBadge(true)
        }

        // 5. Daily Timesheet Reminders (Default Importance)
        val timesheetChannel = NotificationChannel(
            CHANNEL_TIMESHEET,
            "Daily Timesheet Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            group = GROUP_ALERTS
            description = "Evening prompt to review and submit your daily project timesheet"
            enableVibration(true)
            setShowBadge(true)
        }

        manager.createNotificationChannels(listOf(liveChannel, leaveChannel, shiftAlertsChannel, timesheetChannel))
    }

    /**
     * Direct navigation to the app's native Android Notification settings page
     * where all categories/channels can be inspected and configured.
     */
    fun openAppNotificationSettings(context: Context) {
        val intent = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }
            else -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to application details if direct notification settings intent fails
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (err: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Direct navigation to a specific notification channel's native settings page.
     */
    fun openChannelSettings(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to app settings
            }
        }
        openAppNotificationSettings(context)
    }

    /**
     * Checks if a notification channel is enabled by the user in Android system settings.
     */
    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        val managerCompat = NotificationManagerCompat.from(context)
        if (!managerCompat.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return true
            val channel = manager.getNotificationChannel(channelId)
            return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }
}
