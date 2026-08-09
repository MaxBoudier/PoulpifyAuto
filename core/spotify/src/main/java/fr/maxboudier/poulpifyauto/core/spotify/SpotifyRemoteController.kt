package fr.maxboudier.poulpifyauto.core.spotify

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerState
import fr.maxboudier.poulpifyauto.core.model.RemotePlayback
import fr.maxboudier.poulpifyauto.core.model.SpotifyRemote
import fr.maxboudier.poulpifyauto.core.model.RemoteState
import fr.maxboudier.poulpifyauto.core.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Enveloppe Spotify App Remote : connexion, reconnexion, état temps réel.
 *
 * C'est la source de vérité pour tout ce qui touche à la lecture en cours —
 * App Remote pousse l'état à chaque changement, ce qui supprime le sondage et
 * donne une position de lecture exacte au lieu d'une extrapolation locale.
 */
class SpotifyRemoteController(
    private val context: Context,
    private val clientId: String,
    private val redirectUri: String,
) : SpotifyRemote {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private var remote: SpotifyAppRemote? = null
    private var playerSubscription: Subscription<PlayerState>? = null
    private var reconnectJob: Job? = null
    private var reconnectDelayMs = MIN_RECONNECT_MS
    private var wantConnection = false

    private val _state = MutableStateFlow(RemoteState.DISCONNECTED)
    override val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val _playback = MutableStateFlow<RemotePlayback?>(null)
    override val playback: StateFlow<RemotePlayback?> = _playback.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override val isConnected: Boolean get() = remote?.isConnected == true

    /**
     * Contexte d'activité, quand l'écran téléphone est au premier plan.
     *
     * `showAuthView(true)` doit afficher une boîte de dialogue : avec le seul
     * contexte applicatif, Spotify ne peut rien afficher et répond
     * `UserNotAuthorizedException`. La toute première autorisation doit donc
     * passer par l'activité ; une fois accordée, Android Auto se connecte
     * ensuite sans interface.
     */
    @Volatile
    private var activityContext: Context? = null

    fun attachActivity(activity: Activity?) {
        activityContext = activity
        if (activity != null && !isConnected) connect()
    }

    /**
     * `SpotifyAppRemote.connect` construit un `Handler` et lance un `AsyncTask`
     * sans se soucier du thread appelant : appelé depuis un worker, il jette
     * « Can't create handler inside thread ... that has not called
     * Looper.prepare() » et tue le processus.
     *
     * Le coordinateur tourne sur `Dispatchers.Default` ; c'est donc ici, au
     * contact du SDK, qu'on rebascule sur le thread principal — plutôt que
     * d'imposer cette contrainte à tous les appelants.
     */
    override fun connect() {
        scope.launch { connectOnMainThread() }
    }

    private fun connectOnMainThread() {
        wantConnection = true
        if (isConnected) return
        if (!SpotifyAppRemote.isSpotifyInstalled(context)) {
            _state.value = RemoteState.NOT_INSTALLED
            _lastError.value = "L'application Spotify n'est pas installée sur ce téléphone."
            return
        }
        if (clientId.isBlank()) {
            _state.value = RemoteState.UNAUTHORIZED
            _lastError.value = "SPOTIFY_CLIENT_ID absent : renseigne-le dans local.properties."
            return
        }

        _state.value = RemoteState.CONNECTING
        val params = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        // L'activite si elle est au premier plan, sinon le contexte applicatif :
        // seule la premiere autorisation a besoin d'afficher une interface.
        val connectContext = activityContext ?: context
        SpotifyAppRemote.connect(connectContext, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                remote = appRemote
                reconnectDelayMs = MIN_RECONNECT_MS
                _state.value = RemoteState.CONNECTED
                _lastError.value = null
                subscribeToPlayerState(appRemote)
            }

            override fun onFailure(error: Throwable) {
                remote = null
                _playback.value = null
                // Chaque cause a un remede different : les distinguer evite le
                // "ca ne marche pas" opaque de l'ancienne version.
                when (error) {
                    is CouldNotFindSpotifyApp -> {
                        _state.value = RemoteState.NOT_INSTALLED
                        _lastError.value = "Spotify introuvable. Installe l'app Spotify."
                    }
                    is NotLoggedInException -> {
                        _state.value = RemoteState.UNAUTHORIZED
                        _lastError.value = "Connecte-toi dans l'application Spotify, puis réessaie."
                    }
                    is UserNotAuthorizedException -> {
                        _state.value = RemoteState.UNAUTHORIZED
                        _lastError.value = "Autorisation refusée. Vérifie que le SHA-1 et le package " +
                            "sont enregistrés dans le dashboard Spotify."
                    }
                    else -> {
                        _state.value = RemoteState.DISCONNECTED
                        _lastError.value = "Connexion Spotify perdue : ${error.message ?: "raison inconnue"}"
                    }
                }
                Log.w(TAG, "App Remote connection failed", error)
                scheduleReconnect()
            }
        })
    }

    /** Même contrainte de thread que [connect] : le SDK défait ses Handler ici. */
    override fun disconnect() {
        wantConnection = false
        scope.launch {
            reconnectJob?.cancel()
            reconnectJob = null
            playerSubscription?.cancel()
            playerSubscription = null
            remote?.let { SpotifyAppRemote.disconnect(it) }
            remote = null
            _playback.value = null
            _state.value = RemoteState.DISCONNECTED
        }
    }

    private fun subscribeToPlayerState(appRemote: SpotifyAppRemote) {
        playerSubscription?.cancel()
        playerSubscription = appRemote.playerApi.subscribeToPlayerState()
            .setEventCallback { state -> _playback.value = state.toDomain() }
            .setErrorCallback { error ->
                Log.w(TAG, "Player state subscription failed", error)
                remote = null
                _state.value = RemoteState.DISCONNECTED
                scheduleReconnect()
            } as Subscription<PlayerState>
    }

    /**
     * Reconnexion avec backoff : App Remote se déconnecte dès que Spotify est
     * mis en arrière-plan ou tué, ce qui arrive constamment en voiture.
     */
    private fun scheduleReconnect() {
        if (!wantConnection) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_MS)
            // Deja sur le thread principal : appel direct, pas de relance.
            if (wantConnection && !isConnected) connectOnMainThread()
        }
    }

    // --- Commandes de transport -------------------------------------------
    // Toutes renvoient un Result : l'appelant peut afficher l'echec au
    // conducteur au lieu de le perdre dans un Log.e comme avant.

    override suspend fun resume(): Result<Unit> = call { it.playerApi.resume() }
    override suspend fun pause(): Result<Unit> = call { it.playerApi.pause() }
    override suspend fun skipNext(): Result<Unit> = call { it.playerApi.skipNext() }
    override suspend fun skipPrevious(): Result<Unit> = call { it.playerApi.skipPrevious() }
    override suspend fun seekTo(positionMs: Long): Result<Unit> = call { it.playerApi.seekTo(positionMs) }
    override suspend fun setShuffle(enabled: Boolean): Result<Unit> = call { it.playerApi.setShuffle(enabled) }
    override suspend fun setRepeat(mode: RepeatMode): Result<Unit> =
        call { it.playerApi.setRepeat(mode.toRemoteValue()) }
    override suspend fun play(uri: String): Result<Unit> = call { it.playerApi.play(uri) }
    override suspend fun queue(uri: String): Result<Unit> = call { it.playerApi.queue(uri) }

    /**
     * Pochette fournie directement par Spotify, sans requête HTTP.
     * L'ancienne app re-téléchargeait l'image à chaque seconde.
     */
    suspend fun albumArt(imageUriRaw: String?, dimension: Image.Dimension = Image.Dimension.LARGE): Bitmap? {
        val appRemote = remote ?: return null
        if (imageUriRaw.isNullOrBlank()) return null
        return suspendCancellableCoroutine { continuation ->
            appRemote.imagesApi.getImage(ImageUri(imageUriRaw), dimension)
                .setResultCallback { bitmap -> if (continuation.isActive) continuation.resume(bitmap) }
                .setErrorCallback { if (continuation.isActive) continuation.resume(null) }
        }
    }

    private suspend fun call(
        block: (SpotifyAppRemote) -> com.spotify.protocol.client.CallResult<com.spotify.protocol.types.Empty>,
    ): Result<Unit> {
        val appRemote = remote
        if (appRemote == null || !appRemote.isConnected) {
            return Result.failure(SpotifyRemoteUnavailable())
        }
        return suspendCancellableCoroutine { continuation ->
            block(appRemote)
                .setResultCallback { if (continuation.isActive) continuation.resume(Result.success(Unit)) }
                .setErrorCallback { error ->
                    if (continuation.isActive) continuation.resume(Result.failure(error))
                }
        }
    }

    companion object {
        private const val TAG = "SpotifyRemote"
        private const val MIN_RECONNECT_MS = 2_000L
        private const val MAX_RECONNECT_MS = 30_000L
    }
}

private fun RepeatMode.toRemoteValue(): Int = when (this) {
    RepeatMode.OFF -> 0
    RepeatMode.TRACK -> 1
    RepeatMode.CONTEXT -> 2
}

/** Signale que la commande doit être retentée via le serveur (Web API). */
class SpotifyRemoteUnavailable : Exception("Spotify App Remote n'est pas connecté.")

/** Traduction SDK → domaine, seul endroit où les types Spotify sont manipulés. */
private fun PlayerState.toDomain(): RemotePlayback? {
    val t = track ?: return null
    return RemotePlayback(
        trackUri = t.uri,
        trackName = t.name,
        artists = t.artists?.map { it.name } ?: listOfNotNull(t.artist?.name),
        albumName = t.album?.name,
        durationMs = t.duration,
        positionMs = playbackPosition,
        isPaused = isPaused,
        shuffling = playbackOptions?.isShuffling ?: false,
        repeatMode = when (playbackOptions?.repeatMode) {
            1 -> RepeatMode.TRACK
            2 -> RepeatMode.CONTEXT
            else -> RepeatMode.OFF
        },
        canSkipNext = playbackRestrictions?.canSkipNext ?: true,
        canSkipPrevious = playbackRestrictions?.canSkipPrev ?: true,
        canSeek = playbackRestrictions?.canSeek ?: true,
        imageUriRaw = t.imageUri?.raw,
    )
}
