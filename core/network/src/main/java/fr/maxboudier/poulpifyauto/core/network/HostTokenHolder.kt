package fr.maxboudier.poulpifyauto.core.network

/**
 * Détenteur en mémoire du jeton hôte courant.
 *
 * Le jeton est éphémère par conception (le serveur en émet un nouveau à
 * chaque login) : il n'a pas besoin d'être persisté sur le téléphone, ce
 * qu'on persiste c'est le mot de passe hôte pour pouvoir se reconnecter.
 * [AuthInterceptor] lit cette valeur sur chaque requête sortante.
 */
class HostTokenHolder {
    @Volatile
    var token: String? = null
}
