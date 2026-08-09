package fr.maxboudier.poulpifyauto.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** Ajoute `Authorization: Bearer <hostToken>` quand un jeton est disponible. */
class AuthInterceptor(private val tokenHolder: HostTokenHolder) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenHolder.token
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
