package com.altften.recorder.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

val OrangePrimary = Color(0xFFFF6D00)
val OrangeVariant = Color(0xFFE65100)
val GreenSuccess = Color(0xFF00E676)
val RedStop = Color(0xFFFF5252)
val DarkSurface = Color(0xFF1E1E24)
val DarkBackground = Color(0xFF000000)

val WearColorPalette = Colors(
    primary = OrangePrimary,
    primaryVariant = OrangeVariant,
    secondary = GreenSuccess,
    background = DarkBackground,
    surface = DarkSurface,
    error = RedStop,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.White
)
