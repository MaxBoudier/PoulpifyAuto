package fr.maxboudier.poulpifyauto.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

private val poulpifyJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Construit le client HTTP et l'API Retrofit pour une URL de serveur donnée.
 * Recréé quand l'utilisateur change l'URL du serveur dans les réglages.
 */
object NetworkModule {

    fun createOkHttpClient(tokenHolder: HostTokenHolder, debugLogging: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenHolder))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        // Le corps des requetes contient le mot de passe hote : ce log ne doit
        // jamais tourner en release, contrairement a l'ancienne app qui le
        // laissait actif inconditionnellement.
        if (debugLogging) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }
        return builder.build()
    }

    fun createApi(baseUrl: String, client: OkHttpClient): PoulpifyApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(poulpifyJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PoulpifyApi::class.java)
    }

    fun createEventsClient(baseUrl: String, client: OkHttpClient): PoulpifyEventsClient {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return PoulpifyEventsClient(normalized + "api/events", client, poulpifyJson)
    }
}
