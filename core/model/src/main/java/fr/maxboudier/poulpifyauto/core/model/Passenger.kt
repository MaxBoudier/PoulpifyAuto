package fr.maxboudier.poulpifyauto.core.model

data class Passenger(
    val name: String,
    val emoji: String,
)

data class Votes(
    val current: Int,
    val required: Int,
    val hasVoted: Boolean = false,
) {
    val progress: Float get() = if (required <= 0) 0f else (current.toFloat() / required).coerceIn(0f, 1f)
}
