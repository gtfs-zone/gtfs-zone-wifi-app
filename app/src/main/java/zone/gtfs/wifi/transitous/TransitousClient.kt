package zone.gtfs.wifi.transitous

import java.io.IOException
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import zone.gtfs.wifi.session.LatLon
import zone.gtfs.wifi.session.haversineM

class TransitousClient(private val http: OkHttpClient, baseUrl: String) {
    private val base = baseUrl.toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(path: String, query: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val url: HttpUrl = base.newBuilder().addPathSegments(path).apply {
                query.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Transitous ${resp.code} for $path")
                resp.body.string()
            }
        }

    /** Stops within [radiusM] of [at], one per name, nearest first. */
    suspend fun nearbyStops(at: LatLon, radiusM: Double): List<MapStop> {
        val dLat = radiusM / 111_320.0
        val dLon = dLat / cos(Math.toRadians(at.lat)).coerceAtLeast(0.01)
        val body = get(
            "api/v6/map/stops",
            mapOf(
                "min" to "${at.lat - dLat},${at.lon - dLon}",
                "max" to "${at.lat + dLat},${at.lon + dLon}",
            ),
        )
        return dedupeStops(json.decodeFromString<List<MapStop>>(body), at, radiusM)
    }

    suspend fun departures(stopId: String, count: Int = 30): List<StopTime> {
        val body = get("api/v6/stoptimes", mapOf("stopId" to stopId, "n" to "$count"))
        return json.decodeFromString<StopTimesResponse>(body).stopTimes
            .filter { !it.cancelled && !it.tripCancelled && it.place.pickupType != "NOT_ALLOWED" }
    }

    suspend fun trip(tripId: String): Leg {
        val body = get("api/v6/trip", mapOf("tripId" to tripId))
        return json.decodeFromString<TripResponse>(body).legs.firstOrNull()
            ?: throw IOException("Transitous returned no leg for $tripId")
    }
}

/**
 * Transitous lists the same station once per feed; departures merge
 * equivalent stops, so one entry per name is enough. Parent stations win
 * over platforms.
 */
fun dedupeStops(stops: List<MapStop>, at: LatLon, radiusM: Double): List<MapStop> =
    stops
        .map { it to haversineM(at, LatLon(it.lat, it.lon)) }
        .filter { it.second <= radiusM }
        .groupBy { it.first.name.trim().lowercase() }
        .values
        .map { group ->
            group.minWith(compareBy({ it.first.parentId != null }, { it.second }))
        }
        .sortedBy { it.second }
        .map { it.first }
