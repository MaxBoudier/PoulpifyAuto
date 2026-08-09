package fr.maxboudier.poulpifyauto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Rose néon de l'identité Poulpify, repris du site. */
val PoulpifyPink = Color(0xFFFF0084)
private val PoulpifyPinkDark = Color(0xFFD4006E)
private val SurfaceDark = Color(0xFF121212)
private val SurfaceElevatedDark = Color(0xFF1E1E22)

private val DarkColors = darkColorScheme(
    primary = PoulpifyPink,
    onPrimary = Color.White,
    secondary = PoulpifyPinkDark,
    background = Color.Black,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = Color(0xFF9CA3AF),
    error = Color(0xFFEF4444),
)

private val LightColors = lightColorScheme(
    primary = PoulpifyPinkDark,
    onPrimary = Color.White,
    secondary = PoulpifyPink,
)

@Composable
fun PoulpifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
