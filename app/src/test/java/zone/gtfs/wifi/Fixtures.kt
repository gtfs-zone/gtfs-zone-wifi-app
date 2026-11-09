package zone.gtfs.wifi

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import kotlinx.serialization.json.Json
import zone.gtfs.wifi.data.NetTestEntity
import zone.gtfs.wifi.data.SampleEntity
import zone.gtfs.wifi.data.SessionEntity
import zone.gtfs.wifi.data.SessionState
import zone.gtfs.wifi.session.LatLon
import zone.gtfs.wifi.transitous.MapStop
import zone.gtfs.wifi.transitous.StopTimesResponse
import zone.gtfs.wifi.transitous.TripResponse

object Fixtures {
    private val json = Json { ignoreUnknownKeys = true }

    fun text(path: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream(path)) { "missing $path" }
            .bufferedReader().use { it.readText() }

    val stops: List<MapStop> by lazy { json.decodeFromString(text("transitous/map-stops.json")) }
    val stopTimes by lazy { json.decodeFromString<StopTimesResponse>(text("transitous/stoptimes.json")).stopTimes }
    val leg by lazy { json.decodeFromString<TripResponse>(text("transitous/trip.json")).legs.single() }

    private val schema by lazy {
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(text("schemas/session.v1.json"), InputFormat.JSON)
    }

    /** Schema errors for a session JSON document; empty when valid. */
    fun schemaErrors(session: String): List<String> =
        schema.validate(session, InputFormat.JSON).map { it.toString() }

    fun session(kind: String, startedAt: Long = 1_762_000_000_000L) = SessionEntity(
        id = "0b1c2d3e-4f50-4a6b-8c7d-9e0f1a2b3c4d",
        kind = kind,
        state = SessionState.ENDED,
        startedAt = startedAt,
        endedAt = startedAt + 3_600_000,
        title = "test",
        tripJson = null,
        stationJson = null,
        shape = null,
        shapePrecision = null,
        reportsJson = null,
        checklistJson = null,
        payload = null,
        keptSamples = null,
        keptNetTests = null,
        sentAt = null,
        lastError = null,
    )

    fun sample(t: Long, at: LatLon, wifi: Boolean = true) = SampleEntity(
        sessionId = "s",
        t = t,
        lat = at.lat,
        lon = at.lon,
        acc = 8.5,
        speed = 12.0,
        wifiConnected = wifi,
        wifiSsid = if (wifi) "Train-Wifi" else null,
        wifiBssid = if (wifi) "06:aa:bb:cc:dd:01" else null,
        wifiRssi = if (wifi) -61 else null,
        wifiLinkMbps = if (wifi) 72 else null,
        wifiFrequency = if (wifi) 2437 else null,
        cellTech = "lte",
        cellMcc = "262",
        cellMnc = "02",
        cellDbm = -101,
        cellLevel = 2,
    )

    fun netTest(t: Long, reachable: Boolean = true) = NetTestEntity(
        sessionId = "s",
        t = t,
        transport = "wifi",
        reachable = reachable,
        captivePortal = !reachable,
        latencyMs = if (reachable) 84.5 else null,
        fetchBytes = if (reachable) 102_400 else null,
        fetchMs = if (reachable) 950.0 else null,
    )
}
