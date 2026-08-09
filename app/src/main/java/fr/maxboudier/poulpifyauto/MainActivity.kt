package fr.maxboudier.poulpifyauto

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.maxboudier.poulpifyauto.ui.DashboardScreen
import fr.maxboudier.poulpifyauto.ui.DiagnosticsScreen
import fr.maxboudier.poulpifyauto.ui.LibraryScreen
import fr.maxboudier.poulpifyauto.ui.PoulpifyViewModel
import fr.maxboudier.poulpifyauto.ui.SettingsScreen
import fr.maxboudier.poulpifyauto.core.session.PoulpifyGraph
import fr.maxboudier.poulpifyauto.ui.theme.PoulpifyTheme

private enum class Tab(val label: String) {
    PLAYER("Lecture"), LIBRARY("Ajouter"), DIAGNOSTICS("Diagnostic"), SETTINGS("Réglages")
}

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // La session hote s'ouvre aussi hors voiture, des que l'app est lancee.
        CarConnectionObserver(this).observe(this)

        setContent {
            PoulpifyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PoulpifyApp()
                }
            }
        }
    }

    /**
     * La toute premiere autorisation Spotify exige une activite a l'ecran pour
     * afficher sa boite de dialogue. On la prete au controleur tant qu'on est
     * visible, et on la reprend ensuite pour ne pas fuiter l'activite.
     */
    override fun onStart() {
        super.onStart()
        PoulpifyGraph.remote.attachActivity(this)
    }

    override fun onStop() {
        PoulpifyGraph.remote.attachActivity(null)
        super.onStop()
    }
}

@Composable
private fun PoulpifyApp(viewModel: PoulpifyViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(Tab.PLAYER) }
    val snackbarHostState = remember { SnackbarHostState() }

    // La session hote suit le cycle de vie de l'ecran : ouverte a l'affichage,
    // relachee a la fermeture. Le service media garde sa propre reference.
    LaunchedEffect(Unit) { viewModel.startSession() }

    // Les erreurs remontent visuellement au lieu de finir dans un Log.e.
    LaunchedEffect(state.lastError?.at) {
        state.lastError?.let { error ->
            snackbarHostState.showSnackbar(error.message)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                when (entry) {
                                    Tab.PLAYER -> Icons.Default.PlayCircle
                                    Tab.LIBRARY -> Icons.Default.LibraryMusic
                                    Tab.DIAGNOSTICS -> Icons.Default.Build
                                    Tab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = entry.label,
                            )
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (tab) {
                Tab.PLAYER -> DashboardScreen(
                    state = state,
                    positionProvider = viewModel::currentPositionMs,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.skipNext() },
                    onPrevious = { viewModel.skipPrevious() },
                    onToggleLock = { viewModel.toggleQueueLock() },
                )

                Tab.LIBRARY -> LibraryScreen(
                    state = state,
                    searchResults = searchResults,
                    searching = searching,
                    onSearch = { viewModel.search(it) },
                    onAddToQueue = { viewModel.addToQueue(it) },
                )

                Tab.DIAGNOSTICS -> DiagnosticsScreen(
                    state = state,
                    config = config,
                    onRetry = { viewModel.retry() },
                )

                Tab.SETTINGS -> SettingsScreen(
                    config = config,
                    onSaveServerUrl = { viewModel.saveServerUrl(it) },
                    onSavePassword = { viewModel.saveHostPassword(it) },
                    onSaveProfile = { name, emoji -> viewModel.saveDriverProfile(name, emoji) },
                    onAutoStartChanged = { viewModel.setAutoStart(it) },
                    onDisableAutoDisconnectChanged = { viewModel.setDisableServerAutoDisconnect(it) },
                    onRetry = { viewModel.retry() },
                )
            }
        }
    }
}
