package zone.gtfs.wifi.transitous

import zone.gtfs.wifi.session.LatLon
import zone.gtfs.wifi.session.Place
import zone.gtfs.wifi.session.Trip
import zone.gtfs.wifi.session.TripStop
import zone.gtfs.wifi.session.haversineM
import zone.gtfs.wifi.session.timestamp

/** Index in [Leg.stops] of the departure's stop: by id, then parent, then nearest. */
fun boardingIndex(leg: Leg, departure: StopTime): Int {
    val stops = leg.stops
    val place = departure.place
    stops.indexOfFirst { it.stopId != null && it.stopId == place.stopId }.let { if (it >= 0) return it }
    stops.indexOfFirst { it.parentId != null && it.parentId == place.parentId }.let { if (it >= 0) return it }
    val at = LatLon(place.lat, place.lon)
    return stops.indices.minBy { haversineM(at, LatLon(stops[it].lat, stops[it].lon)) }
}

/** Stops the rider can alight at after boarding. */
fun alightingOptions(leg: Leg, departure: StopTime): List<StopPlace> =
    leg.stops.drop(boardingIndex(leg, departure) + 1).filter { it.dropoffType != "NOT_ALLOWED" }

private fun StopPlace.toTripStop(time: String?): TripStop {
    val id = stopId ?: error("Transitous stop $name has no stopId")
    return TripStop(
        transitousStopId = id,
        transitousParentId = parentId,
        gtfsStopId = gtfsStopId(id),
        name = name.take(256),
        lat = lat,
        lon = lon,
        scheduledTime = timestamp(time ?: error("Transitous stop $name has no scheduled time")),
    )
}

fun MapStop.toPlace() = Place(
    transitousStopId = stopId,
    transitousParentId = parentId,
    gtfsStopId = gtfsStopId(stopId),
    name = name.take(256),
    lat = lat,
    lon = lon,
)

private fun String?.clean(max: Int) = this?.trim()?.takeIf { it.isNotEmpty() }?.take(max)

fun buildTrip(
    departure: StopTime,
    alighting: StopPlace,
    carNumber: String?,
    travelClass: String?,
): Trip = Trip(
    transitousTripId = departure.tripId,
    transitousRouteId = departure.routeId,
    transitousSource = departure.source.clean(512),
    gtfs = gtfsIds(departure.tripId, departure.routeId, departure.agencyId),
    routeName = departure.routeName.take(256),
    routeType = departure.routeType?.takeIf { it >= 0 },
    mode = departure.mode.clean(64),
    agencyName = departure.agencyName.clean(256),
    headsign = departure.headsign.clean(256),
    tripShortName = departure.tripShortName.clean(64),
    boarding = departure.place.toTripStop(
        departure.place.scheduledDeparture ?: departure.place.departure,
    ),
    alighting = alighting.toTripStop(alighting.scheduledArrival ?: alighting.arrival),
    carNumber = carNumber.clean(32),
    travelClass = travelClass.clean(32),
)
