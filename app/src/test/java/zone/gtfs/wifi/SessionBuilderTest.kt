package zone.gtfs.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zone.gtfs.wifi.session.LatLon
import zone.gtfs.wifi.session.Place
import zone.gtfs.wifi.session.Reports
import zone.gtfs.wifi.session.SessionV1
import zone.gtfs.wifi.session.Trip
import zone.gtfs.wifi.session.buildSession
import zone.gtfs.wifi.session.decodePolyline
import zone.gtfs.wifi.session.haversineM
import zone.gtfs.wifi.session.nearestOnLine
import zone.gtfs.wifi.session.sessionJson
import zone.gtfs.wifi.session.subLine
import zone.gtfs.wifi.transitous.alightingOptions
import zone.gtfs.wifi.transitous.buildTrip
import zone.gtfs.wifi.transitous.toPlace

class SessionBuilderTest {
    private val t0 = 1_762_600_000_000L
    private val departure = Fixtures.stopTimes.first()
    private val alighting = alightingOptions(Fixtures.leg, departure)[4]
    private val trip: Trip = buildTrip(departure, alighting, "3", "2")
    private val geometry = Fixtures.leg.legGeometry!!
    private val line = decodePolyline(geometry.points, geometry.precision)
    private val boardingAt = LatLon(trip.boarding.lat, trip.boarding.lon)
    private val alightingAt = LatLon(trip.alighting.lat, trip.alighting.lon)

    private fun tripSession() = Fixtures.session("trip", t0).copy(
        tripJson = sessionJson.encodeToString(Trip.serializer(), trip),
        shape = geometry.points,
        shapePrecision = geometry.precision,
        reportsJson = sessionJson.encodeToString(
            Reports.serializer(),
            Reports("present", "works", "click", null, false),
        ),
    )

    private fun encode(s: SessionV1) = sessionJson.encodeToString(SessionV1.serializer(), s)

    /** One sample per shape vertex, from the trip's first stop to its last. */
    private fun ride() = line.mapIndexed { i, p -> Fixtures.sample(t0 + i * 10_000L, p, wifi = i % 3 != 0) }

    @Test
    fun tripSessionMatchesTheSchema() {
        val samples = ride()
        val tests = samples.filterIndexed { i, _ -> i % 6 == 0 }.map { Fixtures.netTest(it.t, it.t % 4 != 0L) }
        val json = encode(buildSession(tripSession(), samples, tests, "0.1.0"))
        assertEquals(emptyList<String>(), Fixtures.schemaErrors(json))
        assertTrue(json.contains("\"car_number\":\"3\""))
        assertTrue(json.contains("\"data_cap\":null"))
        assertFalse(json.contains("\"station\""))
    }

    @Test
    fun tripIsTrimmedToTheStops() {
        val home = LatLon(trip.boarding.lat + 0.02, trip.boarding.lon)
        val offLine = LatLon(alightingAt.lat - 0.01, alightingAt.lon + 0.02)
        val samples = listOf(Fixtures.sample(t0 - 600_000, home)) + ride() +
            Fixtures.sample(t0 + 5_000, offLine)
        val built = buildSession(tripSession(), samples, emptyList(), "0.1.0")
        val part = subLine(line, nearestOnLine(line, boardingAt).first, nearestOnLine(line, alightingAt).first)
        assertTrue(built.samples.isNotEmpty())
        assertTrue(built.samples.size < samples.size)
        for (s in built.samples) {
            assertTrue(nearestOnLine(part, LatLon(s.lat, s.lon)).second <= Config.TRIM_RADIUS_M)
        }
        assertTrue(haversineM(LatLon(built.samples.first().lat, built.samples.first().lon), boardingAt) <= Config.TRIM_RADIUS_M)
        assertTrue(haversineM(LatLon(built.samples.last().lat, built.samples.last().lon), alightingAt) <= Config.TRIM_RADIUS_M)
        assertEquals(built.samples.first().t, built.startedAt)
        assertEquals(built.samples.last().t, built.endedAt)
    }

    @Test
    fun netTestsFollowTheKeptSamples() {
        val samples = ride()
        val built = buildSession(tripSession(), samples, samples.map { Fixtures.netTest(it.t) }, "0.1.0")
        val first = samples.first { haversineM(LatLon(it.lat, it.lon), boardingAt) <= Config.TRIM_RADIUS_M }.t
        assertTrue(built.netTests.isNotEmpty())
        assertTrue(built.netTests.size < samples.size)
        assertTrue(built.netTests.all { java.time.Instant.parse(it.t).toEpochMilli() >= first - Config.NET_TEST_SLACK_MS })
    }

    @Test
    fun withoutAShapeBothStopsMustBeReached() {
        val noShape = tripSession().copy(shape = null, shapePrecision = null)
        val partial = ride().filter { haversineM(LatLon(it.lat, it.lon), alightingAt) > 2_000 }.take(5)
        assertEquals(0, buildSession(noShape, partial, emptyList(), "0.1.0").samples.size)
        assertTrue(buildSession(noShape, ride(), emptyList(), "0.1.0").samples.isNotEmpty())
    }

    @Test
    fun emptyTripSessionMatchesTheSchema() {
        val json = encode(buildSession(tripSession().copy(reportsJson = null), emptyList(), emptyList(), "0.1.0"))
        assertEquals(emptyList<String>(), Fixtures.schemaErrors(json))
    }

    @Test
    fun stationSessionMatchesTheSchema() {
        val stop = Fixtures.stops.first()
        val at = LatLon(stop.lat, stop.lon)
        val session = Fixtures.session("station", t0).copy(
            stationJson = sessionJson.encodeToString(Place.serializer(), stop.toPlace()),
        )
        val far = LatLon(stop.lat + 0.01, stop.lon)
        val samples = (0 until 12).map { Fixtures.sample(t0 + it * 10_000L, at, wifi = it > 3) } +
            Fixtures.sample(t0 + 200_000, far)
        val built = buildSession(session, samples, samples.map { Fixtures.netTest(it.t, false) }, "0.1.0")
        assertEquals(12, built.samples.size)
        assertNull(built.reports)
        val json = encode(built)
        assertEquals(emptyList<String>(), Fixtures.schemaErrors(json))
        assertTrue(json.contains("\"station_checklist\":{\"needed_email\":null"))
    }

    @Test
    fun theValidatorRejectsABrokenSession() {
        val json = encode(buildSession(tripSession(), ride(), emptyList(), "0.1.0"))
            .replace("\"kind\":\"trip\"", "\"kind\":\"station\"")
        assertTrue(Fixtures.schemaErrors(json).isNotEmpty())
    }
}
