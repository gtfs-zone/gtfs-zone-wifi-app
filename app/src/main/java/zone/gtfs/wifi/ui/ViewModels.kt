package zone.gtfs.wifi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zone.gtfs.wifi.Config
import zone.gtfs.wifi.WifiApp
import zone.gtfs.wifi.data.SessionEntity
import zone.gtfs.wifi.data.SessionState
import zone.gtfs.wifi.record.RecordingService
import zone.gtfs.wifi.session.Reports
import zone.gtfs.wifi.session.StationChecklist
import zone.gtfs.wifi.session.sessionJson
import zone.gtfs.wifi.transitous.Leg
import zone.gtfs.wifi.transitous.MapStop
import zone.gtfs.wifi.transitous.StopPlace
import zone.gtfs.wifi.transitous.StopTime
import zone.gtfs.wifi.transitous.alightingOptions
import zone.gtfs.wifi.transitous.buildTrip
import zone.gtfs.wifi.transitous.toPlace
import zone.gtfs.wifi.upload.Uploads

private val Application.wifi get() = this as WifiApp

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val sessions: StateFlow<List<SessionEntity>> =
        app.wifi.sessions.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun uploadNow() = Uploads.now(getApplication())

    fun delete(id: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            if (app.wifi.sessions.get(id)?.state == SessionState.RECORDING) {
                app.wifi.sessions.end(id)
                RecordingService.stop(app)
            }
            app.wifi.sessions.delete(id)
        }
    }
}

/** Loading state of one step. */
data class Loadable<T>(val loading: Boolean = false, val value: T? = null, val error: String? = null)

class StopsViewModel(app: Application) : AndroidViewModel(app) {
    private val _stops = MutableStateFlow(Loadable<List<MapStop>>())
    val stops = _stops.asStateFlow()

    fun load() {
        val app = getApplication<Application>()
        _stops.value = Loadable(loading = true)
        viewModelScope.launch {
            _stops.value = try {
                val at = currentLocation(app) ?: error("No location fix yet")
                Loadable(value = app.wifi.transitous.nearbyStops(at, Config.NEARBY_STOPS_RADIUS_M))
            } catch (e: Exception) {
                Loadable(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Starts a station session at [stop]; returns its id through [onStarted]. */
    fun startStation(stop: MapStop, onStarted: (String) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val s = app.wifi.sessions.startStation(stop.toPlace())
            RecordingService.start(app, s.id)
            onStarted(s.id)
        }
    }
}

class TripPickViewModel(app: Application) : AndroidViewModel(app) {
    private val _departures = MutableStateFlow(Loadable<List<StopTime>>())
    val departures = _departures.asStateFlow()

    private val _leg = MutableStateFlow(Loadable<Leg>())
    val leg = _leg.asStateFlow()

    var stop: MapStop? = null
        private set
    var departure: StopTime? = null
        private set

    fun pickStop(s: MapStop) {
        stop = s
        _departures.value = Loadable(loading = true)
        viewModelScope.launch {
            _departures.value = try {
                Loadable(value = getApplication<Application>().wifi.transitous.departures(s.stopId))
            } catch (e: Exception) {
                Loadable(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun pickDeparture(d: StopTime) {
        departure = d
        _leg.value = Loadable(loading = true)
        viewModelScope.launch {
            _leg.value = try {
                Loadable(value = getApplication<Application>().wifi.transitous.trip(d.tripId))
            } catch (e: Exception) {
                Loadable(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun alightingOptions(): List<StopPlace> {
        val l = _leg.value.value ?: return emptyList()
        val d = departure ?: return emptyList()
        return alightingOptions(l, d)
    }

    fun start(alighting: StopPlace, carNumber: String, travelClass: String, onStarted: (String) -> Unit) {
        val app = getApplication<Application>()
        val d = departure ?: return
        val l = _leg.value.value ?: return
        viewModelScope.launch {
            val trip = buildTrip(d, alighting, carNumber, travelClass)
            val s = app.wifi.sessions.startTrip(trip, l.legGeometry)
            RecordingService.start(app, s.id)
            onStarted(s.id)
        }
    }
}

class SessionViewModel(app: Application, val id: String) : AndroidViewModel(app) {
    private val repo = app.wifi.sessions
    private fun <T> Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val session = repo.observe(id).state(null)
    val sampleCount = repo.observeSampleCount(id).state(0)
    val lastSample = repo.observeLastSample(id).state(null)
    val lastNetTest = repo.observeLastNetTest(id).state(null)

    private val _reports = MutableStateFlow(Reports.EMPTY)
    val reports = _reports.asStateFlow()
    private val _checklist = MutableStateFlow(StationChecklist.EMPTY)
    val checklist = _checklist.asStateFlow()

    init {
        viewModelScope.launch {
            val s = repo.get(id) ?: return@launch
            s.reportsJson?.let { _reports.value = sessionJson.decodeFromString(it) }
            s.checklistJson?.let { _checklist.value = sessionJson.decodeFromString(it) }
        }
    }

    fun setReports(change: (Reports) -> Reports) {
        _reports.update(change)
        val r = _reports.value
        getApplication<Application>().wifi.scope.launch { repo.setReports(id, r) }
    }

    fun setChecklist(change: (StationChecklist) -> StationChecklist) {
        _checklist.update(change)
        val c = _checklist.value
        getApplication<Application>().wifi.scope.launch { repo.setChecklist(id, c) }
    }

    fun end() {
        val app = getApplication<Application>()
        app.wifi.scope.launch {
            repo.end(id)
            RecordingService.stop(app)
        }
    }

    fun submit(onDone: () -> Unit) {
        val app = getApplication<Application>()
        app.wifi.scope.launch {
            if (repo.get(id)?.kind == "trip") {
                repo.setReports(id, _reports.value)
            } else {
                repo.setChecklist(id, _checklist.value)
            }
            repo.submit(id)
            Uploads.schedule(app)
            launch(Dispatchers.Main) { onDone() }
        }
    }
}
