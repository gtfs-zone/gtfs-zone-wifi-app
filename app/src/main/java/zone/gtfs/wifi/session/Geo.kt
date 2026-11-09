package zone.gtfs.wifi.session

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

private const val EARTH_RADIUS_M = 6_371_008.8

private fun rad(deg: Double) = deg * PI / 180.0

fun haversineM(a: LatLon, b: LatLon): Double {
    val dLat = rad(b.lat - a.lat)
    val dLon = rad(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceAtMost(1.0)))
}

/** Decodes an encoded polyline with `precision` decimal digits. */
fun decodePolyline(encoded: String, precision: Int): List<LatLon> {
    val factor = 10.0.pow(precision)
    val out = mutableListOf<LatLon>()
    var i = 0
    var lat = 0L
    var lon = 0L
    fun next(): Long {
        var shift = 0
        var result = 0L
        while (true) {
            val b = encoded[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    }
    while (i < encoded.length) {
        lat += next()
        lon += next()
        out += LatLon(lat / factor, lon / factor)
    }
    return out
}

/** Position on a polyline: segment index and fraction along it. */
data class LinePos(val segment: Int, val fraction: Double) : Comparable<LinePos> {
    override fun compareTo(other: LinePos) =
        compareValuesBy(this, other, { it.segment }, { it.fraction })
}

// Local equirectangular metres around `origin`; accurate at the short
// distances the trim radius cares about.
private fun local(origin: LatLon, p: LatLon): Pair<Double, Double> {
    val k = EARTH_RADIUS_M * PI / 180.0
    return Pair((p.lon - origin.lon) * k * cos(rad(origin.lat)), (p.lat - origin.lat) * k)
}

private fun projectOnSegment(a: LatLon, b: LatLon, p: LatLon): Pair<Double, Double> {
    val (ax, ay) = local(p, a)
    val (bx, by) = local(p, b)
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val f = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
    val x = ax + f * dx
    val y = ay + f * dy
    return Pair(f, sqrt(x * x + y * y))
}

/** The nearest position on `line` to `p`, and the distance to it in metres. */
fun nearestOnLine(line: List<LatLon>, p: LatLon): Pair<LinePos, Double> {
    if (line.size == 1) return Pair(LinePos(0, 0.0), haversineM(line[0], p))
    var best = Pair(LinePos(0, 0.0), Double.MAX_VALUE)
    for (s in 0 until line.size - 1) {
        val (f, d) = projectOnSegment(line[s], line[s + 1], p)
        if (d < best.second) best = Pair(LinePos(s, f), d)
    }
    return best
}

private fun interpolate(line: List<LatLon>, pos: LinePos): LatLon {
    if (pos.segment >= line.size - 1) return line.last()
    val a = line[pos.segment]
    val b = line[pos.segment + 1]
    return LatLon(a.lat + (b.lat - a.lat) * pos.fraction, a.lon + (b.lon - a.lon) * pos.fraction)
}

/** The part of `line` between two positions, in line order. */
fun subLine(line: List<LatLon>, from: LinePos, to: LinePos): List<LatLon> {
    val (a, b) = if (from <= to) Pair(from, to) else Pair(to, from)
    val out = mutableListOf(interpolate(line, a))
    for (i in a.segment + 1..b.segment) out += line[i]
    out += interpolate(line, b)
    return out
}
