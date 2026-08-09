package fr.maxboudier.poulpifyauto.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ApiErrorsTest {

    @Test
    fun `remonte le message d erreur du serveur`() = runTest {
        val body = """{"error":"Aucun appareil Spotify actif."}"""
            .toResponseBody("application/json".toMediaType())

        val result = safeApiCall<Unit> { Response.error(404, body) }

        assertTrue(result.isFailure)
        assertEquals(
            "Aucun appareil Spotify actif.",
            (result.exceptionOrNull() as ApiException).message,
        )
    }

    @Test
    fun `retombe sur un message lisible quand le corps est vide`() = runTest {
        val body = "".toResponseBody("application/json".toMediaType())

        val result = safeApiCall<Unit> { Response.error(403, body) }

        val error = result.exceptionOrNull() as ApiException
        assertEquals("Action refusée : jeton hôte invalide.", error.message)
        assertFalse("un 403 ne doit pas etre retente en boucle", error.retryable)
    }

    @Test
    fun `un 500 est marque comme retentable`() = runTest {
        val body = "".toResponseBody("application/json".toMediaType())

        val result = safeApiCall<Unit> { Response.error(503, body) }

        assertTrue((result.exceptionOrNull() as ApiException).retryable)
    }

    @Test
    fun `une route sans contenu reussit malgre un corps vide`() = runTest {
        val result = safeApiCall { Response.success<Unit>(null) }

        assertTrue(result.isSuccess)
    }

    /**
     * Garde-fou : avant, un corps vide sur une route typée produisait un
     * ClassCastException chez l'appelant au lieu d'une erreur exploitable.
     */
    @Test
    fun `un corps vide sur une route typee produit une erreur, pas un crash`() = runTest {
        val result = safeApiCall { Response.success<StatusHolder>(null) }

        assertTrue(result.isFailure)
        assertEquals("Réponse vide du serveur.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `une panne reseau devient une erreur retentable`() = runTest {
        val result = safeApiCall<Unit> { throw java.io.IOException("boom") }

        val error = result.exceptionOrNull() as ApiException
        assertTrue(error.retryable)
        assertEquals("Impossible de joindre le serveur Poulpify.", error.message)
    }

    private data class StatusHolder(val value: String)
}
