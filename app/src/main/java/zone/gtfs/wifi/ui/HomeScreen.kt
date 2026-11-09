package zone.gtfs.wifi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zone.gtfs.wifi.BuildConfig
import zone.gtfs.wifi.R
import zone.gtfs.wifi.data.SessionEntity
import zone.gtfs.wifi.data.SessionState

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onPermissions: () -> Unit,
    onStartTrip: () -> Unit,
    onStartStation: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    val tick = rememberPermissionTick()
    val open = sessions.filter { it.state == SessionState.RECORDING || it.state == SessionState.ENDED }
    key(tick) {
        val ready = context.readyToRecord()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!ready) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.perm_missing))
                        Button(onClick = onPermissions) { Text(stringResource(R.string.set_up_permissions)) }
                    }
                }
            }
            val recording = open.any { it.state == SessionState.RECORDING }
            Button(onClick = onStartTrip, enabled = ready && !recording, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.start_trip))
            }
            OutlinedButton(onClick = onStartStation, enabled = ready && !recording, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.at_station))
            }
            if (open.isNotEmpty()) Text(stringResource(R.string.open_sessions), style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(open, key = { it.id }) { s -> ListRow(s.title, stateLabel(s)) { onOpen(s.id) } }
            }
        }
    }
}

@Composable
fun stateLabel(s: SessionEntity): String = when (s.state) {
    SessionState.RECORDING -> stringResource(R.string.state_recording)
    SessionState.ENDED -> stringResource(R.string.state_ended)
    SessionState.QUEUED -> stringResource(R.string.state_queued) + (s.lastError?.let { " ($it)" } ?: "")
    SessionState.SENT -> stringResource(R.string.state_sent)
    SessionState.REJECTED -> stringResource(R.string.state_rejected) + (s.lastError?.let { " ($it)" } ?: "")
} + ", " + dateTime(s.startedAt)

@Composable
fun SettingsScreen(vm: HomeViewModel, onOpen: (String) -> Unit) {
    val sessions by vm.sessions.collectAsState()
    val done = sessions.filter { it.state != SessionState.RECORDING && it.state != SessionState.ENDED }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = vm::uploadNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.upload_now)) }
        Text(stringResource(R.string.upload_hint), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.queued_sent, done.count { it.state == SessionState.QUEUED }, done.count { it.state == SessionState.SENT }),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(done, key = { it.id }) { s ->
                ListRow(s.title, stateLabel(s)) { onOpen(s.id) }
                TextButton(onClick = { vm.delete(s.id) }) { Text(stringResource(R.string.delete_session)) }
            }
        }
        Text(stringResource(R.string.about, BuildConfig.VERSION_NAME, BuildConfig.INGEST_URL), style = MaterialTheme.typography.bodySmall)
    }
}
