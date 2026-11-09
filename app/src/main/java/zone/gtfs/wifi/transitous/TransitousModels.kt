package zone.gtfs.wifi.transitous

import kotlinx.serialization.Serializable

// The parts of the Transitous v6 API responses the app reads.

@Serializable
data class MapStop(
    val name: String,
    val stopId: String,
    val parentId: String? = null,
    val lat: Double,
    val lon: Double,
    val importance: Double? = null,
    val modes: List<String> = emptyList(),
)

@Serializable
data class StopPlace(
    val name: String,
    val stopId: String? = null,
    val parentId: String? = null,
    val lat: Double,
    val lon: Double,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val track: String? = null,
    val pickupType: String? = null,
    val dropoffType: String? = null,
    val cancelled: Boolean = false,
)

@Serializable
data class StopTime(
    val place: StopPlace,
    val mode: String? = null,
    val headsign: String? = null,
    val agencyId: String? = null,
    val agencyName: String? = null,
    val routeId: String,
    val tripId: String,
    val routeType: Int? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val tripShortName: String? = null,
    val displayName: String? = null,
    val source: String? = null,
    val cancelled: Boolean = false,
    val tripCancelled: Boolean = false,
) {
    val routeName: String
        get() = listOf(displayName, routeShortName, routeLongName, tripShortName)
            .firstOrNull { !it.isNullOrBlank() } ?: routeId
}

@Serializable
data class StopTimesResponse(val stopTimes: List<StopTime> = emptyList())

@Serializable
data class LegGeometry(val points: String, val precision: Int)

@Serializable
data class Leg(
    val from: StopPlace,
    val to: StopPlace,
    val intermediateStops: List<StopPlace> = emptyList(),
    val legGeometry: LegGeometry? = null,
    val tripId: String? = null,
) {
    val stops: List<StopPlace> get() = listOf(from) + intermediateStops + listOf(to)
}

@Serializable
data class TripResponse(val legs: List<Leg> = emptyList())
