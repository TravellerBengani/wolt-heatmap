package com.example.woltheatmap.detection

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

object WoltSessionTracker {
    const val WOLT_PACKAGE = "com.wolt.courierapp"
    private const val SESSION_WINDOW_MS = 30 * 60 * 1000L

    /**
     * Returns true if the Wolt courier app was brought to the foreground at
     * any point in the last 30 minutes. Requires Usage Access permission — call
     * [hasUsageStatsPermission] first and deep-link to settings if needed.
     */
    fun isSessionActive(context: Context): Boolean {
        if (!hasUsageStatsPermission(context)) return false

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - SESSION_WINDOW_MS, now) ?: return false

        // ACTIVITY_RESUMED (API 29+) and MOVE_TO_FOREGROUND (API <29) share the same
        // integer value (1). Checking ACTIVITY_RESUMED covers both.
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == WOLT_PACKAGE &&
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                return true
            }
        }
        return false
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
