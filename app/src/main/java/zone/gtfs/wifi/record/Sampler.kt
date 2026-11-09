package zone.gtfs.wifi.record

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import zone.gtfs.wifi.session.Cell
import zone.gtfs.wifi.session.Wifi

private val BSSID = Regex("^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")
private val MCC = Regex("^\\d{3}$")
private val MNC = Regex("^\\d{2,3}$")

/** The connected wifi network in schema form, null when not connected. */
fun wifiFromInfo(info: WifiInfo?): Wifi? {
    if (info == null || info.networkId == -1 || info.supplicantState != SupplicantState.COMPLETED) return null
    val ssid = info.ssid?.removeSurrounding("\"")
        ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
    return Wifi(
        ssid = ssid?.take(32),
        bssid = cleanBssid(info.bssid),
        rssi = info.rssi.takeIf { it in -126..0 },
        linkMbps = info.linkSpeed.takeIf { it >= 0 },
        frequency = info.frequency.takeIf { it > 0 },
    )
}

/** Lower-case BSSID, null when redacted or malformed. */
fun cleanBssid(bssid: String?): String? =
    bssid?.lowercase()?.takeIf { it != "02:00:00:00:00:00" && it != "00:00:00:00:00:00" && BSSID.matches(it) }

private fun dbm(v: Int) = v.takeIf { it != CellInfo.UNAVAILABLE && it in -160..0 }
private fun level(v: Int) = v.takeIf { it in 0..4 }
private fun mcc(v: String?) = v?.takeIf { MCC.matches(it) }
private fun mnc(v: String?) = v?.takeIf { MNC.matches(it) }

/** The serving cell in schema form. */
@Suppress("DEPRECATION")
fun cellFromInfo(info: CellInfo): Cell? = when (info) {
    is CellInfoLte -> info.cellIdentity.let { id: CellIdentityLte ->
        Cell("lte", mcc(id.mccString), mnc(id.mncString), dbm(info.cellSignalStrength.dbm), level(info.cellSignalStrength.level))
    }
    is CellInfoNr -> (info.cellIdentity as CellIdentityNr).let { id ->
        Cell("nr", mcc(id.mccString), mnc(id.mncString), dbm(info.cellSignalStrength.dbm), level(info.cellSignalStrength.level))
    }
    is CellInfoWcdma -> info.cellIdentity.let { id: CellIdentityWcdma ->
        Cell("wcdma", mcc(id.mccString), mnc(id.mncString), dbm(info.cellSignalStrength.dbm), level(info.cellSignalStrength.level))
    }
    is CellInfoTdscdma -> info.cellIdentity.let { id: CellIdentityTdscdma ->
        Cell("tdscdma", mcc(id.mccString), mnc(id.mncString), dbm(info.cellSignalStrength.dbm), level(info.cellSignalStrength.level))
    }
    is CellInfoGsm -> info.cellIdentity.let { id: CellIdentityGsm ->
        Cell("gsm", mcc(id.mccString), mnc(id.mncString), dbm(info.cellSignalStrength.dbm), level(info.cellSignalStrength.level))
    }
    is CellInfoCdma -> Cell("cdma", null, null, dbm(info.cellSignalStrength.dbm), level(info.cellSignalStrength.level))
    else -> null
}

/** Reads the connected wifi and the serving cell. Callers hold fine location. */
class Sampler(context: Context) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val telephony = context.applicationContext.getSystemService(TelephonyManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    @Suppress("DEPRECATION")
    fun wifi(): Wifi? = wifiFromInfo(wifi?.connectionInfo)

    @SuppressLint("MissingPermission")
    suspend fun cell(): Cell? {
        val tm = telephony ?: return null
        val fresh = try {
            withTimeoutOrNull(3_000) {
                suspendCancellableCoroutine<List<CellInfo>?> { cont ->
                    tm.requestCellInfoUpdate(
                        executor,
                        object : TelephonyManager.CellInfoCallback() {
                            override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                if (cont.isActive) cont.resume(cellInfo.toList())
                            }

                            override fun onError(errorCode: Int, detail: Throwable?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            }
        } catch (_: SecurityException) {
            null
        }
        val all = fresh ?: try {
            tm.allCellInfo.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        return all.firstOrNull { it.isRegistered }?.let(::cellFromInfo)
    }

    fun close() = executor.shutdown()
}
