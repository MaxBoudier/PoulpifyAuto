package fr.maxboudier.poulpifyauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.maxboudier.poulpifyauto.core.model.PoulpifyUiState

class PassengersScreen(carContext: CarContext) : PoulpifyScreen(carContext) {

    override fun stateKey(state: PoulpifyUiState): Any = state.passengers

    override fun onGetTemplate(): Template {
        val passengers = state.passengers
        val limit = listContentLimit()

        val list = ItemList.Builder().apply {
            if (passengers.isEmpty()) {
                setNoItemsMessage("Personne à bord. Partage le QR code pour inviter tes passagers.")
            } else {
                passengers.take(limit).forEach { passenger ->
                    addItem(
                        Row.Builder()
                            .setTitle("${passenger.emoji} ${passenger.name}")
                            .build()
                    )
                }
            }
        }.build()

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Passagers (${passengers.size})")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(list)
            .build()
    }
}
