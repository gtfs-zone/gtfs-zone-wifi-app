package zone.gtfs.wifi.record

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zone.gtfs.wifi.Config
import zone.gtfs.wifi.R
import zone.gtfs.wifi.WifiApp
import zone.gtfs.wifi.data.NetTestEntity
import zone.gtfs.wifi.data.SampleEntity
import zone.gtfs.wifi.data.SessionState
import zone.gtfs.wifi.ui.MainActivity

/**
 * Foreground service (type location) for the active session: a sample every
 * [Config.SAMPLE_INTERVAL_MS] and a network test every
 * [Config.NET_TEST_INTERVAL_MS], all written to Room.
 */
class RecordingService : LifecycleService() {
    private val app get() = application as WifiApp
    private var loop: Job? = null
    private var sessionId: String? = null
    private var sampler: Sampler? = null
    private var networks: Networks? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }

    @Volatile private var lastFix: Location? = null
    private val listener = LocationListener { lastFix = it }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        if (!startInForeground()) return START_NOT_STICKY
        if (loop == null) loop = lifecycleScope.launch { record(intent?.getStringExtra(EXTRA_SESSION)) }
        return START_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, WifiApp.RECORDING_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.recording_title))
            .setContentText(getString(R.string.recording_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.end_session), stop)
            .build()
    }

    private fun startInForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        true
    } catch (_: RuntimeException) {
        // Location permission gone, or a restart the system no longer allows.
        stopSelf()
        false
    }

    private suspend fun record(requested: String?) {
        val session = (requested?.let { app.sessions.get(it) } ?: app.sessions.recording())
            ?.takeIf { it.state == SessionState.RECORDING }
        if (session == null) {
            stopRecording()
            return
        }
        sessionId = session.id
        val sampler = Sampler(this).also { sampler = it }
        val networks = Networks(this).also { it.start(); networks = it }
        val tester = NetTester(app.http)
        acquireWakeLock()
        startLocation()
        var lastTest = 0L
        while (lifecycleScope.isActive) {
            val tick = System.currentTimeMillis()
            if (app.sessions.get(session.id)?.state != SessionState.RECORDING) break
            sample(session.id, sampler, tick)
            if (tick - lastTest >= Config.NET_TEST_INTERVAL_MS) {
                lastTest = tick
                lifecycleScope.launch { netTest(session.id, tester, networks, tick) }
            }
            delay((Config.SAMPLE_INTERVAL_MS - (System.currentTimeMillis() - tick)).coerceAtLeast(0))
        }
        stopRecording()
    }

    private suspend fun sample(id: String, sampler: Sampler, now: Long) {
        val fix = lastFix?.takeIf { now - it.time <= Config.MAX_FIX_AGE_MS } ?: return
        val wifi = sampler.wifi()
        val cell = sampler.cell()
        app.sessions.addSample(
            SampleEntity(
                sessionId = id,
                t = now,
                lat = fix.latitude,
                lon = fix.longitude,
                acc = if (fix.hasAccuracy()) fix.accuracy.toDouble() else 0.0,
                speed = if (fix.hasSpeed()) fix.speed.toDouble() else null,
                wifiConnected = wifi != null,
                wifiSsid = wifi?.ssid,
                wifiBssid = wifi?.bssid,
                wifiRssi = wifi?.rssi,
                wifiLinkMbps = wifi?.linkMbps,
                wifiFrequency = wifi?.frequency,
                cellTech = cell?.tech,
                cellMcc = cell?.mcc,
                cellMnc = cell?.mnc,
                cellDbm = cell?.levelDbm,
                cellLevel = cell?.level04,
            ),
        )
    }

    private suspend fun netTest(id: String, tester: NetTester, networks: Networks, now: Long) {
        val (transport, network) = networks.forTest() ?: return
        val r = tester.run(network)
        app.sessions.addNetTest(
            NetTestEntity(
                sessionId = id,
                t = now,
                transport = transport,
                reachable = r.reachable,
                captivePortal = r.captivePortal,
                latencyMs = r.latencyMs,
                fetchBytes = r.fetchBytes,
                fetchMs = r.fetchMs,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                locationManager.hasProvider(LocationManager.FUSED_PROVIDER)
            ) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
        }
        for (p in providers) {
            runCatching {
                locationManager.requestLocationUpdates(p, Config.SAMPLE_INTERVAL_MS / 2, 0f, listener, Looper.getMainLooper())
            }
        }
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gtfs-zone-wifi:recording")
            .apply { acquire(WAKE_LOCK_MAX_MS) }
    }

    private fun stopRecording() {
        val id = sessionId
        if (id != null) app.scope.launch { app.sessions.end(id) }
        cleanUp()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanUp() {
        runCatching { locationManager.removeUpdates(listener) }
        networks?.stop()
        networks = null
        sampler?.close()
        sampler = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "zone.gtfs.wifi.STOP"
        private const val EXTRA_SESSION = "session"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_MAX_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context, sessionId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecordingService::class.java).putExtra(EXTRA_SESSION, sessionId),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
