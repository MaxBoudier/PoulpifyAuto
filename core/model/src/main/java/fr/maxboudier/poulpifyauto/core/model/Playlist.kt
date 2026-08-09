package fr.maxboudier.poulpifyauto.core.model

data class Playlist(
    val id: String,
    val uri: String,
    val name: String,
    val imageUrl: String?,
)

/** Section de la bibliothèque hôte affichée dans la navigation voiture. */
enum class LibrarySection {
    QUEUE, PLAYLISTS, LIKED, TOP, RECENT
}
