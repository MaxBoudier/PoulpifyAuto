package fr.maxboudier.poulpifyauto.media

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import fr.maxboudier.poulpifyauto.core.data.QrCodeGenerator
import fr.maxboudier.poulpifyauto.core.session.PoulpifyGraph
import fr.maxboudier.poulpifyauto.core.session.SessionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Surface média d'Android Auto : navigation dans la bibliothèque de l'hôte,
 * écran de lecture, recherche vocale.
 *
 * C'est ce service qui porte le premier plan et sa notification, avec le type
 * `mediaPlayback` — légitimement cette fois, là où l'ancien service usurpait ce
 * type pour ne faire qu'un heartbeat HTTP.
 */
@UnstableApi
class PoulpifyMediaLibraryService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var coordinator: SessionCoordinator
    private lateinit var player: SpotifyProxyPlayer
    private var librarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        coordinator = PoulpifyGraph.coordinator
        // La session hote reste ouverte tant qu'Android Auto est connecte.
        coordinator.acquire()

        player = SpotifyProxyPlayer(mainLooper, coordinator)
        librarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setId("poulpify_media_session")
            .build()

        // Le lecteur n'a pas de source d'evenements propre : c'est l'etat
        // partage qui le pilote.
        scope.launch {
            coordinator.state.collect { player.onUpstreamStateChanged() }
        }

        // Toute nouveaute cote file ou verrou doit rafraichir l'arborescence
        // parcourue, sinon la voiture affiche une file perimee.
        scope.launch {
            coordinator.state
                .map { it.queue.map { track -> track.uri } to it.queueLocked }
                .distinctUntilChanged()
                .collect {
                    librarySession?.notifyChildrenChanged(MediaId.NODE_QUEUE, it.first.size, null)
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        librarySession

    override fun onDestroy() {
        librarySession?.run {
            player.release()
            release()
        }
        librarySession = null
        coordinator.release()
        scope.cancel()
        super.onDestroy()
    }

    // -----------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_TOGGLE_LOCK, Bundle.EMPTY))
                .add(SessionCommand(CMD_HOST_SKIP, Bundle.EMPTY))
                .add(SessionCommand(CMD_VOTE_SKIP, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(customLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = scope.future {
            when (customCommand.customAction) {
                CMD_TOGGLE_LOCK -> {
                    coordinator.toggleQueueLock()
                    session.setCustomLayout(customLayout())
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
                CMD_HOST_SKIP -> {
                    coordinator.hostSkip()
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
                CMD_VOTE_SKIP -> {
                    coordinator.voteSkip()
                    session.setCustomLayout(customLayout())
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
                else -> SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            LibraryResult.ofItem(browsableNode(MediaId.ROOT, "Poulpify"), params)
        )

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val item = when {
                mediaId == MediaId.ROOT -> browsableNode(MediaId.ROOT, "Poulpify")
                mediaId.startsWith("node_") -> rootNodes().firstOrNull { it.mediaId == mediaId }
                else -> allKnownTracks().firstOrNull { MediaId.payload(mediaId) == it.uri }
                    ?.toMediaItem(mediaId)
            }
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val children = childrenOf(parentId)
            LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            session.notifyChildrenChanged(browser, parentId, childrenOf(parentId).size, params)
            LibraryResult.ofVoid()
        }

        // --- Recherche (clavier et voix de l'UI média d'Android Auto) ---

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            val results = coordinator.search(query)
            lastSearch = query to results
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val (cachedQuery, cached) = lastSearch ?: (null to emptyList())
            val results = if (cachedQuery == query) cached else coordinator.search(query)
            LibraryResult.ofItemList(
                ImmutableList.copyOf(results.map { it.toMediaItem(MediaId.queueAdd(it.uri)) }),
                params,
            )
        }

        /**
         * Un appui sur un titre n'est pas une commande « lis ceci maintenant » :
         * selon le préfixe de l'identifiant, on ajoute à la file collaborative
         * (cas par défaut, qui préserve l'attribution) ou on lance la lecture.
         * Dans les deux cas la playlist du lecteur reste inchangée, puisque
         * c'est Spotify qui décide réellement de ce qui passe.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            mediaItems.firstOrNull()?.mediaId?.let { mediaId ->
                handleItemActivation(mediaId)
            }
            currentItemsWithPosition()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.forEach { handleItemActivation(it.mediaId) }
            mutableListOf()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            currentItemsWithPosition()
        }
    }

    private var lastSearch: Pair<String, List<fr.maxboudier.poulpifyauto.core.model.Track>>? = null

    private suspend fun handleItemActivation(mediaId: String) {
        when {
            MediaId.isPlayNow(mediaId) -> coordinator.playNow(MediaId.payload(mediaId))
            MediaId.isQueueAdd(mediaId) -> {
                val uri = MediaId.payload(mediaId)
                val track = allKnownTracks().firstOrNull { it.uri == uri }
                if (track != null) coordinator.addToQueue(track)
            }
        }
    }

    private fun currentItemsWithPosition(): MediaSession.MediaItemsWithStartPosition {
        val items = buildList {
            player.currentMediaItem?.let { add(it) }
        }
        return MediaSession.MediaItemsWithStartPosition(
            items,
            if (items.isEmpty()) 0 else player.currentMediaItemIndex,
            player.currentPosition,
        )
    }

    private fun allKnownTracks() = with(coordinator.state.value) {
        queue + likedTracks + topTracks + recentTracks + listOfNotNull(nowPlaying?.track) +
            (lastSearch?.second ?: emptyList()) + cachedPlaylistTracks.values.flatten()
    }

    private val cachedPlaylistTracks = mutableMapOf<String, List<fr.maxboudier.poulpifyauto.core.model.Track>>()

    private suspend fun childrenOf(parentId: String): List<MediaItem> {
        val ui = coordinator.state.value
        return when {
            parentId == MediaId.ROOT -> rootNodes()

            parentId == MediaId.NODE_QUEUE ->
                if (ui.queue.isEmpty()) {
                    listOf(infoItem("empty_queue", "La file est vide", "Ajoutez un son depuis vos playlists"))
                } else {
                    ui.queue.map { it.toMediaItem(MediaId.queueAdd(it.uri)) }
                }

            parentId == MediaId.NODE_PASSENGERS ->
                if (ui.passengers.isEmpty()) {
                    listOf(infoItem("no_passengers", "Seul à bord 🐙"))
                } else {
                    ui.passengers.map { infoItem("passenger_${it.name}", "${it.emoji} ${it.name}") }
                }

            parentId == MediaId.NODE_INVITE -> inviteItems()

            parentId == MediaId.NODE_PLAYLISTS -> ui.playlists.map { it.toMediaItem() }
            parentId == MediaId.NODE_LIKED -> ui.likedTracks.map { it.toMediaItem(MediaId.queueAdd(it.uri)) }
            parentId == MediaId.NODE_TOP -> ui.topTracks.map { it.toMediaItem(MediaId.queueAdd(it.uri)) }
            parentId == MediaId.NODE_RECENT -> ui.recentTracks.map { it.toMediaItem(MediaId.queueAdd(it.uri)) }

            MediaId.isPlaylist(parentId) -> {
                val playlistId = MediaId.payload(parentId)
                val tracks = coordinator.playlistTracks(playlistId)
                cachedPlaylistTracks[playlistId] = tracks
                // Premiere ligne : lancer toute la playlist. Les suivantes
                // ajoutent le titre a la file collaborative.
                buildList {
                    val playlist = ui.playlists.firstOrNull { it.id == playlistId }
                    if (playlist != null) {
                        add(
                            MediaItem.Builder()
                                .setMediaId(MediaId.playNow(playlist.uri))
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle("▶ Jouer cette playlist maintenant")
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .build()
                                )
                                .build()
                        )
                    }
                    addAll(tracks.map { it.toMediaItem(MediaId.queueAdd(it.uri)) })
                }
            }

            else -> emptyList()
        }
    }

    private fun rootNodes(): List<MediaItem> {
        val ui = coordinator.state.value
        return listOf(
            browsableNode(
                MediaId.NODE_QUEUE,
                "À suivre",
                if (ui.queueLocked) "File verrouillée • ${ui.queue.size} titres"
                else "${ui.queue.size} titres",
            ),
            browsableNode(MediaId.NODE_PLAYLISTS, "Mes playlists"),
            browsableNode(MediaId.NODE_LIKED, "Titres likés"),
            browsableNode(MediaId.NODE_TOP, "Top titres"),
            browsableNode(MediaId.NODE_RECENT, "Écoutés récemment"),
            browsableNode(MediaId.NODE_PASSENGERS, "Passagers", "${ui.passengers.size} à bord"),
            browsableNode(MediaId.NODE_INVITE, "Inviter un passager", "Afficher le QR code"),
        )
    }

    /**
     * QR d'invitation dans la navigation média.
     *
     * Le tableau de bord Car App Library a déjà un écran dédié, mais il n'est
     * pas atteignable depuis l'application média : un conducteur qui reste dans
     * la surface média n'aurait aucun moyen de faire rejoindre ses passagers.
     * La pochette de l'unique élément porte le QR.
     */
    private fun inviteItems(): List<MediaItem> {
        val url = coordinator.state.value.shareUrl
            ?: return listOf(infoItem("no_url", "Aucun serveur configuré"))

        val qr = QrCodeGenerator.generatePng(url, QR_SIZE_PX)
            ?: return listOf(infoItem("qr_failed", url, "Saisis cette adresse dans un navigateur"))

        return listOf(
            MediaItem.Builder()
                .setMediaId("${MediaId.PREFIX_INFO}invite")
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle("Scanne pour rejoindre 🐙")
                        .setSubtitle(url)
                        .setArtworkData(qr, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .setIsBrowsable(false)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        )
    }

    private fun customLayout(): ImmutableList<CommandButton> {
        val ui = coordinator.state.value
        val locked = ui.queueLocked
        val votes = ui.votes.current
        val required = ui.votes.required
        return ImmutableList.of(
            CommandButton.Builder()
                .setDisplayName(if (locked) "Déverrouiller la file" else "Verrouiller la file")
                .setIconResId(
                    if (locked) android.R.drawable.ic_lock_idle_lock
                    else android.R.drawable.ic_lock_lock
                )
                .setSessionCommand(SessionCommand(CMD_TOGGLE_LOCK, Bundle.EMPTY))
                .build(),
            CommandButton.Builder()
                .setDisplayName("Passer (hôte)")
                .setIconResId(android.R.drawable.ic_media_next)
                .setSessionCommand(SessionCommand(CMD_HOST_SKIP, Bundle.EMPTY))
                .build(),
            // L'hote peut aussi voter comme un passager, plutot que d'imposer
            // le saut : le decompte rend le vote lisible sans ouvrir un ecran.
            CommandButton.Builder()
                .setDisplayName("Voter pour passer ($votes/$required)")
                .setIconResId(android.R.drawable.ic_menu_sort_by_size)
                .setSessionCommand(SessionCommand(CMD_VOTE_SKIP, Bundle.EMPTY))
                .build(),
        )
    }

    companion object {
        private const val CMD_TOGGLE_LOCK = "fr.maxboudier.poulpifyauto.TOGGLE_LOCK"
        private const val CMD_HOST_SKIP = "fr.maxboudier.poulpifyauto.HOST_SKIP"
        private const val CMD_VOTE_SKIP = "fr.maxboudier.poulpifyauto.VOTE_SKIP"
        private const val QR_SIZE_PX = 512
    }
}
