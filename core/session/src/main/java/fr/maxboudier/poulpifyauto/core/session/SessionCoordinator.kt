package fr.maxboudier.poulpifyauto.core.session

import android.os.SystemClock
import android.util.Log
import fr.maxboudier.poulpifyauto.core.model.AppConfig
import fr.maxboudier.poulpifyauto.core.model.ConfigSource
import fr.maxboudier.poulpifyauto.core.model.ConnectionState
import fr.maxboudier.poulpifyauto.core.model.Passenger
import fr.maxboudier.poulpifyauto.core.model.PlaybackSnapshot
import fr.maxboudier.poulpifyauto.core.model.Playlist
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.model.RemotePlayback
import fr.maxboudier.poulpifyauto.core.model.RemoteState
import fr.maxboudier.poulpifyauto.core.model.SpotifyRemote
import fr.maxboudier.poulpifyauto.core.model.RepeatMode
import fr.maxboudier.poulpifyauto.core.model.Track
import fr.maxboudier.poulpifyauto.core.model.UserFacingError
import fr.maxboudier.poulpifyauto.core.model.Votes
import fr.maxboudier.poulpifyauto.core.network.ApiException
import fr.maxboudier.poulpifyauto.core.network.HostTokenHolder
import fr.maxboudier.poulpifyauto.core.network.NetworkModule
import fr.maxboudier.poulpifyauto.core.network.PoulpifyApi
import fr.maxboudier.poulpifyauto.core.network.SseEvent
import fr.maxboudier.poulpifyauto.core.network.dto.HeartbeatRequestDto
import fr.maxboudier.poulpifyauto.core.network.dto.LoginRequestDto
import fr.maxboudier.poulpifyauto.core.network.dto.QueueAddRequestDto
import fr.maxboudier.poulpifyauto.core.network.dto.SseSnapshotDto
import fr.maxboudier.poulpifyauto.core.network.dto.TransferPlaybackRequestDto
import fr.maxboudier.poulpifyauto.core.network.dto.VoteSkipRequestDto
import fr.maxboudier.poulpifyauto.core.network.safeApiCall
import fr.maxboudier.poulpifyauto.core.network.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Source de vérité unique de l'application.
 *
 * La voiture (templates), le service média et l'écran téléphone ne sont que
 * trois vues de [state]. Aucune des trois ne maintient d'état propre ni ne
 * parle directement au réseau : c'est ce qui produisait les incohérences de
 * l'ancienne version, où trois boucles de sondage indépendantes tournaient.
 *
 * Répartition des responsabilités :
 * - transport (play/pause/next/seek) → App Remote d'abord, repli serveur
 * - ajout à la file → serveur (conserve l'attribution « ajouté par » et le verrou)
 * - file, passagers, votes, verrou → serveur via SSE
 * - état de lecture et position → App Remote (poussé, exact)
 */
class SessionCoordinator(
    private val settings: ConfigSource,
    private val remote: SpotifyRemote,
    private val tokenHolder: HostTokenHolder,
    private val scope: CoroutineScope,
    private val debugLogging: Boolean,
) {
    private val subscribers = AtomicInteger(0)
    private val loginMutex = Mutex()

    private var api: PoulpifyApi? = null
    private var apiBaseUrl: String? = null
    private var sseJob: Job? = null
    private var heartbeatJob: Job? = null
    private var libraryJob: Job? = null

    private val serverSnapshot = MutableStateFlow<SseSnapshotDto?>(null)
    private val library = MutableStateFlow(LibraryState())
    private val connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val error = MutableStateFlow<UserFacingError?>(null)
    private val config = MutableStateFlow<AppConfig?>(null)

    /**
     * Ancre de position : App Remote ne pousse la position qu'aux changements.
     * On interpole localement au lieu de la faire figurer dans [state], ce qui
     * éviterait d'invalider tout l'écran voiture deux fois par seconde.
     */
    @Volatile
    private var positionAnchorMs: Long = 0
    @Volatile
    private var positionAnchorAt: Long = 0
    @Volatile
    private var positionAdvancing: Boolean = false

    private val localState = combine(connection, error, library, config) { conn, err, lib, cfg ->
        LocalState(conn, err, lib, cfg)
    }

    val state: StateFlow<PoulpifyUiState> = combine(
        serverSnapshot,
        remote.playback,
        remote.state,
        localState,
    ) { snapshot, remotePlayback, remoteState, local ->
        merge(snapshot, remotePlayback, remoteState, local)
    }.stateIn(scope, SharingStarted.Eagerly, PoulpifyUiState())

    // ---------------------------------------------------------------------
    // Cycle de vie
    // ---------------------------------------------------------------------

    /**
     * Démarre la session. Compté par références : l'activité, le service média
     * et la session voiture peuvent l'appeler indépendamment, la session ne
     * s'arrête qu'au dernier relâchement.
     */
    fun acquire() {
        if (subscribers.incrementAndGet() == 1) {
            scope.launch { startSession() }
        }
    }

    fun release() {
        if (subscribers.decrementAndGet() <= 0) {
            subscribers.set(0)
            scope.launch { stopSession() }
        }
    }

    private suspend fun startSession() {
        val cfg = settings.current()
        config.value = cfg

        if (!cfg.isComplete) {
            connection.value = ConnectionState.DISCONNECTED
            error.value = UserFacingError(
                "Configuration incomplète : renseigne l'URL du serveur et le mot de passe hôte.",
                retryable = false,
            )
            return
        }

        buildApi(cfg.serverUrl)
        remote.connect()
        loginLoop(cfg)
        startEventStream(cfg)
        startHeartbeat(cfg)
        refreshLibrary()
    }

    private suspend fun stopSession() {
        sseJob?.cancel(); sseJob = null
        heartbeatJob?.cancel(); heartbeatJob = null
        libraryJob?.cancel(); libraryJob = null
        remote.disconnect()

        // On ferme proprement la session cote serveur : depuis la Phase 0 cela
        // ne detruit plus les jetons Spotify, le site web reste connecte.
        runCatching { api?.let { safeApiCall { it.logoutHost() } } }
        tokenHolder.token = null
        connection.value = ConnectionState.DISCONNECTED
        serverSnapshot.value = null
    }

    private fun buildApi(serverUrl: String) {
        if (apiBaseUrl == serverUrl && api != null) return
        val client = NetworkModule.createOkHttpClient(tokenHolder, debugLogging)
        api = NetworkModule.createApi(serverUrl, client)
        events = NetworkModule.createEventsClient(serverUrl, client)
        apiBaseUrl = serverUrl
    }

    private var events: fr.maxboudier.poulpifyauto.core.network.PoulpifyEventsClient? = null

    // ---------------------------------------------------------------------
    // Authentification hôte
    // ---------------------------------------------------------------------

    /**
     * Connexion automatique en tant qu'hôte, avec backoff. C'est la demande
     * centrale : au lancement, l'app prend la main sur la session sans que le
     * conducteur ait quoi que ce soit à saisir.
     */
    private suspend fun loginLoop(cfg: AppConfig) {
        var backoff = MIN_BACKOFF_MS
        while (subscribers.get() > 0) {
            if (login(cfg)) return
            connection.value = ConnectionState.RECONNECTING
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private suspend fun login(cfg: AppConfig): Boolean = loginMutex.withLock {
        val client = api ?: return false
        val password = cfg.hostPassword ?: return false
        connection.value = ConnectionState.AUTHENTICATING

        val result = safeApiCall {
            client.loginHost(
                LoginRequestDto(
                    password = password,
                    // On reprend la main sur une session eventuellement ouverte
                    // ailleurs. Depuis la Phase 0 cela n'ejecte plus les passagers.
                    force = true,
                    autoDisconnectEnabled = !cfg.disableServerAutoDisconnect,
                )
            )
        }

        result.fold(
            onSuccess = { response ->
                if (!response.success || response.hostToken == null) {
                    error.value = UserFacingError("Le serveur a refusé la connexion hôte.", retryable = false)
                    connection.value = ConnectionState.DISCONNECTED
                    return@withLock false
                }
                tokenHolder.token = response.hostToken
                connection.value = if (response.spotifyAuthenticated) {
                    ConnectionState.CONNECTED
                } else {
                    // Session hote ouverte, mais le serveur n'a pas de jeton
                    // Spotify : c'est une autorisation OAuth a faire une fois
                    // depuis le telephone, pas une panne.
                    ConnectionState.DEGRADED
                }
                error.value = null
                true
            },
            onFailure = { failure ->
                val apiError = failure as? ApiException
                error.value = UserFacingError(
                    apiError?.message ?: "Connexion au serveur impossible.",
                    retryable = apiError?.retryable ?: true,
                )
                if (apiError?.code == 401) {
                    // Mot de passe faux : inutile de marteler le serveur.
                    error.value = UserFacingError("Mot de passe hôte incorrect.", retryable = false)
                    connection.value = ConnectionState.DISCONNECTED
                    return@withLock true
                }
                false
            },
        )
    }

    /** Re-login silencieux après un 403 (jeton expiré ou repris ailleurs). */
    private suspend fun reAuthenticate() {
        val cfg = config.value ?: return
        login(cfg)
    }

    // ---------------------------------------------------------------------
    // Flux temps réel
    // ---------------------------------------------------------------------

    private fun startEventStream(cfg: AppConfig) {
        sseJob?.cancel()
        val stream = events ?: return
        sseJob = scope.launch {
            stream.connect().collect { event ->
                when (event) {
                    is SseEvent.Connected -> {
                        if (connection.value == ConnectionState.RECONNECTING) {
                            connection.value = ConnectionState.CONNECTED
                        }
                    }
                    is SseEvent.Snapshot -> onSnapshot(event.data)
                    is SseEvent.Disconnected -> {
                        if (subscribers.get() > 0 && connection.value == ConnectionState.CONNECTED) {
                            connection.value = ConnectionState.RECONNECTING
                        }
                    }
                }
            }
        }
    }

    private suspend fun onSnapshot(snapshot: SseSnapshotDto) {
        serverSnapshot.value = snapshot

        // Le serveur a perdu la session hote (redemarrage, expiration) :
        // on se re-authentifie sans rien demander au conducteur.
        if (!snapshot.status.hostActive && subscribers.get() > 0) {
            reAuthenticate()
        }

        connection.value = when {
            !snapshot.status.authenticated -> ConnectionState.DEGRADED
            tokenHolder.token == null -> ConnectionState.RECONNECTING
            else -> ConnectionState.CONNECTED
        }

        // Si App Remote n'est pas connecte, la position vient du serveur.
        if (remote.state.value != RemoteState.CONNECTED) {
            snapshot.player?.let { player ->
                setPositionAnchor(player.progressMs, player.isPlaying)
            }
        }
    }

    private fun startHeartbeat(cfg: AppConfig) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (subscribers.get() > 0) {
                val client = api
                if (client != null && tokenHolder.token != null) {
                    // 5s suffisent : le serveur purge les inactifs a 15s. L'ancienne
                    // app envoyait un heartbeat par seconde, plus deux autres requetes.
                    safeApiCall {
                        client.heartbeat(
                            HeartbeatRequestDto(
                                username = cfg.driverName,
                                emoji = cfg.driverEmoji,
                                hostToken = tokenHolder.token,
                            )
                        )
                    }
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Bibliothèque
    // ---------------------------------------------------------------------

    fun refreshLibrary() {
        libraryJob?.cancel()
        libraryJob = scope.launch {
            val client = api ?: return@launch
            val playlists = safeApiCall { client.getPlaylists() }.getOrNull()
                ?.items?.map { it.toDomain() } ?: emptyList()
            val liked = safeApiCall { client.getLikedTracks() }.getOrNull()
                ?.items?.mapNotNull { it.track?.toDomain() } ?: emptyList()
            val top = safeApiCall { client.getTopTracks() }.getOrNull()
                ?.items?.map { it.toDomain() } ?: emptyList()
            val recent = safeApiCall { client.getRecentlyPlayed() }.getOrNull()
                ?.items?.map { it.track.toDomain() } ?: emptyList()

            library.value = LibraryState(playlists, liked, top, recent)
        }
    }

    suspend fun playlistTracks(playlistId: String): List<Track> {
        val client = api ?: return emptyList()
        return safeApiCall { client.getPlaylistTracks(playlistId) }
            .getOrNull()?.items?.mapNotNull { it.track?.toDomain() } ?: emptyList()
    }

    suspend fun search(query: String): List<Track> {
        val client = api ?: return emptyList()
        return safeApiCall { client.search(query, limit = 20) }
            .getOrNull()?.tracks?.items?.map { it.toDomain() } ?: emptyList()
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    suspend fun togglePlayPause(): Result<Unit> {
        val playing = state.value.nowPlaying?.isPlaying == true
        return if (playing) pause() else play()
    }

    suspend fun play(): Result<Unit> = transport(
        viaRemote = { remote.resume() },
        viaServer = { it.play(null) },
    )

    suspend fun pause(): Result<Unit> = transport(
        viaRemote = { remote.pause() },
        viaServer = { it.pause(null) },
    )

    suspend fun skipNext(): Result<Unit> = transport(
        viaRemote = { remote.skipNext() },
        viaServer = { it.hostSkip() },
    )

    suspend fun skipPrevious(): Result<Unit> = transport(
        viaRemote = { remote.skipPrevious() },
        viaServer = { it.skipPrevious(null) },
    )

    suspend fun seekTo(positionMs: Long): Result<Unit> = transport(
        viaRemote = { remote.seekTo(positionMs) },
        viaServer = { it.seek(positionMs) },
    )

    suspend fun setShuffle(enabled: Boolean): Result<Unit> = transport(
        viaRemote = { remote.setShuffle(enabled) },
        viaServer = { it.setShuffle(enabled) },
    )

    suspend fun setRepeat(mode: RepeatMode): Result<Unit> = transport(
        viaRemote = { remote.setRepeat(mode) },
        viaServer = { it.setRepeat(mode.toServerValue()) },
    )

    /**
     * Lance immédiatement un titre ou un contexte. Passe par App Remote, qui
     * sait démarrer une lecture même sans appareil actif côté Web API.
     */
    suspend fun playNow(uri: String): Result<Unit> {
        val result = remote.play(uri)
        if (result.isSuccess) return result
        return reportFailure(
            Result.failure(
                ApiException(
                    "Impossible de lancer la lecture : Spotify doit être ouvert sur le téléphone.",
                )
            )
        )
    }

    /**
     * Ajoute à la file **via le serveur** et non via App Remote : c'est le
     * serveur qui tient l'attribution « ajouté par » que voient les passagers,
     * et qui applique le verrou.
     */
    suspend fun addToQueue(track: Track): Result<Unit> {
        val client = api ?: return reportFailure(Result.failure(ApiException("Serveur non configuré.")))
        val cfg = config.value
        val result = safeApiCall {
            client.addToQueue(
                QueueAddRequestDto(
                    uri = track.uri,
                    username = cfg?.driverName,
                    emoji = cfg?.driverEmoji,
                )
            )
        }
        if (result.isFailure && (result.exceptionOrNull() as? ApiException)?.code == 403) {
            reAuthenticate()
        }
        return reportFailure(result.map { })
    }

    suspend fun toggleQueueLock(): Result<Unit> {
        val client = api ?: return reportFailure(Result.failure(ApiException("Serveur non configuré.")))
        val result = safeApiCall { client.toggleLock() }
        if ((result.exceptionOrNull() as? ApiException)?.code == 403) {
            reAuthenticate()
            return reportFailure(safeApiCall { client.toggleLock() }.map { })
        }
        return reportFailure(result.map { })
    }

    /** Skip immédiat réservé à l'hôte, sans passer par le vote des passagers. */
    suspend fun hostSkip(): Result<Unit> {
        val client = api ?: return reportFailure(Result.failure(ApiException("Serveur non configuré.")))
        // On passe par le serveur meme si App Remote est dispo : le serveur
        // remet les votes a zero, ce qu'App Remote ne peut pas faire.
        val result = safeApiCall { client.hostSkip() }
        return if (result.isSuccess) result.map { } else transport(
            viaRemote = { remote.skipNext() },
            viaServer = { it.hostSkip() },
        )
    }

    suspend fun voteSkip(): Result<Unit> {
        val client = api ?: return reportFailure(Result.failure(ApiException("Serveur non configuré.")))
        val name = config.value?.driverName ?: return Result.failure(ApiException("Profil conducteur absent."))
        return reportFailure(safeApiCall { client.voteSkip(VoteSkipRequestDto(name)) }.map { })
    }

    suspend fun transferPlaybackTo(deviceId: String): Result<Unit> {
        val client = api ?: return reportFailure(Result.failure(ApiException("Serveur non configuré.")))
        return reportFailure(
            safeApiCall { client.transferPlayback(TransferPlaybackRequestDto(deviceId)) }.map { }
        )
    }

    /**
     * App Remote d'abord (latence nulle, marche même serveur injoignable),
     * repli sur le serveur si le téléphone n'a pas Spotify au premier plan.
     */
    private suspend fun transport(
        viaRemote: suspend () -> Result<Unit>,
        viaServer: suspend (PoulpifyApi) -> retrofit2.Response<Unit>,
    ): Result<Unit> {
        if (remote.isConnected) {
            val result = viaRemote()
            if (result.isSuccess) return result
            Log.d(TAG, "App Remote command failed, falling back to server", result.exceptionOrNull())
        }
        val client = api ?: return reportFailure(
            Result.failure(ApiException("Ni Spotify ni le serveur ne sont joignables."))
        )
        val result = safeApiCall { viaServer(client) }
        if ((result.exceptionOrNull() as? ApiException)?.code == 403) {
            reAuthenticate()
            return reportFailure(safeApiCall { viaServer(client) }.map { })
        }
        return reportFailure(result.map { })
    }

    /**
     * Toute erreur devient visible. C'est le point qui manquait le plus dans
     * l'ancienne version : chaque `catch` s'y terminait par un `Log.e` muet.
     */
    private fun <T> reportFailure(result: Result<T>): Result<T> {
        result.exceptionOrNull()?.let { throwable ->
            val apiError = throwable as? ApiException
            error.value = UserFacingError(
                apiError?.message ?: throwable.message ?: "Action impossible.",
                retryable = apiError?.retryable ?: true,
            )
        }
        return result
    }

    fun clearError() {
        error.value = null
    }

    /** Force une reconnexion complète (bouton « Réessayer »). */
    fun retry() {
        error.value = null
        scope.launch {
            val cfg = settings.current()
            config.value = cfg
            if (!cfg.isComplete) return@launch
            buildApi(cfg.serverUrl)
            remote.connect()
            loginLoop(cfg)
            startEventStream(cfg)
            refreshLibrary()
        }
    }

    // ---------------------------------------------------------------------
    // Position de lecture
    // ---------------------------------------------------------------------

    private fun setPositionAnchor(positionMs: Long, advancing: Boolean) {
        positionAnchorMs = positionMs
        positionAnchorAt = SystemClock.elapsedRealtime()
        positionAdvancing = advancing
    }

    /**
     * Position interpolée. Volontairement hors de [state] : la faire figurer
     * dans l'état forcerait une invalidation de l'écran voiture deux fois par
     * seconde, ce que le host Android Auto limite de toute façon.
     */
    fun currentPositionMs(): Long {
        val base = positionAnchorMs
        if (!positionAdvancing) return base
        val elapsed = SystemClock.elapsedRealtime() - positionAnchorAt
        val duration = state.value.nowPlaying?.durationMs ?: Long.MAX_VALUE
        return (base + elapsed).coerceAtMost(duration)
    }

    // ---------------------------------------------------------------------
    // Fusion d'état
    // ---------------------------------------------------------------------

    private fun merge(
        snapshot: SseSnapshotDto?,
        remotePlayback: RemotePlayback?,
        remoteState: RemoteState,
        local: LocalState,
    ): PoulpifyUiState {
        val serverQueue = snapshot?.queue?.map { it.toDomain() } ?: emptyList()
        val serverPlayer = snapshot?.player?.toDomain()

        val nowPlaying = when {
            remoteState == RemoteState.CONNECTED && remotePlayback != null -> {
                // L'attribution et l'URL de pochette viennent du serveur ;
                // le titre, l'etat et la position viennent d'App Remote.
                val serverMatch = serverPlayer?.track?.takeIf { it.uri == remotePlayback.trackUri }
                setPositionAnchor(remotePlayback.positionMs, !remotePlayback.isPaused)
                PlaybackSnapshot(
                    track = Track(
                        id = remotePlayback.trackUri.substringAfterLast(':'),
                        uri = remotePlayback.trackUri,
                        name = remotePlayback.trackName,
                        artists = remotePlayback.artists,
                        albumName = remotePlayback.albumName,
                        imageUrl = serverMatch?.imageUrl,
                        durationMs = remotePlayback.durationMs,
                        addedViaPoulpify = serverMatch?.addedViaPoulpify ?: false,
                        addedBy = serverMatch?.addedBy,
                        addedByEmoji = serverMatch?.addedByEmoji,
                        addedByHost = serverMatch?.addedByHost ?: false,
                    ),
                    isPlaying = !remotePlayback.isPaused,
                    progressMs = remotePlayback.positionMs,
                    durationMs = remotePlayback.durationMs,
                    shuffling = remotePlayback.shuffling,
                    repeatMode = remotePlayback.repeatMode,
                    deviceName = serverPlayer?.deviceName,
                    deviceActive = snapshot?.status?.spotifyDeviceActive ?: true,
                    canSkipNext = remotePlayback.canSkipNext,
                    canSkipPrevious = remotePlayback.canSkipPrevious,
                    canSeek = remotePlayback.canSeek,
                )
            }
            else -> serverPlayer
        }

        return PoulpifyUiState(
            connection = local.connection,
            remote = remoteState,
            nowPlaying = nowPlaying,
            queue = serverQueue,
            passengers = snapshot?.passengers?.map { it.toDomain() } ?: emptyList<Passenger>(),
            votes = Votes(
                current = snapshot?.votes?.skipVotes ?: 0,
                required = snapshot?.votes?.requiredVotes ?: 1,
            ),
            queueLocked = snapshot?.status?.queueLocked ?: false,
            playlists = local.library.playlists,
            likedTracks = local.library.liked,
            topTracks = local.library.top,
            recentTracks = local.library.recent,
            shareUrl = local.config?.serverUrl,
            lastError = local.error,
        )
    }

    private data class LocalState(
        val connection: ConnectionState,
        val error: UserFacingError?,
        val library: LibraryState,
        val config: AppConfig?,
    )

    data class LibraryState(
        val playlists: List<Playlist> = emptyList(),
        val liked: List<Track> = emptyList(),
        val top: List<Track> = emptyList(),
        val recent: List<Track> = emptyList(),
    )

    companion object {
        private const val TAG = "SessionCoordinator"
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }
}

private fun RepeatMode.toServerValue(): String = when (this) {
    RepeatMode.OFF -> "off"
    RepeatMode.TRACK -> "track"
    RepeatMode.CONTEXT -> "context"
}
