package zone.gtfs.wifi.record

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/** Tracks the wifi and cell networks, so a test can bind to either. */
class Networks(context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    @Volatile var wifi: Network? = null
        private set

    @Volatile var cell: Network? = null
        private set

    private fun callback(set: (Network?) -> Unit) = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = set(network)
        override fun onLost(network: Network) = set(null)
    }

    private val wifiCallback = callback { wifi = it }
    private val cellCallback = callback { cell = it }

    fun start() {
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            wifiCallback,
        )
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build(),
            cellCallback,
        )
    }

    fun stop() {
        runCatching { cm.unregisterNetworkCallback(wifiCallback) }
        runCatching { cm.unregisterNetworkCallback(cellCallback) }
    }

    /** Connected wifi first, else cell; null with neither. */
    fun forTest(): Pair<String, Network>? =
        wifi?.let { "wifi" to it } ?: cell?.let { "cell" to it }
}
