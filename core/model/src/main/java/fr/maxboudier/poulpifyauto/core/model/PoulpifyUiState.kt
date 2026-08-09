package fr.maxboudier.poulpifyauto.core.model

/**
 * État unique partagé par les trois surfaces (voiture, service média, téléphone).
 * Aucune des trois ne maintient son propre état : elles observent celui-ci.
 */
data class PoulpifyUiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val remote: RemoteState = RemoteState.DISCONNECTED,
    val nowPlaying: PlaybackSnapshot? = null,
    val queue: List<Track> = emptyList(),
    val passengers: List<Passenger> = emptyList(),
    val votes: Votes = Votes(0, 1),
    val queueLocked: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val topTracks: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val shareUrl: String? = null,
    val lastError: UserFacingError? = null,
) {
    val isHostReady: Boolean get() = connection == ConnectionState.CONNECTED
}
