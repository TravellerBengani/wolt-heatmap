package com.example.woltheatmap.detection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.location.Geocoder
import com.example.woltheatmap.HeatmapApplication
import com.example.woltheatmap.data.local.StopEntity
import com.example.woltheatmap.util.GeoUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Calendar
import java.util.Locale

class StopDetectionService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Rolling buffer of recent fixes; oldest entries are trimmed by BUFFER_WINDOW_MS.
    private val locationBuffer = ArrayDeque<Location>()

    // Confirmed stationary stop currently in progress.
    private var isInStop = false
    private var stopCenterLat = 0.0
    private var stopCenterLng = 0.0

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        if (::locationCallback.isInitialized) return  // guard against duplicate onStartCommand calls
        // 1-minute heartbeat at balanced (cell/wifi) accuracy — battery-friendly and
        // sufficient to confirm a 3-minute stationary window with a few readings.
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_INTERVAL_MS,
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onNewLocation(it) }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            // Location permission revoked after service started; give up cleanly.
            stopSelf()
        }
    }

    private fun onNewLocation(location: Location) {
        val now = System.currentTimeMillis()
        while (locationBuffer.isNotEmpty() && now - locationBuffer.first().time > BUFFER_WINDOW_MS) {
            locationBuffer.removeFirst()
        }
        locationBuffer.addLast(location)

        if (!isInStop) checkForStopStart() else checkForStopEnd(location)
    }

    private fun checkForStopStart() {
        if (locationBuffer.size < 2) return

        val spanMs = locationBuffer.last().time - locationBuffer.first().time
        if (spanMs < STOP_MIN_DURATION_MS) return

        val centroidLat = locationBuffer.sumOf { it.latitude } / locationBuffer.size
        val centroidLng = locationBuffer.sumOf { it.longitude } / locationBuffer.size
        val dist = FloatArray(1)

        val allStationary = locationBuffer.all { loc ->
            Location.distanceBetween(loc.latitude, loc.longitude, centroidLat, centroidLng, dist)
            dist[0] <= STATIONARY_RADIUS_M
        }

        if (allStationary) {
            isInStop = true
            stopCenterLat = centroidLat
            stopCenterLng = centroidLng
        }
    }

    private fun checkForStopEnd(current: Location) {
        val dist = FloatArray(1)
        Location.distanceBetween(
            current.latitude, current.longitude,
            stopCenterLat, stopCenterLng,
            dist,
        )
        if (dist[0] > MOVE_DETECTION_RADIUS_M) {
            finalizeStop()
        }
    }

    private fun finalizeStop() {
        val lat = stopCenterLat
        val lng = stopCenterLng

        isInStop = false
        stopCenterLat = 0.0
        stopCenterLng = 0.0
        locationBuffer.clear()

        if (!WoltSessionTracker.isSessionActive(this)) return

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val vehicleClass = prefs.getString(PREF_VEHICLE_CLASS, null) ?: return

        // Capture time on the calling thread; resolveCity blocks on IO.
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1  // Calendar: 1-7 → 0-6
        val hourBucket = cal.get(Calendar.HOUR_OF_DAY)

        serviceScope.launch {
            val city = resolveCity(lat, lng) ?: return@launch
            val stop = StopEntity(
                city = city,
                geohash = GeoUtils.encode(lat, lng),
                dayOfWeek = dayOfWeek,
                hourBucket = hourBucket,
                vehicleClass = vehicleClass,
            )
            (application as HeatmapApplication).database.stopDao().insert(stop)
        }
    }

    // Called from serviceScope (IO dispatcher) — blocking Geocoder call is intentional.
    @Suppress("DEPRECATION")
    private fun resolveCity(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val addresses = Geocoder(this, Locale.getDefault()).getFromLocation(lat, lng, 1)
            val raw = addresses?.firstOrNull()?.locality
                ?: addresses?.firstOrNull()?.subAdminArea
                ?: return null
            raw.lowercase().replace("\\s+".toRegex(), "_")
        } catch (e: IOException) {
            null
        }
    }

    private fun buildNotification() = run {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Stop detection", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wolt Heatmap")
            .setContentText("Watching for delivery stops")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "stop_detection"

        private const val LOCATION_INTERVAL_MS = 60_000L       // 1-min heartbeat
        private const val BUFFER_WINDOW_MS = 5 * 60_000L       // keep last 5 min of fixes
        private const val STOP_MIN_DURATION_MS = 3 * 60_000L   // need 3+ min to confirm stop
        private const val STATIONARY_RADIUS_M = 100f           // all fixes must be within 100 m
        private const val MOVE_DETECTION_RADIUS_M = 150f       // exit radius to end the stop

        // SharedPreferences keys — PREF_VEHICLE_CLASS is written during onboarding.
        // City is resolved at stop-time via Geocoder rather than stored in prefs.
        const val PREFS_NAME = "onboarding"
        const val PREF_VEHICLE_CLASS = "vehicle_class"

        fun start(context: Context) =
            ContextCompat.startForegroundService(
                context,
                Intent(context, StopDetectionService::class.java),
            )

        fun stop(context: Context) =
            context.stopService(Intent(context, StopDetectionService::class.java))
    }
}
