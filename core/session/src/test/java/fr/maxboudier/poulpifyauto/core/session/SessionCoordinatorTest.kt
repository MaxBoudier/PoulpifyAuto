package fr.maxboudier.poulpifyauto.core.session

import fr.maxboudier.poulpifyauto.core.model.ConnectionState
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.RemoteState
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Le coordinateur parle à un vrai serveur HTTP (MockWebServer) : on attend donc
 * de vraies conditions plutôt que d'avancer un temps virtuel, qui ne saurait
 * rien des E/S réseau réelles.
 */
class SessionCoordinatorTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope

    /** Réponses routées par chemin : indépendant de l'ordre des appels. */
    private val routes = mutableMapOf<String, () -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        server = MockWebServer()
        routes.clear()
        requests.clear()

        route("/api/host/login") { json("""{"success":true,"hostToken":"tok1","spotifyAuthenticated":true}""") }
        // Le flux SSE se ferme immediatement ici : le client rouvrira, ce qui
        // n'a pas d'incidence sur les assertions.
        route("/api/events") {
            MockResponse.Builder().setHeader("Content-Type", "text/event-stream").body(": ping\n\n").build()
        }
        route("/api/host/logout") { json("""{"success":true}""") }
        listOf("/api/me/playlists", "/api/me/tracks", "/api/me/top-tracks", "/api/me/recently-played")
            .forEach { route(it) { json("""{"items":[]}""") } }
        route("/api/heartbeat") { json("""{"activeUsers":[],"skipVotes":0,"requiredVotes":1,"hasVoted":false}""") }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                return routes[path]?.invoke() ?: json("""{"error":"no route for $path"}""", 404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun route(path: String, response: () -> MockResponse) {
        routes[path] = response
    }

    private fun json(body: String, code: Int = 200) = MockResponse.Builder()
        .code(code)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun coordinator(
        remote: FakeSpotifyRemote = FakeSpotifyRemote(),
        tokenHolder: HostTokenHolder = HostTokenHolder(),
        password: String? = "secret",
    ) = SessionCoordinator(
        settings = FakeConfigSource.of(server.url("/").toString(), password),
        remote = remote,
        tokenHolder = tokenHolder,
        scope = scope,
        debugLogging = false,
    ) to tokenHolder

    /** Attend qu'une condition sur l'état devienne vraie, ou échoue au bout de 5 s. */
    private fun SessionCoordinator.await(
        description: String,
        predicate: (PoulpifyUiState) -> Boolean,
    ): PoulpifyUiState = runBlocking {
        try {
            withTimeout(5_000) { state.first(predicate) }
        } catch (e: Exception) {
            throw AssertionError("condition jamais atteinte : $description (état=${state.value})", e)
        }
    }

    private fun requestsTo(path: String) = requests.count { it.url.encodedPath == path }

    @Test
    fun `se connecte automatiquement en hote au demarrage`() {
        val (coordinator, tokens) = coordinator()

        coordinator.acquire()
        coordinator.await("session hôte connectée") { it.connection == ConnectionState.CONNECTED }

        assertEquals("tok1", tokens.token)

        val loginBody = requests.first { it.url.encodedPath == "/api/host/login" }
            .body?.utf8().orEmpty()
        // `force` reprend la main sur une session ouverte ailleurs ; depuis la
        // Phase 0 cote serveur, cela n'ejecte plus les passagers.
        assertTrue(loginBody.contains("\"force\":true"))
        assertTrue("le mot de passe doit etre transmis", loginBody.contains("secret"))
    }

    @Test
    fun `un mot de passe incorrect ne boucle pas et affiche une erreur nette`() {
        route("/api/host/login") { json("""{"error":"Invalid password"}""", 401) }

        val (coordinator, tokens) = coordinator()
        coordinator.acquire()
        val state = coordinator.await("erreur de mot de passe remontée") {
            it.lastError != null && it.connection == ConnectionState.DISCONNECTED
        }

        assertNull("aucun jeton ne doit etre retenu", tokens.token)
        assertEquals("Mot de passe hôte incorrect.", state.lastError?.message)
        assertEquals(
            "marteler le serveur avec un mot de passe faux n'aiderait pas",
            1,
            requestsTo("/api/host/login"),
        )
    }

    @Test
    fun `Spotify non autorise cote serveur donne un etat degrade, pas une panne`() {
        route("/api/host/login") { json("""{"success":true,"hostToken":"tok","spotifyAuthenticated":false}""") }

        val (coordinator, tokens) = coordinator()
        coordinator.acquire()
        coordinator.await("état dégradé") { it.connection == ConnectionState.DEGRADED }

        assertNotNull("la session hote est bien ouverte malgre tout", tokens.token)
    }

    @Test
    fun `les commandes passent par App Remote quand il est connecte`() {
        val remote = FakeSpotifyRemote(RemoteState.CONNECTED)
        val (coordinator, _) = coordinator(remote)
        coordinator.acquire()
        coordinator.await("connecté") { it.connection == ConnectionState.CONNECTED }

        val result = runBlocking { coordinator.pause() }

        assertTrue(result.isSuccess)
        assertEquals(listOf("pause"), remote.commands)
        assertEquals("aucun appel serveur ne doit partir", 0, requestsTo("/api/host/player/pause"))
    }

    @Test
    fun `une commande App Remote en echec bascule sur le serveur`() {
        route("/api/host/player/pause") { json("""{"success":true}""") }
        val remote = FakeSpotifyRemote(RemoteState.CONNECTED).apply { failCommands = true }

        val (coordinator, _) = coordinator(remote)
        coordinator.acquire()
        coordinator.await("connecté") { it.connection == ConnectionState.CONNECTED }

        val result = runBlocking { coordinator.pause() }

        assertTrue(remote.commands.contains("pause"))
        assertTrue("le repli serveur doit reussir", result.isSuccess)
        assertEquals(1, requestsTo("/api/host/player/pause"))
    }

    @Test
    fun `App Remote absent n empeche pas la session hote`() {
        val remote = FakeSpotifyRemote(RemoteState.NOT_INSTALLED)
        val (coordinator, _) = coordinator(remote)

        coordinator.acquire()
        val state = coordinator.await("connecté sans Spotify local") {
            it.connection == ConnectionState.CONNECTED
        }

        assertEquals(RemoteState.NOT_INSTALLED, state.remote)
        assertEquals("la connexion App Remote doit quand meme etre tentee", 1, remote.connectCalls)
    }

    /**
     * Régression directe de l'ancien comportement : un jeton hôte périmé
     * produisait un 403 silencieux et l'app restait bloquée sans rien dire.
     */
    @Test
    fun `un 403 declenche une re-authentification puis rejoue l action`() {
        val (coordinator, tokens) = coordinator()
        coordinator.acquire()
        coordinator.await("connecté") { it.connection == ConnectionState.CONNECTED }
        assertEquals("tok1", tokens.token)

        // Premier verrou refuse (jeton perime), puis accepte apres re-login.
        var lockCalls = 0
        route("/api/toggle-lock") {
            if (lockCalls++ == 0) json("""{"error":"Unauthorized host action."}""", 403)
            else json("""{"success":true,"queueLocked":true}""")
        }
        route("/api/host/login") { json("""{"success":true,"hostToken":"tok2","spotifyAuthenticated":true}""") }

        val result = runBlocking { coordinator.toggleQueueLock() }

        assertEquals("le jeton doit avoir ete renouvele", "tok2", tokens.token)
        assertEquals("le verrou doit avoir ete rejoue", 2, lockCalls)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `relacher la derniere reference ferme la session hote`() {
        val (coordinator, tokens) = coordinator()
        coordinator.acquire()
        coordinator.await("connecté") { it.connection == ConnectionState.CONNECTED }
        assertNotNull(tokens.token)

        coordinator.release()
        coordinator.await("session fermée") { it.connection == ConnectionState.DISCONNECTED }

        assertNull(tokens.token)
        assertEquals("le serveur doit avoir ete prevenu", 1, requestsTo("/api/host/logout"))
    }

    @Test
    fun `deux references gardent la session ouverte jusqu au dernier relachement`() {
        val (coordinator, tokens) = coordinator()
        // Le service media et l'ecran voiture prennent chacun une reference.
        coordinator.acquire()
        coordinator.acquire()
        coordinator.await("connecté") { it.connection == ConnectionState.CONNECTED }

        coordinator.release()
        Thread.sleep(300)

        assertNotNull("une reference subsiste, la session doit rester ouverte", tokens.token)
        assertEquals(0, requestsTo("/api/host/logout"))
    }
}
