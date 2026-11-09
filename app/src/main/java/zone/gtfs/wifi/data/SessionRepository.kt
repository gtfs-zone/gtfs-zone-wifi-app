package zone.gtfs.wifi.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import zone.gtfs.wifi.BuildConfig
import zone.gtfs.wifi.session.Place
import zone.gtfs.wifi.session.Reports
import zone.gtfs.wifi.session.StationChecklist
import zone.gtfs.wifi.session.Trip
import zone.gtfs.wifi.session.buildSession
import zone.gtfs.wifi.session.sessionJson
import zone.gtfs.wifi.transitous.LegGeometry

class SessionRepository(private val dao: SessionDao, private val now: () -> Long = System::currentTimeMillis) {
    fun observeAll(): Flow<List<SessionEntity>> = dao.observeAll()
    fun observe(id: String) = dao.observe(id)
    fun observeSampleCount(id: String) = dao.observeSampleCount(id)
    fun observeLastSample(id: String) = dao.observeLastSample(id)
    fun observeLastNetTest(id: String) = dao.observeLastNetTest(id)

    suspend fun get(id: String) = dao.get(id)
    suspend fun recording(): SessionEntity? = dao.withState(SessionState.RECORDING).lastOrNull()

    private fun newSession(kind: String, title: String) = SessionEntity(
        id = UUID.randomUUID().toString(),
        kind = kind,
        state = SessionState.RECORDING,
        startedAt = now(),
        endedAt = null,
        title = title,
        tripJson = null,
        stationJson = null,
        shape = null,
        shapePrecision = null,
        reportsJson = null,
        checklistJson = null,
        payload = null,
        keptSamples = null,
        keptNetTests = null,
        sentAt = null,
        lastError = null,
    )

    suspend fun startTrip(trip: Trip, shape: LegGeometry?): SessionEntity {
        val title = "${trip.routeName}: ${trip.boarding.name} to ${trip.alighting.name}"
        val s = newSession("trip", title).copy(
            tripJson = sessionJson.encodeToString(Trip.serializer(), trip),
            shape = shape?.points,
            shapePrecision = shape?.precision,
            reportsJson = sessionJson.encodeToString(Reports.serializer(), Reports.EMPTY),
        )
        dao.insert(s)
        return s
    }

    suspend fun startStation(station: Place): SessionEntity {
        val s = newSession("station", station.name).copy(
            stationJson = sessionJson.encodeToString(Place.serializer(), station),
            checklistJson = sessionJson.encodeToString(StationChecklist.serializer(), StationChecklist.EMPTY),
        )
        dao.insert(s)
        return s
    }

    suspend fun addSample(sample: SampleEntity) = dao.insertSample(sample)
    suspend fun addNetTest(test: NetTestEntity) = dao.insertNetTest(test)

    suspend fun end(id: String) {
        val s = dao.get(id) ?: return
        if (s.state == SessionState.RECORDING) dao.update(s.copy(state = SessionState.ENDED, endedAt = now()))
    }

    suspend fun setReports(id: String, reports: Reports) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(reportsJson = sessionJson.encodeToString(Reports.serializer(), reports)))
    }

    suspend fun setChecklist(id: String, checklist: StationChecklist) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(checklistJson = sessionJson.encodeToString(StationChecklist.serializer(), checklist)))
    }

    /** Trims and builds the upload, then drops the raw rows. */
    suspend fun submit(id: String) {
        val s = dao.get(id) ?: return
        if (s.state != SessionState.ENDED) return
        val built = buildSession(s, dao.samples(id), dao.netTests(id), BuildConfig.VERSION_NAME)
        dao.queue(
            s.copy(
                state = SessionState.QUEUED,
                payload = sessionJson.encodeToString(zone.gtfs.wifi.session.SessionV1.serializer(), built),
                keptSamples = built.samples.size,
                keptNetTests = built.netTests.size,
            ),
        )
    }

    suspend fun queued(): List<SessionEntity> = dao.withState(SessionState.QUEUED)

    suspend fun markSent(id: String) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(state = SessionState.SENT, sentAt = now(), lastError = null))
    }

    suspend fun markRejected(id: String, error: String) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(state = SessionState.REJECTED, lastError = error.take(500)))
    }

    suspend fun noteError(id: String, error: String) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(lastError = error.take(500)))
    }

    suspend fun delete(id: String) = dao.delete(id)
}
