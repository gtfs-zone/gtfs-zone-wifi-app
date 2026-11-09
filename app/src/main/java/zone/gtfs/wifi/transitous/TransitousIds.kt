package zone.gtfs.wifi.transitous

import zone.gtfs.wifi.session.GtfsIds

// GTFS ids derived from Transitous ids, as described in
// gtfs-zone-wifi-ingest schemas/README.md. Null when an id does not parse.

private val TRIP_ID = Regex("""^(\d{8})_\d{2}:\d{2}_([^_]+)_(.+)$""")

/** Splits `<feed>_<gtfs id>` at the first underscore. */
fun splitFeedId(id: String?): Pair<String, String>? {
    if (id == null) return null
    val i = id.indexOf('_')
    if (i <= 0 || i == id.lastIndex) return null
    return Pair(id.substring(0, i), id.substring(i + 1))
}

fun gtfsIds(tripId: String, routeId: String, agencyId: String?): GtfsIds {
    val trip = TRIP_ID.matchEntire(tripId)?.groupValues
    val route = splitFeedId(routeId)
    return GtfsIds(
        feed = trip?.get(2) ?: route?.first,
        tripId = trip?.get(3),
        routeId = route?.second,
        agencyId = agencyId?.takeIf { it.isNotEmpty() },
        serviceDate = trip?.get(1),
    )
}

fun gtfsStopId(stopId: String?): String? = splitFeedId(stopId)?.second
