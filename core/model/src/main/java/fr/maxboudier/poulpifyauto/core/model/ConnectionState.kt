package fr.maxboudier.poulpifyauto.core.model

/** État de la session hôte sur le serveur Poulpify. */
enum class ConnectionState {
    DISCONNECTED,
    AUTHENTICATING,
    CONNECTED,
    /** Connecté mais dégradé : Spotify non authentifié côté serveur, ou aucun appareil actif. */
    DEGRADED,
    RECONNECTING,
}

/** État de la connexion Spotify App Remote sur le téléphone. */
enum class RemoteState {
    NOT_INSTALLED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    UNAUTHORIZED,
}

data class UserFacingError(
    val message: String,
    val retryable: Boolean = true,
    val at: Long = System.currentTimeMillis(),
)
