package zone.gtfs.wifi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import zone.gtfs.wifi.data.AppDatabase
import zone.gtfs.wifi.data.SessionRepository
import zone.gtfs.wifi.transitous.TransitousClient
import zone.gtfs.wifi.upload.Uploads

class WifiApp : Application() {
    /** For writes that must outlive a screen or the service. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http by lazy { baseHttpClient() }
    val db by lazy { AppDatabase.open(this) }
    val sessions by lazy { SessionRepository(db.sessions()) }
    val transitous by lazy { TransitousClient(http, BuildConfig.TRANSITOUS_URL) }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                RECORDING_CHANNEL,
                getString(R.string.channel_recording),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        Uploads.schedule(this)
    }

    companion object {
        const val RECORDING_CHANNEL = "recording"
    }
}
