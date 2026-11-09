package zone.gtfs.wifi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zone.gtfs.wifi.R
import zone.gtfs.wifi.data.NetTestEntity
import zone.gtfs.wifi.data.SampleEntity
import zone.gtfs.wifi.data.SessionState
import zone.gtfs.wifi.session.Reports
import zone.gtfs.wifi.session.StationChecklist

private val yesNo = listOf(true, false)

@Composable
private fun yesNoNames(): (Boolean) -> String {
    val yes = stringResource(R.string.yes)
    val no = stringResource(R.string.no)
    return { if (it) yes else no }
}

@Composable
fun SessionScreen(vm: SessionViewModel, onSubmitted: () -> Unit, onDelete: () -> Unit) {
    val session by vm.session.collectAsState()
    val count by vm.sampleCount.collectAsState()
    val sample by vm.lastSample.collectAsState()
    val test by vm.lastNetTest.collectAsState()
    val s = session ?: return
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.title, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.started_at, dateTime(s.startedAt)), style = MaterialTheme.typography.bodySmall)
        if (s.state == SessionState.RECORDING || s.state == SessionState.ENDED) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.samples_count, count))
                    Text(sampleLine(sample))
                    Text(netTestLine(test))
                }
            }
        }
        val editable = s.state == SessionState.RECORDING || s.state == SessionState.ENDED
        if (s.kind == "trip") ReportsForm(vm, editable) else ChecklistForm(vm, editable)
        when (s.state) {
            SessionState.RECORDING -> Button(onClick = vm::end, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.end_session))
            }
            SessionState.ENDED -> {
                Text(stringResource(R.string.submit_hint), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.submit(onSubmitted) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.submit))
                }
            }
            else -> Text(stringResource(R.string.state_line, s.state.name.lowercase(), s.keptSamples ?: 0, s.keptNetTests ?: 0))
        }
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.delete_session))
        }
    }
}

@Composable
private fun sampleLine(s: SampleEntity?): String = when {
    s == null -> stringResource(R.string.no_sample_yet)
    else -> stringResource(
        R.string.sample_line,
        if (s.wifiConnected) (s.wifiSsid ?: "?") + " " + (s.wifiRssi?.let { "$it dBm" } ?: "") else "-",
        s.cellTech?.let { it + " " + (s.cellDbm?.let { d -> "$d dBm" } ?: "") } ?: "-",
        s.acc.toInt(),
    )
}

@Composable
private fun netTestLine(t: NetTestEntity?): String = when {
    t == null -> stringResource(R.string.no_test_yet)
    t.reachable -> stringResource(R.string.test_ok, t.transport, t.latencyMs?.toInt() ?: 0, t.fetchMs?.toInt()?.toString() ?: "-")
    t.captivePortal == true -> stringResource(R.string.test_portal, t.transport)
    else -> stringResource(R.string.test_unreachable, t.transport)
}

@Composable
private fun ReportsForm(vm: SessionViewModel, editable: Boolean) {
    val r by vm.reports.collectAsState()
    val yn = yesNoNames()
    fun set(change: (Reports) -> Reports) { if (editable) vm.setReports(change) }
    Column {
        Text(stringResource(R.string.reports), style = MaterialTheme.typography.titleMedium)
        ChoiceRow(stringResource(R.string.report_outlets), Reports.OUTLETS, r.outlets, { it }) { v -> set { it.copy(outlets = v) } }
        ChoiceRow(stringResource(R.string.report_wifi), Reports.WIFI, r.wifi, { it }) { v -> set { it.copy(wifi = v) } }
        ChoiceRow(stringResource(R.string.report_portal), Reports.PORTAL, r.portal, { it }) { v -> set { it.copy(portal = v) } }
        ChoiceRow(stringResource(R.string.report_data_cap), yesNo, r.dataCap, yn) { v -> set { it.copy(dataCap = v) } }
        ChoiceRow(stringResource(R.string.report_vpn_blocked), yesNo, r.vpnBlocked, yn) { v -> set { it.copy(vpnBlocked = v) } }
    }
}

@Composable
private fun ChecklistForm(vm: SessionViewModel, editable: Boolean) {
    val c by vm.checklist.collectAsState()
    val yn = yesNoNames()
    fun set(change: (StationChecklist) -> StationChecklist) { if (editable) vm.setChecklist(change) }
    @Composable
    fun row(label: Int, value: Boolean?, change: StationChecklist.(Boolean?) -> StationChecklist) =
        ChoiceRow(stringResource(label), yesNo, value, yn) { v -> set { it.change(v) } }
    Column {
        Text(stringResource(R.string.checklist), style = MaterialTheme.typography.titleMedium)
        row(R.string.check_email, c.neededEmail) { copy(neededEmail = it) }
        row(R.string.check_account, c.neededAccount) { copy(neededAccount = it) }
        row(R.string.check_sms, c.neededSms) { copy(neededSms = it) }
        row(R.string.check_checkbox, c.neededCheckbox) { copy(neededCheckbox = it) }
        row(R.string.check_password, c.neededPassword) { copy(neededPassword = it) }
        row(R.string.check_payment, c.neededPayment) { copy(neededPayment = it) }
        row(R.string.check_could_not_connect, c.couldNotConnect) { copy(couldNotConnect = it) }
    }
}
