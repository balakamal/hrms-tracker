package com.balakamal.hrmstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ShiftAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            ShiftAlertManager.ACTION_ALARM_TICK -> {
                if (ShiftAlertManager.isNudgeActive(context)) {
                    ShiftAlertManager.sendNudgeNotification(context)
                    ShiftAlertManager.scheduleNextAlarm(context)
                } else {
                    ShiftAlertManager.stopAlerts(context)
                }
            }
            ShiftAlertManager.ACTION_DISMISS_NUDGE -> {
                ShiftAlertManager.stopAlerts(context)
            }
        }
    }
}
