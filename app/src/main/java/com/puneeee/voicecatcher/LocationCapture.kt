package com.puneeee.voicecatcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocationCapture(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val geocoderExecutor = Executors.newSingleThreadExecutor()

    fun capture(onLocation: (CaptureLocation?) -> Unit) {
        val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return onLocation(null)
        val manager = context.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull(manager::isProviderEnabled)
            ?: return onLocation(null)
        val delivered = AtomicBoolean(false)
        val locationReceived = AtomicBoolean(false)
        val cancellationSignal = CancellationSignal()
        lateinit var timeout: Runnable
        fun deliver(location: Location?) {
            if (!locationReceived.compareAndSet(false, true)) return
            if (location == null) {
                if (delivered.compareAndSet(false, true)) onLocation(null)
                return
            }
            geocoderExecutor.execute {
                val capture = location.toCaptureLocation(resolvePlace(location))
                context.mainExecutor.execute {
                    if (delivered.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeout)
                        onLocation(capture)
                    }
                }
            }
        }
        timeout = Runnable {
            cancellationSignal.cancel()
            if (delivered.compareAndSet(false, true)) onLocation(null)
        }
        handler.postDelayed(timeout, LOCATION_TIMEOUT_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(provider, cancellationSignal, context.mainExecutor) { deliver(it) }
        } else {
            @Suppress("MissingPermission")
            deliver(manager.getLastKnownLocation(provider))
        }
    }

    private fun resolvePlace(location: Location): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?.let { address -> listOfNotNull(address.subLocality, address.locality, address.adminArea).distinct().joinToString(", ") }
            ?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun Location.toCaptureLocation(placeLabel: String?) = CaptureLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        placeLabel = placeLabel,
    )

    private companion object { const val LOCATION_TIMEOUT_MS = 10_000L }
}
