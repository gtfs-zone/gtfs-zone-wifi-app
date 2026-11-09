package zone.gtfs.wifi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import zone.gtfs.wifi.session.LatLon

/** A fresh fix, else the last known one. Callers hold fine location. */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): LatLon? {
    val lm = context.getSystemService(LocationManager::class.java)
    val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.hasProvider(LocationManager.FUSED_PROVIDER)) {
        LocationManager.FUSED_PROVIDER
    } else {
        LocationManager.GPS_PROVIDER
    }
    val fresh = withTimeoutOrNull(20_000) {
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            LocationManagerCompat.getCurrentLocation(lm, provider, cancel, ContextCompat.getMainExecutor(context)) {
                if (cont.isActive) cont.resume(it)
            }
        }
    }
    val fix = fresh ?: listOf(provider, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    return fix?.let { LatLon(it.latitude, it.longitude) }
}
