package fr.maxboudier.poulpifyauto.core.session

import fr.maxboudier.poulpifyauto.core.model.ConnectionState
import fr.maxboudier.poulpifyauto.core.model.RemotePlayback
import fr.maxboudier.poulpifyauto.core.model.RemoteState
import fr.maxboudier.poulpifyauto.core.model.RepeatMode
import fr.maxboudier.poulpifyauto.core.network.HostTokenHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * La position de lecture est interpolée localement entre deux poussées d'App
 * Remote. Ces tests verrouillent le fait qu'elle avance, et surtout qu'elle ne
 * revienne pas en arrière quand l'état global est recalculé pour une raison
 * sans rapport (un passager qui rejoint, la file qui change).
 */
class PlaybackPositionTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.url.encodedPath) {
                    "/api/host/login" ->
                        """{"success":true,"hostToken":"tok","spotifyAuthenticated":true}"""
                    "/api/events" -> return MockResponse.Builder()
                        .setHeader("Content-Type", "text/event-stream").body(": ping\n\n").build()
                    else -> """{"items":[]}"""
                }
                return MockResponse.Builder()
                    .setHeader("Content-Type", "application/json").body(body).build()
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun playback(positionMs: Long, paused: Boolean = false) = RemotePlayback(
        trackUri = "spotify:track:abc",
        trackName = "Fake Song",
        artists = listOf("Fake Artist"),
        albumName = "Fake Album",
        durationMs = 300_000,
        positionMs = positionMs,
        isPaused = paused,
        shuffling = false,
        repeatMode = RepeatMode.OFF,
        canSkipNext = true,
        canSkipPrevious = true,
        canSeek = true,
        imageUriRaw = null,
    )

    /** Horloge pilotee : rend les tests deterministes, sans Thread.sleep. */
    private var clockMs = 0L

    private fun connectedCoordinator(remote: FakeSpotifyRemote): SessionCoordinator {
        val coordinator = SessionCoordinator(
            settings = FakeConfigSource.of(server.url("/").toString(), "secret"),
            remote = remote,
            tokenHolder = HostTokenHolder(),
            scope = scope,
            debugLogging = false,
            elapsedRealtimeMs = { clockMs },
        )
        coordinator.acquire()
        runBlocking {
            withTimeout(5_000) { coordinator.state.first { it.connection == ConnectionState.CONNECTED } }
        }
        return coordinator
    }

    @Test
    fun `la position avance entre deux poussees d App Remote`() {
        val remote = FakeSpotifyRemote(RemoteState.CONNECTED)
        remote.playback.value = playback(positionMs = 10_000)
        val coordinator = connectedCoordinator(remote)

        runBlocking { withTimeout(5_000) { coordinator.state.first { it.nowPlaying?.track != null } } }
        val before = coordinator.currentPositionMs()
        clockMs += 5_000
        val after = coordinator.currentPositionMs()

        assertEquals("la position doit avancer d'exactement le temps ecoule", before + 5_000, after)
    }

    /**
     * Régression : `merge` s'exécute à chaque émission amont. Sans garde, il
     * ré-ancrait la position sur la dernière valeur poussée par App Remote et
     * la barre de progression reculait à chaque changement de file.
     */
    @Test
    fun `un changement d etat sans rapport ne fait pas reculer la position`() {
        val remote = FakeSpotifyRemote(RemoteState.CONNECTED)
        remote.playback.value = playback(positionMs = 10_000)
        val coordinator = connectedCoordinator(remote)

        runBlocking { withTimeout(5_000) { coordinator.state.first { it.nowPlaying?.track != null } } }
        clockMs += 20_000
        val beforeUnrelatedChange = coordinator.currentPositionMs()

        // Recalcul de l'etat global declenche par autre chose que la lecture.
        coordinator.refreshLibrary()
        Thread.sleep(300)

        val afterUnrelatedChange = coordinator.currentPositionMs()
        assertEquals(
            "la position a recule ($beforeUnrelatedChange -> $afterUnrelatedChange)",
            beforeUnrelatedChange,
            afterUnrelatedChange,
        )
    }

    @Test
    fun `en pause la position reste figee`() {
        val remote = FakeSpotifyRemote(RemoteState.CONNECTED)
        remote.playback.value = playback(positionMs = 42_000, paused = true)
        val coordinator = connectedCoordinator(remote)

        runBlocking { withTimeout(5_000) { coordinator.state.first { it.nowPlaying?.track != null } } }
        val before = coordinator.currentPositionMs()
        clockMs += 10_000

        assertEquals("la position ne doit pas bouger en pause", before, coordinator.currentPositionMs())
    }
}
