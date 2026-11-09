package zone.gtfs.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zone.gtfs.wifi.session.LatLon
import zone.gtfs.wifi.transitous.alightingOptions
import zone.gtfs.wifi.transitous.boardingIndex
import zone.gtfs.wifi.transitous.buildTrip
import zone.gtfs.wifi.transitous.dedupeStops
import zone.gtfs.wifi.transitous.gtfsIds
import zone.gtfs.wifi.transitous.gtfsStopId

class TransitousTest {
    @Test
    fun derivesGtfsIds() {
        val ids = gtfsIds("20261108_14:19_de-DELFI_3468911239", "de-DELFI_de:VBB:11000000|R-Bahn|FEX:_100", "10434")
        assertEquals("de-DELFI", ids.feed)
        assertEquals("3468911239", ids.tripId)
        assertEquals("de:VBB:11000000|R-Bahn|FEX:_100", ids.routeId)
        assertEquals("10434", ids.agencyId)
        assertEquals("20261108", ids.serviceDate)
        assertEquals("de:11000:900003200:1:50", gtfsStopId("de-DELFI_de:11000:900003200:1:50"))
    }

    @Test
    fun unparseableIdsAreNull() {
        val ids = gtfsIds("weird", "noseparator", "")
        assertNull(ids.tripId)
        assertNull(ids.routeId)
        assertNull(ids.agencyId)
        assertNull(gtfsStopId("_x"))
    }

    @Test
    fun boardsAtTheDepartureStop() {
        val dep = Fixtures.stopTimes.first()
        val leg = Fixtures.leg
        val i = boardingIndex(leg, dep)
        assertEquals(dep.place.stopId, leg.stops[i].stopId)
        val options = alightingOptions(leg, dep)
        assertEquals(leg.stops.size - i - 1, options.size)
        assertEquals(leg.to.stopId, options.last().stopId)
    }

    @Test
    fun buildsATrip() {
        val dep = Fixtures.stopTimes.first()
        val alight = alightingOptions(Fixtures.leg, dep)[2]
        val trip = buildTrip(dep, alight, " 7 ", "")
        assertEquals("S7", trip.routeName)
        assertEquals("de-VBB", trip.gtfs.feed)
        assertEquals(dep.place.stopId, trip.boarding.transitousStopId)
        assertEquals(alight.stopId, trip.alighting.transitousStopId)
        assertEquals("7", trip.carNumber)
        assertNull(trip.travelClass)
        assertTrue(trip.boarding.scheduledTime < trip.alighting.scheduledTime)
    }

    @Test
    fun nearbyStopsAreOnePerName() {
        val at = LatLon(52.5255, 13.3694)
        val stops = dedupeStops(Fixtures.stops, at, 600.0)
        assertEquals(stops.map { it.name.lowercase() }.distinct().size, stops.size)
        assertTrue(stops.size < Fixtures.stops.size)
    }
}
