package zone.gtfs.wifi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import zone.gtfs.wifi.R
import zone.gtfs.wifi.transitous.MapStop

private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val dateClock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

fun clockTime(iso: String?): String = iso?.let { runCatching { clock.format(Instant.parse(it)) }.getOrNull() } ?: "--:--"
fun dateTime(epochMs: Long): String = dateClock.format(Instant.ofEpochMilli(epochMs))

@Composable
fun <T> LoadableView(state: Loadable<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        state.value != null -> content(state.value)
    }
}

@Composable
fun ListRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
fun NearbyStops(vm: StopsViewModel, onPick: (MapStop) -> Unit) {
    val state by vm.stops.collectAsState()
    LaunchedEffect(Unit) { if (state.value == null && !state.loading) vm.load() }
    LoadableView(state, vm::load) { stops ->
        if (stops.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.no_stops))
                Button(onClick = vm::load) { Text(stringResource(R.string.retry)) }
            }
        } else {
            LazyColumn {
                items(stops, key = { it.stopId }) { s ->
                    ListRow(s.name, s.modes.joinToString(", ") { it.lowercase().replace('_', ' ') }) { onPick(s) }
                }
            }
        }
    }
}

/** One choice out of [options], or none (null). */
@Composable
fun <T> ChoiceRow(label: String, options: List<T>, selected: T?, name: (T) -> String, onSelect: (T?) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (o in options) {
                FilterChip(
                    selected = o == selected,
                    onClick = { onSelect(if (o == selected) null else o) },
                    label = { Text(name(o)) },
                )
            }
        }
    }
}

