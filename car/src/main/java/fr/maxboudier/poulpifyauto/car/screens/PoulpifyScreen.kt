package fr.maxboudier.poulpifyauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState
import fr.maxboudier.poulpifyauto.core.session.PoulpifyGraph
import fr.maxboudier.poulpifyauto.core.session.SessionCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Écran relié à l'état partagé.
 *
 * Chaque écran déclare, via [stateKey], la partie de l'état qui le concerne :
 * on ne redessine que quand cette partie change. Sans ce filtre, la moindre
 * milliseconde de progression déclencherait un `invalidate()`, que le host
 * Android Auto limite de toute façon et qui ferait clignoter la liste.
 */
abstract class PoulpifyScreen(carContext: CarContext) : Screen(carContext) {

    protected val coordinator: SessionCoordinator = PoulpifyGraph.coordinator

    protected val state: PoulpifyUiState get() = coordinator.state.value

    /** Signature de ce qui doit provoquer un redessin. */
    protected open fun stateKey(state: PoulpifyUiState): Any = state

    private var collectJob: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                collectJob = owner.lifecycleScope.launch {
                    coordinator.state
                        .distinctUntilChangedBy { stateKey(it) }
                        .collect { invalidate() }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                collectJob?.cancel()
                collectJob = null
            }
        })
    }

    /**
     * Nombre maximal d'éléments que le host accepte d'afficher. On l'interroge
     * au lieu de coder une valeur en dur : la limite dépend de la voiture et
     * de l'état de conduite.
     */
    protected fun listContentLimit(): Int =
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

    protected fun gridContentLimit(): Int =
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)

    protected fun toast(message: String) {
        CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show()
    }

    /**
     * Lance une action et rapporte son échec au conducteur. Une commande qui
     * échoue en silence est la pire des expériences en voiture : c'est ce que
     * faisait l'ancienne version pour chacune de ses actions.
     */
    protected fun runAction(successMessage: String? = null, action: suspend () -> Result<Unit>) {
        lifecycleScope.launch {
            val result = action()
            result.fold(
                onSuccess = { successMessage?.let { toast(it) } },
                onFailure = { toast(it.message ?: "Action impossible.") },
            )
        }
    }
}
