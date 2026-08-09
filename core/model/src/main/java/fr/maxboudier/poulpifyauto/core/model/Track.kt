package fr.maxboudier.poulpifyauto.core.model

/** Titre Spotify tel qu'affiché dans l'app, indépendant de la source (App Remote ou serveur). */
data class Track(
    val id: String,
    val uri: String,
    val name: String,
    val artists: List<String>,
    val albumName: String?,
    val imageUrl: String?,
    val durationMs: Long,
    val addedViaPoulpify: Boolean = false,
    val addedBy: String? = null,
    val addedByEmoji: String? = null,
    val addedByHost: Boolean = false,
    /** Titre "surprise" masqué tant qu'il n'est pas passé à l'antenne. */
    val isInked: Boolean = false,
) {
    val artistLabel: String get() = artists.joinToString(", ").ifBlank { "Artiste inconnu" }
}
