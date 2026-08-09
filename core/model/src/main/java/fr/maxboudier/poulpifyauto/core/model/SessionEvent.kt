package fr.maxboudier.poulpifyauto.core.model

/**
 * Ce qui vient d'arriver dans la session et mérite d'être signalé au
 * conducteur — par une notification sur l'écran de la voiture.
 *
 * Ces événements sont déduits en comparant deux instantanés successifs du
 * serveur : le flux SSE décrit un état, pas des transitions.
 */
sealed interface SessionEvent {

    /** Un passager vient de rejoindre la session. */
    data class PassengerJoined(val passenger: Passenger) : SessionEvent

    /** Quelqu'un a voté pour passer le titre en cours. */
    data class SkipVoteCast(val votes: Votes, val trackName: String?) : SessionEvent

    /** Un passager a ajouté un titre à la file. */
    data class TrackQueued(val track: Track) : SessionEvent

    /** La file a été verrouillée ou déverrouillée. */
    data class QueueLockChanged(val locked: Boolean) : SessionEvent
}
