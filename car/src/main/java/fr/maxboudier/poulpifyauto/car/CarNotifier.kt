package fr.maxboudier.poulpifyauto.car

import android.content.Context
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.maxboudier.poulpifyauto.core.model.SessionEvent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Notifications affichées sur l'écran de la voiture.
 *
 * Android Auto n'affiche pas les notifications ordinaires d'une application :
 * il faut passer par [CarNotificationManager] et attacher un [CarAppExtender],
 * qui porte le titre, le texte et l'importance propres à la surface voiture.
 *
 * Ce que le conducteur fait lui-même n'est jamais notifié : c'est le
 * coordinateur qui filtre ses propres actions en amont.
 */
class CarNotifier(private val context: Context) {

    private val manager = CarNotificationManager.from(context)
    private val nextId = AtomicInteger(BASE_ID)

    init {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Activité de la session")
                .setDescription("Votes, ajouts à la file et arrivées de passagers")
                .build()
        )
    }

    fun notify(event: SessionEvent) {
        val (title, text) = when (event) {
            is SessionEvent.PassengerJoined ->
                "${event.passenger.emoji} ${event.passenger.name} a rejoint" to
                    "Un passager de plus à bord"

            is SessionEvent.SkipVoteCast ->
                "Vote pour passer ⏭️" to buildString {
                    append("${event.votes.current}/${event.votes.required} vote(s)")
                    event.trackName?.let { append(" • $it") }
                }

            is SessionEvent.TrackQueued -> {
                val who = event.track.addedBy ?: "Un passager"
                val emoji = event.track.addedByEmoji.orEmpty()
                "$emoji$who a ajouté un son" to
                    // Un titre surprise le reste jusqu'a ce qu'il passe : le
                    // devoiler dans une notification viderait le jeu.
                    if (event.track.isInked) "🐙 Titre surprise"
                    else "${event.track.name} — ${event.track.artistLabel}"
            }

            // Le conducteur est le seul a pouvoir verrouiller : il vient de le
            // faire, une notification serait redondante.
            is SessionEvent.QueueLockChanged -> return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setContentTitle(title)
                    .setContentText(text)
                    .setChannelId(CHANNEL_ID)
                    .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .build()
            )

        runCatching { manager.notify(nextIdWrapped(), builder) }
    }

    /**
     * Les identifiants tournent sur une petite plage : chaque événement doit
     * apparaître séparément, mais un trajet ne doit pas accumuler des milliers
     * de notifications distinctes.
     */
    private fun nextIdWrapped(): Int {
        val id = nextId.incrementAndGet()
        if (id > BASE_ID + ID_RANGE) nextId.set(BASE_ID)
        return id
    }

    private companion object {
        const val CHANNEL_ID = "poulpify_session_activity"
        const val BASE_ID = 2_000
        const val ID_RANGE = 50
    }
}
