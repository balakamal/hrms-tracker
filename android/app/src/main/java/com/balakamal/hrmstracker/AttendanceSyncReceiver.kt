package com.balakamal.hrmstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

/**
 * AttendanceSyncReceiver handles exact wake-ups from AlarmManager and device unlock events
 * to deliver immediate biometric data updates to widgets without opening the app.
 */
class AttendanceSyncReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AttendanceSyncReceiver"
        private const val PREFS_NAME = "HRMS_PREFS"
        const val KEY_LAST_SYNC_TIMESTAMP = "WidgetLastSyncTimestamp"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "onReceive action: $action")

        val appContext = context.applicationContext

        when (action) {
            AttendanceSyncManager.ACTION_ATTENDANCE_SYNC_ALARM -> {
                // Exact Alarm fired by AlarmManager watchdog
                AttendanceWorker.syncNow(appContext)
                AttendanceSyncManager.scheduleNextSync(appContext)
            }

            Intent.ACTION_USER_PRESENT -> {
                // User turned on and unlocked phone; update local widgets instantly
                AttendanceAppWidgetProvider.triggerWidgetUpdate(appContext)

                // If last network sync was more than 2 minutes ago, refresh from API in background
                val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
                val elapsedSinceLastSync = System.currentTimeMillis() - lastSyncTime

                if (elapsedSinceLastSync > 2 * 60 * 1000L) {
                    Log.d(TAG, "Screen unlocked and sync is ${elapsedSinceLastSync / 1000}s old. Fetching fresh biometrics...")
                    AttendanceWorker.syncNow(appContext)
                }
            }
        }
    }
}
