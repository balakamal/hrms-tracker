package com.balakamal.hrmstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Ensures widget sync, background monitoring, and timesheet notifications
 * automatically restart and persist across device reboots and app updates.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i("HRMS_BootReceiver", "Received boot/update broadcast action: $action")

        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        if (action in validActions) {
            val sharedPrefs = context.getSharedPreferences(TokenManager.PREFS_NAME, Context.MODE_PRIVATE)
            val accessToken = sharedPrefs.getString(TokenManager.KEY_ACCESS_TOKEN, null)
            val refreshToken = sharedPrefs.getString(TokenManager.KEY_REFRESH_TOKEN, null)

            // 1. Immediately trigger local widget update to restore live numbers
            AttendanceAppWidgetProvider.triggerWidgetUpdate(context)

            // 2. Schedule timesheet reminder worker
            TimesheetReminderWorker.schedule(context)

            // 3. If logged in, re-schedule background attendance worker & trigger refresh
            if (!accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()) {
                AttendanceWorker.schedule(context)

                // Refresh widget in the background
                val refreshIntent = Intent(context, AttendanceAppWidgetProvider::class.java).apply {
                    this.action = AttendanceAppWidgetProvider.ACTION_REFRESH
                }
                context.sendBroadcast(refreshIntent)
            }
        }
    }
}
