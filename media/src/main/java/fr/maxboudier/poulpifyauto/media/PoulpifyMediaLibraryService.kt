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

        // Les notifications vivent ici et non dans la session Car App
        // Library : celle-ci n'existe que si le conducteur ouvre le tableau de
        // bord, alors que ce service tourne des qu'Android Auto est connecte.
        val notifier = CarNotifier(this)
        scope.launch {
            coordinator.events.collect { notifier.notify(it) }
        }

        // Le lecteur n'a pas de source d'evenements propre : c'est l'etat
        // partage qui le pilote.
        scope.launch {
            coordinator.state.collect { player.onUpstreamStateChanged() }
        }

        // Le libelle du bouton de vote porte le decompte : sans cette mise a
        // jour, il resterait fige sur la valeur affichee a la connexion.
        scope.launch {
            coordinator.state
                .map { it.votes to it.queueLocked }
                .distinctUntilChanged()
                .collect {
                    librarySession?.setMediaButtonPreferences(customLayout())
                    // Le decompte figure dans le libelle de l'entree de
                    // navigation : la racine doit etre reconstruite.
                    librarySession?.notifyChildrenChanged(MediaId.ROOT, rootNodes().size, null)
                }
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
                .add(SessionCommand(CMD_SHOW_QR, Bundle.EMPTY))
                .add(SessionCommand(CMD_VOTE_SKIP, Bundle.EMPTY))
                .build()

            android.util.Log.i(
                "PoulpifyMedia",
                "onConnect pkg=${controller.packageName} v=${controller.controllerVersion} " +
                    "boutons=${customLayout().size} cmds=${sessionCommands.commands.size}",
            )
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                // `setMediaButtonPreferences` et non `setCustomLayout` : c'est
                // ce modele que media3 1.11 convertit en actions personnalisees
                // de la PlaybackStateCompat, la seule que lit Android Auto.
                .setMediaButtonPreferences(customLayout())
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
                    session.setMediaButtonPreferences(customLayout())
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
                CMD_SHOW_QR -> {
                    showQrOnArtwork()
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
                CMD_VOTE_SKIP -> {
                    coordinator.voteSkip()
                    session.setMediaButtonPreferences(customLayout())
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
            MediaId.isAction(mediaId) -> when (MediaId.payload(mediaId)) {
                MediaId.ACTION_VOTE_SKIP -> coordinator.voteSkip()
                MediaId.ACTION_TOGGLE_LOCK -> coordinator.toggleQueueLock()
            }
            MediaId.isPlayNow(mediaId) -> coordinator.playNow(MediaId.payload(mediaId))
            MediaId.isQueueAdd(mediaId) -> {
                val uri = MediaId.payload(mediaId)
                val track = allKnownTracks().firstOrNull { it.uri == uri }
                if (track != null) coordinator.addToQueue(track)
            }
        }
    }

    /**
     * Bascule la pochette sur le QR d'invitation quelques secondes.
     *
     * La pochette est le seul grand emplacement de l'écran de lecture ; les
     * templates ne permettent pas d'afficher une image de cette taille
     * ailleurs. Le retour automatique évite de laisser un écran qui ne
     * correspond plus à ce qui joue.
     */
    private fun showQrOnArtwork() {
        val url = coordinator.state.value.shareUrl ?: return
        val qr = QrCodeGenerator.generatePng(url, QR_SIZE_PX, QrCodeGenerator.loadLogo(this))
            ?: return
        player.flashQrArtwork(qr, QR_FLASH_MS)
    }

    /** Élément de liste qui déclenche une action au lieu de jouer un titre. */
    private fun actionItem(action: String, title: String, subtitle: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(MediaId.action(action))
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(false)
                    // Doit etre "playable" pour que le host le rende cliquable.
                    .setIsPlayable(true)
                    .build()
            )
            .build()

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
            actionItem(
                MediaId.ACTION_VOTE_SKIP,
                "⏭️ Voter pour passer (${ui.votes.current}/${ui.votes.required})",
                ui.nowPlaying?.track?.name?.let { "Titre en cours : $it" },
            ),
            actionItem(
                MediaId.ACTION_TOGGLE_LOCK,
                if (ui.queueLocked) "🔓 Déverrouiller la file" else "🔒 Verrouiller la file",
                if (ui.queueLocked) "Les passagers ne peuvent plus ajouter"
                else "Les passagers peuvent ajouter des sons",
            ),
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

        val qr = QrCodeGenerator.generatePng(url, QR_SIZE_PX, QrCodeGenerator.loadLogo(this))
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

    /**
     * Boutons affichés dans l'écran de lecture d'Android Auto.
     *
     * `setCustomIconResId` avec une vraie ressource est **indispensable** :
     * Android Auto lit la `PlaybackStateCompat` héritée, dont les actions
     * personnalisées exigent un identifiant de drawable. Avec une simple
     * constante d'icône sémantique, media3 n'émettait aucune action et les
     * boutons restaient invisibles (`custom actions=[]` dans dumpsys).
     *
     * Le bouton « suivant » du transport reste un saut immédiat d'hôte ; voter
     * est une action distincte, avec son décompte lisible sur le libellé.
     */
    private fun customLayout(): ImmutableList<CommandButton> {
        val ui = coordinator.state.value
        val locked = ui.queueLocked
        val votes = ui.votes.current
        val required = ui.votes.required

        return ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_THUMB_UP_UNFILLED)
                .setDisplayName("Voter pour passer ($votes/$required)")
                .setCustomIconResId(R.drawable.ic_vote_skip)
                .setSessionCommand(SessionCommand(CMD_VOTE_SKIP, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(true)
                .build(),
            CommandButton.Builder(
                if (locked) CommandButton.ICON_FLAG_FILLED else CommandButton.ICON_FLAG_UNFILLED
            )
                .setDisplayName(if (locked) "Déverrouiller la file" else "Verrouiller la file")
                .setCustomIconResId(
                    if (locked) R.drawable.ic_queue_locked else R.drawable.ic_queue_open
                )
                .setSessionCommand(SessionCommand(CMD_TOGGLE_LOCK, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(true)
                .build(),
            // Le bouton « suivant » du transport fait deja le saut immediat :
            // cette place est mieux employee par le QR d'invitation.
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("Afficher le QR d'invitation")
                .setCustomIconResId(R.drawable.ic_show_qr)
                .setSessionCommand(SessionCommand(CMD_SHOW_QR, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(true)
                .build(),
        )
    }

    companion object {
        private const val CMD_TOGGLE_LOCK = "fr.maxboudier.poulpifyauto.TOGGLE_LOCK"
        private const val CMD_SHOW_QR = "fr.maxboudier.poulpifyauto.SHOW_QR"
        private const val QR_FLASH_MS = 8_000L
        private const val CMD_VOTE_SKIP = "fr.maxboudier.poulpifyauto.VOTE_SKIP"
        private const val QR_SIZE_PX = 512
    }
}
