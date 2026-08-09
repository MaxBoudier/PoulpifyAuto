package fr.maxboudier.poulpifyauto.core.session

import android.content.Context
import fr.maxboudier.poulpifyauto.core.data.PoulpifySettings
import fr.maxboudier.poulpifyauto.core.network.HostTokenHolder
import fr.maxboudier.poulpifyauto.core.spotify.SpotifyRemoteController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Graphe de dépendances tenu à la main.
 *
 * Pas de Hilt : trois points d'entrée seulement (activité, service média,
 * service voiture), et les `Screen` de Car App Library ne sont pas des
 * composants Android — l'injection par annotations y coûterait plus qu'elle
 * ne rapporte, pour un graphe qui tient en dix lignes.
 *
 * Initialisé une fois depuis `PoulpifyApplication.onCreate()`.
 */
object PoulpifyGraph {

    @Volatile
    private var initialized = false

    lateinit var settings: PoulpifySettings
        private set
    lateinit var remote: SpotifyRemoteController
        private set
    lateinit var coordinator: SessionCoordinator
        private set

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Synchronized
    fun init(
        context: Context,
        spotifyClientId: String,
        spotifyRedirectUri: String,
        debugLogging: Boolean,
    ) {
        if (initialized) return
        val appContext = context.applicationContext

        settings = PoulpifySettings(appContext)
        remote = SpotifyRemoteController(appContext, spotifyClientId, spotifyRedirectUri)
        coordinator = SessionCoordinator(
            settings = settings,
            remote = remote,
            tokenHolder = HostTokenHolder(),
            scope = applicationScope,
            debugLogging = debugLogging,
        )
        initialized = true
    }

    fun isInitialized(): Boolean = initialized
}
