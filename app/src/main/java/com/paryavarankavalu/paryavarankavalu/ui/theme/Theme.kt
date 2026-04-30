package com.paryavarankavalu.paryavarankavalu.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Stitch Design System: Paryavaran Kavalu ─────────────────────────────────

private val StitchLightColorScheme = lightColorScheme(
    primary              = GreenPrimary,
    onPrimary            = OnGreenPrimary,
    primaryContainer     = GreenPrimaryContainer,
    onPrimaryContainer   = OnGreenPrimaryContainer,
    inversePrimary       = InversePrimary,

    secondary            = Secondary,
    onSecondary          = OnSecondary,
    secondaryContainer   = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary             = Tertiary,
    onTertiary           = OnTertiary,
    tertiaryContainer    = TertiaryContainer,
    onTertiaryContainer  = OnTertiaryContainer,

    background           = Background,
    onBackground         = OnBackground,

    surface              = Surface,
    onSurface            = OnSurface,
    surfaceVariant       = SurfaceVariant,
    onSurfaceVariant     = OnSurfaceVariant,
    surfaceTint          = SurfaceTint,
    inverseSurface       = InverseSurface,
    inverseOnSurface     = InverseOnSurface,

    error                = ErrorColor,
    onError              = OnError,
    errorContainer       = ErrorContainer,
    onErrorContainer     = OnErrorContainer,

    outline              = Outline,
    outlineVariant       = OutlineVariant,

    scrim                = Color(0xFF000000),
)

private val StitchDarkColorScheme = darkColorScheme(
    primary              = InversePrimary,          // #4AE176 on dark
    onPrimary            = Color(0xFF002109),
    primaryContainer     = GreenPrimary,
    onPrimaryContainer   = Color(0xFF6BFF8F),
    inversePrimary       = GreenPrimary,

    secondary            = Color(0xFF96D5A3),
    onSecondary          = Color(0xFF003919),
    secondaryContainer   = Secondary,
    onSecondaryContainer = SecondaryContainer,

    tertiary             = Color(0xFFAFCEBA),
    onTertiary           = Color(0xFF042014),
    tertiaryContainer    = Tertiary,
    onTertiaryContainer  = TertiaryContainer,

    background           = Color(0xFF0E150E),
    onBackground         = Color(0xFFDCE5D9),
    surface              = Color(0xFF0E150E),
    onSurface            = Color(0xFFDCE5D9),
    surfaceVariant       = Color(0xFF3D4A3D),
    onSurfaceVariant     = OutlineVariant,
    surfaceTint          = InversePrimary,
    inverseSurface       = SurfaceContainerHighest,
    inverseOnSurface     = OnSurface,

    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),

    outline              = Outline,
    outlineVariant       = Color(0xFF3D4A3D),
    scrim                = Color(0xFF000000),
)

@Composable
fun ParyavaranKavaluTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled – use Stitch brand colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> StitchDarkColorScheme
        else      -> StitchLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use sage background for status bar for a nature-forward look
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
