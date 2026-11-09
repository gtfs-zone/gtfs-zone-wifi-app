package zone.gtfs.wifi.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Mirrors schemas/session.v1.json from gtfs-zone-wifi-ingest. Fields without
// a default are always written, null included; fields defaulting to null are
// left out when null.

val sessionJson = Json {
    encodeDefaults = false
    explicitNulls = true
    ignoreUnknownKeys = true
}

@Serializable
data class SessionV1(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("session_id") val sessionId: String,
    @SerialName("app_version") val appVersion: String,
    val kind: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    val samples: List<Sample>,
    @SerialName("net_tests") val netTests: List<NetTest>,
    val trip: Trip? = null,
    val station: Place? = null,
    val reports: Reports? = null,
    @SerialName("station_checklist") val stationChecklist: StationChecklist? = null,
)

@Serializable
data class Place(
    @SerialName("transitous_stop_id") val transitousStopId: String,
    @SerialName("transitous_parent_id") val transitousParentId: String?,
    @SerialName("gtfs_stop_id") val gtfsStopId: String?,
    val name: String,
    val lat: Double,
    val lon: Double,
)

@Serializable
data class TripStop(
    @SerialName("transitous_stop_id") val transitousStopId: String,
    @SerialName("transitous_parent_id") val transitousParentId: String?,
    @SerialName("gtfs_stop_id") val gtfsStopId: String?,
    val name: String,
    val lat: Double,
    val lon: Double,
    @SerialName("scheduled_time") val scheduledTime: String,
)

@Serializable
data class GtfsIds(
    val feed: String?,
    @SerialName("trip_id") val tripId: String?,
    @SerialName("route_id") val routeId: String?,
    @SerialName("agency_id") val agencyId: String?,
    @SerialName("service_date") val serviceDate: String?,
)

@Serializable
data class Trip(
    @SerialName("transitous_trip_id") val transitousTripId: String,
    @SerialName("transitous_route_id") val transitousRouteId: String,
    @SerialName("transitous_source") val transitousSource: String?,
    val gtfs: GtfsIds,
    @SerialName("route_name") val routeName: String,
    @SerialName("route_type") val routeType: Int?,
    val mode: String?,
    @SerialName("agency_name") val agencyName: String?,
    val headsign: String?,
    @SerialName("trip_short_name") val tripShortName: String?,
    val boarding: TripStop,
    val alighting: TripStop,
    @SerialName("car_number") val carNumber: String?,
    @SerialName("class") val travelClass: String?,
)

@Serializable
data class Wifi(
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    @SerialName("link_mbps") val linkMbps: Int?,
    val frequency: Int?,
)

@Serializable
data class Cell(
    val tech: String,
    val mcc: String?,
    val mnc: String?,
    @SerialName("level_dbm") val levelDbm: Int?,
    @SerialName("level_0_4") val level04: Int?,
)

@Serializable
data class Sample(
    val t: String,
    val lat: Double,
    val lon: Double,
    val acc: Double,
    val speed: Double?,
    val wifi: Wifi?,
    val cell: Cell?,
)

@Serializable
data class NetTest(
    val t: String,
    val transport: String,
    val reachable: Boolean,
    @SerialName("captive_portal") val captivePortal: Boolean?,
    @SerialName("latency_ms") val latencyMs: Double?,
    @SerialName("fetch_bytes") val fetchBytes: Long?,
    @SerialName("fetch_ms") val fetchMs: Double?,
)

@Serializable
data class Reports(
    val outlets: String?,
    val wifi: String?,
    val portal: String?,
    @SerialName("data_cap") val dataCap: Boolean?,
    @SerialName("vpn_blocked") val vpnBlocked: Boolean?,
) {
    companion object {
        val EMPTY = Reports(null, null, null, null, null)
        val OUTLETS = listOf("none", "present", "working")
        val WIFI = listOf("absent", "broken", "works")
        val PORTAL = listOf("none", "click", "email", "account", "sms", "password", "paid")
    }
}

@Serializable
data class StationChecklist(
    @SerialName("needed_email") val neededEmail: Boolean?,
    @SerialName("needed_account") val neededAccount: Boolean?,
    @SerialName("needed_sms") val neededSms: Boolean?,
    @SerialName("needed_checkbox") val neededCheckbox: Boolean?,
    @SerialName("needed_password") val neededPassword: Boolean?,
    @SerialName("needed_payment") val neededPayment: Boolean?,
    @SerialName("could_not_connect") val couldNotConnect: Boolean?,
) {
    companion object {
        val EMPTY = StationChecklist(null, null, null, null, null, null, null)
    }
}
