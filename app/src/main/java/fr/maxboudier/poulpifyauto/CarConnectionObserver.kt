package fr.maxboudier.poulpifyauto

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import fr.maxboudier.poulpifyauto.core.session.PoulpifyGraph
import fr.maxboudier.poulpifyauto.media.PoulpifyMediaLibraryService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Démarre la session hôte dès que le téléphone se branche à Android Auto.
 *
 * C'est la demande centrale : le conducteur branche son téléphone, la session
 * s'ouvre, il n'a rien à saisir. Sans cet observateur, il faudrait ouvrir
 * l'application et appuyer sur un bouton avant chaque trajet.
 */
class CarConnectionObserver(private val context: Context) : Observer<Int> {

    private var lastType = CarConnection.CONNECTION_TYPE_NOT_CONNECTED

    fun observe(owner: LifecycleOwner) {
        CarConnection(context).type.observe(owner, this)
    }

    override fun onChanged(value: Int) {
        if (value == lastType) return
        lastType = value

        when (value) {
            CarConnection.CONNECTION_TYPE_PROJECTION,
            CarConnection.CONNECTION_TYPE_NATIVE,
            -> onCarConnected()

            else -> Log.d(TAG, "Voiture déconnectée (type=$value)")
        }
    }

    private fun onCarConnected() {
        val autoStart = runCatching {
            runBlocking { PoulpifyGraph.settings.config.first().autoStartOnCarConnect }
        }.getOrDefault(true)

        if (!autoStart) {
            Log.d(TAG, "Démarrage auto désactivé dans les réglages.")
            return
        }

        // Demarrer le service media suffit : c'est lui qui prend une reference
        // sur la session hote et porte le premier plan.
        val intent = Intent(context, PoulpifyMediaLibraryService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { Log.w(TAG, "Impossible de démarrer le service média", it) }
    }

    private companion object {
        const val TAG = "CarConnection"
    }
}
