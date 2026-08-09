package fr.maxboudier.poulpifyauto.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.maxboudier.poulpifyauto.car.screens.DashboardScreen
import fr.maxboudier.poulpifyauto.core.session.PoulpifyGraph

class PoulpifyCarAppService : CarAppService() {

    /**
     * En debug on accepte n'importe quel host pour pouvoir tester avec le
     * Desktop Head Unit. En release on s'en tient à la liste signée fournie
     * par la bibliothèque : accepter tout le monde laisserait n'importe quelle
     * application se faire passer pour le système de la voiture.
     */
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = PoulpifySession()
}

class PoulpifySession : Session() {

    init {
        // `Session` est un LifecycleOwner : on relache la session hote quand
        // Android Auto detruit l'ecran, sans quoi le compteur de references
        // ne redescendrait jamais a zero.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                PoulpifyGraph.coordinator.release()
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        // La session hote doit vivre tant que l'ecran voiture est ouvert, meme
        // si le service media n'a pas encore ete lie par Android Auto.
        PoulpifyGraph.coordinator.acquire()
        return DashboardScreen(carContext)
    }
}
