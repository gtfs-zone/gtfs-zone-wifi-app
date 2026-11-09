package zone.gtfs.wifi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import zone.gtfs.wifi.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App()
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val PERMISSIONS = "permissions"
    const val TRIP = "trip"
    const val TRIP_STEP = "trip/{step}"
    const val STATION = "station"
    const val SESSION = "session/{id}"
    const val SETTINGS = "settings"
    fun session(id: String) = "session/$id"
    fun trip(step: TripStep) = "trip/${step.name}"
}

@Composable
private fun NavHostController.tripEntry(current: NavBackStackEntry): NavBackStackEntry =
    remember(current) { getBackStackEntry(Routes.TRIP) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val atHome = entry?.destination?.route == Routes.HOME
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (!atHome) IconButton(onClick = { nav.popBackStack() }) { Text("<") }
                },
                actions = {
                    if (atHome) TextButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Text(stringResource(R.string.sessions)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel(),
                        onPermissions = { nav.navigate(Routes.PERMISSIONS) },
                        onStartTrip = { nav.navigate(Routes.trip(TripStep.STOP)) },
                        onStartStation = { nav.navigate(Routes.STATION) },
                        onOpen = { nav.navigate(Routes.session(it)) },
                    )
                }
                composable(Routes.PERMISSIONS) { PermissionsScreen { nav.popBackStack() } }
                // The trip steps share view models scoped to this graph.
                navigation(startDestination = Routes.TRIP_STEP, route = Routes.TRIP) {
                    composable(
                        Routes.TRIP_STEP,
                        arguments = listOf(navArgument("step") { defaultValue = TripStep.STOP.name }),
                    ) { e ->
                        val parent = nav.tripEntry(e)
                        val step = TripStep.valueOf(e.arguments?.getString("step") ?: TripStep.STOP.name)
                        TripPickScreen(
                            viewModel(parent),
                            viewModel(parent),
                            step,
                            onNext = { nav.navigate(Routes.trip(it)) },
                            onStarted = { id ->
                                nav.navigate(Routes.session(id)) { popUpTo(Routes.HOME) }
                            },
                        )
                    }
                }
                composable(Routes.STATION) {
                    val vm: StopsViewModel = viewModel()
                    NearbyStops(vm) { stop ->
                        vm.startStation(stop) { id -> nav.navigate(Routes.session(id)) { popUpTo(Routes.HOME) } }
                    }
                }
                composable(Routes.SESSION) { e ->
                    val id = e.arguments?.getString("id") ?: return@composable
                    val home: HomeViewModel = viewModel()
                    SessionScreen(
                        viewModel(key = id) { SessionViewModel(get(androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY)!!, id) },
                        onSubmitted = { nav.popBackStack(Routes.HOME, false) },
                        onDelete = {
                            home.delete(id)
                            nav.popBackStack(Routes.HOME, false)
                        },
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen(viewModel()) { nav.navigate(Routes.session(it)) } }
            }
        }
    }
}
