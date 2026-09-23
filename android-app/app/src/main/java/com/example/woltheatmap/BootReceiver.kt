package com.example.woltheatmap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.woltheatmap.detection.StopDetectionService
import com.example.woltheatmap.work.UploadWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(StopDetectionService.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(StopDetectionService.PREF_VEHICLE_CLASS, null) == null) return
        UploadWorker.schedulePeriodicUpload(context)
        StopDetectionService.start(context)
    }
}
