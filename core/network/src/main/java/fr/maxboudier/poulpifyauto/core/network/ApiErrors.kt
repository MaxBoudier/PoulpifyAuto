package fr.maxboudier.poulpifyauto.core.network

import fr.maxboudier.poulpifyauto.core.network.dto.ErrorResponseDto
import kotlinx.serialization.json.Json
import retrofit2.Response

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Traduit une réponse en échec en message exploitable par l'utilisateur.
 * Le serveur renvoie `{ error: "..." }` sur toutes ses routes en erreur ;
 * on retombe sur un message générique si le corps est absent ou illisible.
 */
fun Response<*>.readableError(): String {
    val body = errorBody()?.string()
    val fromServer = body?.let { runCatching { errorJson.decodeFromString(ErrorResponseDto.serializer(), it).error }.getOrNull() }
    if (!fromServer.isNullOrBlank()) return fromServer
    return when (code()) {
        401 -> "Session hôte expirée, reconnexion en cours…"
        403 -> "Action refusée : jeton hôte invalide."
        404 -> "Aucun appareil Spotify actif. Lance une lecture sur Spotify d'abord."
        429 -> "Trop de requêtes vers Spotify, réessaie dans quelques secondes."
        in 500..599 -> "Le serveur Poulpify ne répond pas correctement."
        else -> "Erreur inattendue (${code()})."
    }
}

/**
 * Exécute un appel réseau et convertit systématiquement l'échec en [Result].
 *
 * `reified` pour distinguer les routes qui ne renvoient rien (204/corps vide,
 * typées `Response<Unit>`) d'une réponse tronquée : sans cette distinction, un
 * corps vide sur une route censée répondre produirait un ClassCastException
 * chez l'appelant plutôt qu'une erreur lisible.
 */
suspend inline fun <reified T> safeApiCall(block: suspend () -> Response<T>): Result<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        when {
            body != null -> Result.success(body)
            T::class == Unit::class -> @Suppress("UNCHECKED_CAST") Result.success(Unit as T)
            else -> Result.failure(ApiException("Réponse vide du serveur.", response.code()))
        }
    } else {
        Result.failure(ApiException(response.readableError(), response.code()))
    }
} catch (e: java.io.IOException) {
    Result.failure(ApiException("Impossible de joindre le serveur Poulpify.", retryable = true, cause = e))
} catch (e: Exception) {
    Result.failure(ApiException("Erreur inattendue : ${e.message}", retryable = false, cause = e))
}

class ApiException(
    message: String,
    val code: Int? = null,
    val retryable: Boolean = code == null || code >= 500 || code == 429,
    cause: Throwable? = null,
) : Exception(message, cause)
