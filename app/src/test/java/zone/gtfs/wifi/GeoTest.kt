package zone.gtfs.wifi

import org.junit.Assert.assertEquals
import org.junit.Test
import zone.gtfs.wifi.session.LatLon
import zone.gtfs.wifi.session.decodePolyline
import zone.gtfs.wifi.session.haversineM
import zone.gtfs.wifi.session.nearestOnLine

class GeoTest {
    @Test
    fun decodesTheReferencePolyline() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)), points)
    }

    @Test
    fun decodesTheTransitousShape() {
        val geometry = Fixtures.leg.legGeometry!!
        val line = decodePolyline(geometry.points, geometry.precision)
        val first = Fixtures.leg.from
        assertEquals(0.0, haversineM(line.first(), LatLon(first.lat, first.lon)), 50.0)
    }

    @Test
    fun distanceToALine() {
        val line = listOf(LatLon(52.0, 13.0), LatLon(52.0, 13.1))
        val (_, d) = nearestOnLine(line, LatLon(52.001, 13.05))
        assertEquals(111.2, d, 1.0)
        assertEquals(haversineM(LatLon(52.0, 13.1), LatLon(52.0, 13.2)), nearestOnLine(line, LatLon(52.0, 13.2)).second, 1.0)
    }
}
