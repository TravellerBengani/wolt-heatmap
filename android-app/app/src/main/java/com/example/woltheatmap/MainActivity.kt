package com.example.woltheatmap

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.woltheatmap.detection.StopDetectionService
import com.example.woltheatmap.detection.WoltSessionTracker
import com.example.woltheatmap.onboarding.buildVehiclePickerView
import com.example.woltheatmap.util.GeoUtils
import com.example.woltheatmap.work.UploadWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ── Permission launchers ─────────────────────────────────────────────────
    // All must be registered (as properties) before the Activity is started.

    private val fineLocLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showMessage(
                "Location access required",
                "The app needs location to detect delivery stops. " +
                "Your exact trace is never uploaded — only an anonymised area code.",
                "Try again",
            ) { startPermissionFlow() }
            return@registerForActivityResult
        }
        requestBackgroundLocation()
    }

    private val bgLocLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Whether granted or not we continue — the service degrades gracefully.
        promptUsageAccess()
    }

    private val usageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        promptBatteryOptimization()
    }

    private val batteryOptLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        completeOnboarding()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val prefs = getSharedPreferences(StopDetectionService.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getString(StopDetectionService.PREF_VEHICLE_CLASS, null) != null) {
            UploadWorker.schedulePeriodicUpload(this)
            StopDetectionService.start(this)
            showHeatmapScreen()
        } else {
            showVehicleClassPicker()
        }
    }

    // ── Onboarding steps ─────────────────────────────────────────────────────

    private fun showVehicleClassPicker() {
        setContentView(buildVehiclePickerView(this) { vehicleClass ->
            getSharedPreferences(StopDetectionService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(StopDetectionService.PREF_VEHICLE_CLASS, vehicleClass.serialKey)
                .apply()
            startPermissionFlow()
        })
    }

    private fun startPermissionFlow() {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine) {
            showMessage(
                "Step 1 of 3: Location",
                "The app tracks where you stop between orders. " +
                "Exact coordinates are hashed on-device — the server only ever sees a coarse area code.",
                "Grant location",
            ) { fineLocLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        } else {
            requestBackgroundLocation()
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Background location is implicitly included with fine location on API <29.
            promptUsageAccess()
            return
        }
        val hasBg = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasBg) {
            promptUsageAccess()
            return
        }
        showMessage(
            "Step 1 of 3: Background location",
            "Choose \"Allow all the time\" so stops can be detected while Wolt is running in the background.",
            "Continue",
        ) { bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    }

    private fun promptUsageAccess() {
        if (WoltSessionTracker.hasUsageStatsPermission(this)) {
            promptBatteryOptimization()
            return
        }
        showMessage(
            "Step 2 of 3: Usage Access",
            "The app checks whether the Wolt courier app was open in the last 30 minutes before logging a stop. " +
            "Nothing from Wolt's servers is read — only which app was on-screen on your device.",
            "Open settings",
        ) { usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }

    private fun promptBatteryOptimization() {
        showMessage(
            "Step 3 of 3: Battery",
            "Exempting this app from battery optimisation keeps location updates reliable while you're delivering.",
            "Allow",
        ) {
            try {
                batteryOptLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (e: ActivityNotFoundException) {
                // Some ROMs don't expose this setting — skip it.
                completeOnboarding()
            }
        }
    }

    private fun completeOnboarding() {
        UploadWorker.schedulePeriodicUpload(this)
        StopDetectionService.start(this)
        showHeatmapScreen()
    }

    // ── Heatmap screen ───────────────────────────────────────────────────────

    private fun showHeatmapScreen() {
        val prefs = getSharedPreferences(StopDetectionService.PREFS_NAME, MODE_PRIVATE)
        val vehicleClass = prefs.getString(StopDetectionService.PREF_VEHICLE_CLASS, "fast") ?: "fast"

        val headerText = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }
        val subText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        val refreshBtn = Button(this).apply {
            text = "Refresh"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val resultsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 64, 48, 64)
                addView(headerText)
                addView(subText)
                addView(refreshBtn)
                addView(resultsLayout)
            })
        })

        fun refresh() {
            val cal = Calendar.getInstance()
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val vehicleLabel = if (vehicleClass == "fast") "Car/Moto" else "Bike/Scooter"
            headerText.text = "Best spots right now"
            subText.text = "$vehicleLabel · ${"%02d:00".format(hour)}"
            resultsLayout.removeAllViews()
            resultsLayout.addView(centeredText("Finding your location…"))

            lifecycleScope.launch {
                val city = resolveCurrentCity()
                if (city == null) {
                    resultsLayout.removeAllViews()
                    resultsLayout.addView(centeredText(
                        "Couldn't determine your city.\n" +
                        "Make sure location is enabled and tap Refresh."
                    ))
                    return@launch
                }

                val cells = try {
                    (application as HeatmapApplication).api.getHeatmap(city, vehicleClass, dow, hour)
                } catch (e: Exception) {
                    null
                }

                resultsLayout.removeAllViews()
                when {
                    cells == null -> resultsLayout.addView(centeredText(
                        "Couldn't reach the server.\nUploads will retry automatically."
                    ))
                    cells.isEmpty() -> resultsLayout.addView(centeredText(
                        "No spots have enough data yet for this\ntime slot in $city.\n\n" +
                        "Suggestions appear once ~8 stops are logged."
                    ))
                    else -> cells.forEachIndexed { i, cell ->
                        val (lat, lng) = GeoUtils.decodeToCenter(cell.geohash)
                        resultsLayout.addView(LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundColor(if (i % 2 == 0) Color.parseColor("#F7F7F7") else Color.WHITE)
                            setPadding(24, 20, 24, 20)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            addView(TextView(this@MainActivity).apply {
                                text = "#${i + 1}  %.4f°N  %.4f°E".format(lat, lng)
                                textSize = 15f
                                setTextColor(Color.BLACK)
                            })
                            addView(TextView(this@MainActivity).apply {
                                text = "Activity score: %.1f".format(cell.decayed_weight)
                                textSize = 12f
                                setTextColor(Color.GRAY)
                            })
                        })
                    }
                }
            }
        }

        refreshBtn.setOnClickListener { refresh() }
        refresh()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun showMessage(title: String, body: String, btnLabel: String, onClick: () -> Unit) {
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(64, 0, 64, 0)

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 22f
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, 20)
            })
            addView(TextView(this@MainActivity).apply {
                text = body
                textSize = 15f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, 0, 48)
            })
            addView(Button(this@MainActivity).apply {
                text = btnLabel
                setOnClickListener { onClick() }
            })
        })
    }

    private fun centeredText(msg: String) = TextView(this).apply {
        text = msg
        textSize = 14f
        setTextColor(Color.GRAY)
        gravity = Gravity.CENTER
        setPadding(0, 32, 0, 16)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private suspend fun resolveCurrentCity(): String? {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) return null

        val location = suspendCancellableCoroutine<Location?> { cont ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
                    .addOnCanceledListener  { cont.resume(null) }
            } catch (e: SecurityException) {
                cont.resume(null)
            }
        } ?: return null

        return withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            try {
                @Suppress("DEPRECATION") // sync Geocoder is fine on IO thread
                val addresses = Geocoder(this@MainActivity, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                val raw = addresses?.firstOrNull()?.locality
                    ?: addresses?.firstOrNull()?.subAdminArea
                    ?: return@withContext null
                raw.lowercase().replace("\\s+".toRegex(), "_")
            } catch (e: IOException) {
                null
            }
        }
    }
}
