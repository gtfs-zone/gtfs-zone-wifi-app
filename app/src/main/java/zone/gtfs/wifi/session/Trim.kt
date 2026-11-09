package zone.gtfs.wifi.session

import zone.gtfs.wifi.Config

/** A recorded point, as the trim sees it. */
interface Located {
    val t: Long
    val lat: Double
    val lon: Double
}

private val Located.pos get() = LatLon(lat, lon)

/**
 * Trip samples between the boarding and alighting stops, plus [radius]
 * around each.
 *
 * The window runs from the first sample near the boarding stop to the last
 * sample near the alighting stop (the session start or end when a stop is
 * never reached). Inside it, a sample is kept only within [radius] of the
 * trip line between the two stops. Without a line, both stops must have
 * been reached, and the window is kept as is.
 */
fun <T : Located> trimTrip(
    samples: List<T>,
    boarding: LatLon,
    alighting: LatLon,
    line: List<LatLon>?,
    radius: Double = Config.TRIM_RADIUS_M,
): List<T> {
    if (samples.isEmpty()) return samples
    val sorted = samples.sortedBy { it.t }
    val first = sorted.indexOfFirst { haversineM(it.pos, boarding) <= radius }
    val last = sorted.indexOfLast { haversineM(it.pos, alighting) <= radius }
    if (line.isNullOrEmpty()) {
        if (first < 0 || last < first) return emptyList()
        return sorted.subList(first, last + 1)
    }
    val start = if (first < 0) 0 else first
    val end = if (last < start) sorted.lastIndex else last
    val part = subLine(line, nearestOnLine(line, boarding).first, nearestOnLine(line, alighting).first)
    return sorted.subList(start, end + 1).filter { nearestOnLine(part, it.pos).second <= radius }
}

/** Station samples within [radius] of the stop. */
fun <T : Located> trimStation(
    samples: List<T>,
    stop: LatLon,
    radius: Double = Config.TRIM_RADIUS_M,
): List<T> = samples.filter { haversineM(it.pos, stop) <= radius }.sortedBy { it.t }

/** Items whose time is within [slack] of the kept samples' time range. */
fun <T> trimToWindow(items: List<T>, kept: List<Located>, slack: Long, time: (T) -> Long): List<T> {
    if (kept.isEmpty()) return emptyList()
    val from = kept.minOf { it.t } - slack
    val to = kept.maxOf { it.t } + slack
    return items.filter { time(it) in from..to }.sortedBy(time)
}
