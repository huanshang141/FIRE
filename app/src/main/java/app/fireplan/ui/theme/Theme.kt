package app.fireplan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Pine = Color(0xFF1F4D3A)
private val PineDark = Color(0xFF143528)
private val Mist = Color(0xFFF6F3EC)
private val Paper = Color(0xFFFFFDF8)
private val Ink = Color(0xFF1C1B19)
private val Clay = Color(0xFF8A5A32)
private val Rose = Color(0xFF8E1D2C)
private val Leaf = Color(0xFF9DCEB5)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8DF),
    onPrimaryContainer = PineDark,
    secondary = Clay,
    onSecondary = Color.White,
    background = Mist,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E1D6),
    onSurfaceVariant = Color(0xFF4E4943),
    outline = Color(0xFF8A847A),
    error = Rose,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Leaf,
    onPrimary = PineDark,
    primaryContainer = Pine,
    onPrimaryContainer = Color(0xFFD7E8DF),
    secondary = Color(0xFFE2B48A),
    onSecondary = Color(0xFF3D2612),
    background = Color(0xFF141714),
    onBackground = Color(0xFFE6E2D8),
    surface = Color(0xFF1C211D),
    onSurface = Color(0xFFE6E2D8),
    surfaceVariant = Color(0xFF2A312C),
    onSurfaceVariant = Color(0xFFC9C2B6),
    outline = Color(0xFF8E887E),
    error = Color(0xFFFFB3B8),
    onError = Color(0xFF680014),
)

@Composable
fun FirePlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
