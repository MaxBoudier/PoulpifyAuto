package fr.maxboudier.poulpifyauto

import android.app.Application
import fr.maxboudier.poulpifyauto.core.session.PoulpifyGraph

class PoulpifyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Point d'initialisation unique du graphe. Le service media et le
        // service voiture peuvent demarrer sans passer par l'activite : c'est
        // ici, et nulle part ailleurs, que tout est cable.
        PoulpifyGraph.init(
            context = this,
            spotifyClientId = BuildConfig.SPOTIFY_CLIENT_ID,
            spotifyRedirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
            debugLogging = BuildConfig.DEBUG,
        )
    }
}
