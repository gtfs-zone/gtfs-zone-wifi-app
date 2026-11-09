package zone.gtfs.wifi.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import zone.gtfs.wifi.session.Located

enum class SessionState {
    /** The foreground service is sampling. */
    RECORDING,

    /** Sampling stopped; reports can still be edited before submitting. */
    ENDED,

    /** Trimmed and built; waiting for the upload worker. */
    QUEUED,
    SENT,

    /** Ingest rejected it; kept so the rider can see and delete it. */
    REJECTED,
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val state: SessionState,
    val startedAt: Long,
    val endedAt: Long?,
    /** Short label for lists: route and stops, or the station name. */
    val title: String,
    /** session.v1 `trip` as JSON, for trip sessions. */
    val tripJson: String?,
    /** session.v1 `station` as JSON, for station sessions. */
    val stationJson: String?,
    /** Encoded polyline of the whole trip, from Transitous `legGeometry`. */
    val shape: String?,
    val shapePrecision: Int?,
    val reportsJson: String?,
    val checklistJson: String?,
    /** The built session JSON, set when queued; raw rows are deleted then. */
    val payload: String?,
    val keptSamples: Int?,
    val keptNetTests: Int?,
    val sentAt: Long?,
    val lastError: String?,
)

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val sessionId: String,
    override val t: Long,
    override val lat: Double,
    override val lon: Double,
    val acc: Double,
    val speed: Double?,
    val wifiConnected: Boolean,
    val wifiSsid: String?,
    val wifiBssid: String?,
    val wifiRssi: Int?,
    val wifiLinkMbps: Int?,
    val wifiFrequency: Int?,
    val cellTech: String?,
    val cellMcc: String?,
    val cellMnc: String?,
    val cellDbm: Int?,
    val cellLevel: Int?,
) : Located

@Entity(
    tableName = "net_tests",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class NetTestEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val sessionId: String,
    val t: Long,
    val transport: String,
    val reachable: Boolean,
    val captivePortal: Boolean?,
    val latencyMs: Double?,
    val fetchBytes: Long?,
    val fetchMs: Double?,
)
