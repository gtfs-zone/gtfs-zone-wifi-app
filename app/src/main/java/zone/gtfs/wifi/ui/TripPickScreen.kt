package zone.gtfs.wifi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zone.gtfs.wifi.R
import zone.gtfs.wifi.transitous.StopPlace
import zone.gtfs.wifi.transitous.StopTime

enum class TripStep { STOP, DEPARTURE, ALIGHTING }

@Composable
fun TripPickScreen(stopsVm: StopsViewModel, vm: TripPickViewModel, step: TripStep, onNext: (TripStep) -> Unit, onStarted: (String) -> Unit) {
    when (step) {
        TripStep.STOP -> NearbyStops(stopsVm) {
            vm.pickStop(it)
            onNext(TripStep.DEPARTURE)
        }
        TripStep.DEPARTURE -> Departures(vm) {
            vm.pickDeparture(it)
            onNext(TripStep.ALIGHTING)
        }
        TripStep.ALIGHTING -> Alighting(vm, onStarted)
    }
}

private fun StopTime.label() = listOfNotNull(clockTime(place.scheduledDeparture ?: place.departure), routeName, headsign)
    .joinToString("  ")

@Composable
private fun Departures(vm: TripPickViewModel, onPick: (StopTime) -> Unit) {
    val state by vm.departures.collectAsState()
    LoadableView(state, { vm.stop?.let(vm::pickStop) }) { list ->
        if (list.isEmpty()) {
            Text(stringResource(R.string.no_departures), Modifier.padding(16.dp))
        } else {
            LazyColumn {
                itemsIndexed(list) { _, d ->
                    val track = d.place.track?.let { stringResource(R.string.track, it) }
                    ListRow(d.label(), listOfNotNull(d.agencyName, d.place.name, track).joinToString(", ")) { onPick(d) }
                }
            }
        }
    }
}

@Composable
private fun Alighting(vm: TripPickViewModel, onStarted: (String) -> Unit) {
    val state by vm.leg.collectAsState()
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    var car by rememberSaveable { mutableStateOf("") }
    var travelClass by rememberSaveable { mutableStateOf("") }
    LoadableView(state, { vm.departure?.let(vm::pickDeparture) }) { _ ->
        val options: List<StopPlace> = vm.alightingOptions()
        Column {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(vm.departure?.label().orEmpty(), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(car, { car = it.take(32) }, label = { Text(stringResource(R.string.car_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(travelClass, { travelClass = it.take(32) }, label = { Text(stringResource(R.string.travel_class)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { picked?.let { vm.start(options[it], car, travelClass, onStarted) } },
                enabled = picked != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.start_trip)) }
            Text(stringResource(R.string.pick_alighting), style = MaterialTheme.typography.labelLarge)
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(options) { i, s ->
                val mark = if (picked == i) "> " else ""
                ListRow(mark + s.name, clockTime(s.scheduledArrival ?: s.arrival)) { picked = i }
            }
        }
        }
    }
}
