package zone.gtfs.wifi.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import zone.gtfs.wifi.R

/** A runtime permission group the app asks for, with why. */
data class PermissionGroup(val permissions: List<String>, val title: Int, val reason: Int)

val permissionGroups: List<PermissionGroup> = buildList {
    add(
        PermissionGroup(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            R.string.perm_location,
            R.string.perm_location_reason,
        ),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(PermissionGroup(listOf(Manifest.permission.NEARBY_WIFI_DEVICES), R.string.perm_wifi, R.string.perm_wifi_reason))
        add(
            PermissionGroup(
                listOf(Manifest.permission.POST_NOTIFICATIONS),
                R.string.perm_notifications,
                R.string.perm_notifications_reason,
            ),
        )
    }
}

fun Context.granted(group: PermissionGroup) =
    group.permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

fun Context.locationEnabled() = LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java))

fun Context.readyToRecord() = permissionGroups.all { granted(it) } && locationEnabled()

/** Re-reads permission state whenever the screen resumes. */
@Composable
fun rememberPermissionTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return tick
}

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val resumeTick = rememberPermissionTick()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    // Permission state is not observable; re-read it after a request or resume.
    key(tick, resumeTick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.perm_intro), style = MaterialTheme.typography.bodyMedium)
            for (group in permissionGroups) {
                val ok = context.granted(group)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(group.reason), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (ok) {
                                Text(stringResource(R.string.granted), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Button(onClick = { launcher.launch(group.permissions.toTypedArray()) }) {
                                    Text(stringResource(R.string.grant))
                                }
                                OutlinedButton(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.fromParts("package", context.packageName, null)),
                                    )
                                }) { Text(stringResource(R.string.open_settings)) }
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.perm_service), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.perm_service_reason), style = MaterialTheme.typography.bodySmall)
                    if (!context.locationEnabled()) {
                        Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                            Text(stringResource(R.string.turn_on_location))
                        }
                    }
                }
            }
            Button(onClick = onDone, enabled = context.readyToRecord(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.done))
            }
        }
    }
}
