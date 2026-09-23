package com.example.woltheatmap.work

import android.content.Context
import androidx.work.*
import com.example.woltheatmap.HeatmapApplication
import com.example.woltheatmap.data.remote.StopEventDto
import java.util.concurrent.TimeUnit

class UploadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HeatmapApplication
        val dao = app.database.stopDao()
        val api = app.api

        val pending = dao.getAll()
        if (pending.isEmpty()) return Result.success()

        val dtos = pending.map {
            StopEventDto(
                city = it.city,
                geohash = it.geohash,
                day_of_week = it.dayOfWeek,
                hour_bucket = it.hourBucket,
                vehicle_class = it.vehicleClass,
            )
        }

        return try {
            api.uploadStops(dtos)
            dao.deleteByIds(pending.map { it.id })
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "upload_stops_periodic"

        fun schedulePeriodicUpload(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun runImmediately(context: Context) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
