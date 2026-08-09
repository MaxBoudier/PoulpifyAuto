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

    /**
     * Quelqu'un a voté pour passer, sans que le seuil soit atteint.
     * [remaining] est le nombre de voix qu'il manque encore.
     */
    data class SkipVoteCast(val votes: Votes, val remaining: Int, val trackName: String?) : SessionEvent

    /** Le vote a abouti : le titre est passé. */
    data class SkipVotePassed(val trackName: String?) : SessionEvent

    /**
     * Un passager a ajouté un titre à la file.
     *
     * Le titre n'est volontairement pas transporté : la notification ne doit
     * pas révéler ce qui a été ajouté, seulement qu'il se passe quelque chose.
     */
    data class TrackQueued(val by: String?, val emoji: String?, val queueSize: Int) : SessionEvent

    /** La file a été verrouillée ou déverrouillée. */
    data class QueueLockChanged(val locked: Boolean) : SessionEvent
}
