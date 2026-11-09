package zone.gtfs.wifi.session

import java.time.Instant
import zone.gtfs.wifi.Config
import zone.gtfs.wifi.data.NetTestEntity
import zone.gtfs.wifi.data.SampleEntity
import zone.gtfs.wifi.data.SessionEntity

/** RFC 3339 UTC with at most millisecond precision, as the schema wants. */
fun timestamp(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

/** A Transitous timestamp in the schema's format. */
fun timestamp(transitous: String): String = timestamp(Instant.parse(transitous).toEpochMilli())

/** Builds the upload for [session], trimmed to the trip or station. */
fun buildSession(
    session: SessionEntity,
    samples: List<SampleEntity>,
    netTests: List<NetTestEntity>,
    appVersion: String,
): SessionV1 {
    val trip = session.tripJson?.let { sessionJson.decodeFromString<Trip>(it) }
    val station = session.stationJson?.let { sessionJson.decodeFromString<Place>(it) }
    val kept = when {
        trip != null -> trimTrip(
            samples,
            LatLon(trip.boarding.lat, trip.boarding.lon),
            LatLon(trip.alighting.lat, trip.alighting.lon),
            session.shape?.let { decodePolyline(it, session.shapePrecision ?: 6) },
        )
        station != null -> trimStation(samples, LatLon(station.lat, station.lon))
        else -> error("session ${session.id} has neither trip nor station")
    }
    val tests = trimToWindow(netTests, kept, Config.NET_TEST_SLACK_MS) { it.t }
    val startedAt = kept.firstOrNull()?.t ?: session.startedAt
    val endedAt = kept.lastOrNull()?.t ?: session.endedAt ?: session.startedAt
    return SessionV1(
        schemaVersion = 1,
        sessionId = session.id,
        appVersion = appVersion,
        kind = session.kind,
        startedAt = timestamp(startedAt),
        endedAt = timestamp(maxOf(startedAt, endedAt)),
        samples = kept.map { it.toJson() },
        netTests = tests.map { it.toJson() },
        trip = trip,
        station = station,
        reports = if (trip != null) {
            session.reportsJson?.let { sessionJson.decodeFromString<Reports>(it) } ?: Reports.EMPTY
        } else {
            null
        },
        stationChecklist = if (station != null) {
            session.checklistJson?.let { sessionJson.decodeFromString<StationChecklist>(it) }
                ?: StationChecklist.EMPTY
        } else {
            null
        },
    )
}

private fun SampleEntity.toJson() = Sample(
    t = timestamp(t),
    lat = lat,
    lon = lon,
    acc = acc.coerceAtLeast(0.0),
    speed = speed?.takeIf { it >= 0 },
    wifi = if (wifiConnected) {
        Wifi(wifiSsid, wifiBssid, wifiRssi, wifiLinkMbps, wifiFrequency)
    } else {
        null
    },
    cell = cellTech?.let { Cell(it, cellMcc, cellMnc, cellDbm, cellLevel) },
)

private fun NetTestEntity.toJson() =
    NetTest(timestamp(t), transport, reachable, captivePortal, latencyMs, fetchBytes, fetchMs)
