package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme =
  darkColorScheme(
    primary = ArgCelesteBright,
    onPrimary = Color(0xFF04192E),
    primaryContainer = ArgCelesteContainerDark,
    onPrimaryContainer = ArgCelesteContainerLight,
    secondary = ArgSolGold,
    onSecondary = Color(0xFF2B1B00),
    secondaryContainer = ArgSolContainerDark,
    onSecondaryContainer = ArgSolContainerLight,
    tertiary = EmeraldIncome,
    onTertiary = ArgNightBg,
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = EmeraldContainerLight,
    error = CoralExpense,
    onError = Color.White,
    errorContainer = CoralContainerDark,
    onErrorContainer = CoralContainerLight,
    background = ArgNightBg,
    onBackground = DarkTextPrimary,
    surface = ArgNightSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = ArgNightSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = ArgNightBorder,
    outlineVariant = ArgNightOutlineVariant,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ArgCelestePrimary,
    onPrimary = Color.White,
    primaryContainer = ArgCelesteContainerLight,
    onPrimaryContainer = ArgCelesteOnContainerLight,
    secondary = ArgSolAmber,
    onSecondary = Color.White,
    secondaryContainer = ArgSolContainerLight,
    onSecondaryContainer = ArgSolOnContainerLight,
    tertiary = Color(0xFF047857),
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = Color(0xFF022C22),
    error = Color(0xFFBE123C),
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = Color(0xFF4C0519),
    background = ArgLightBg,
    onBackground = ArgTextPrimary,
    surface = ArgLightSurface,
    onSurface = ArgTextPrimary,
    surfaceVariant = ArgLightVariant,
    onSurfaceVariant = ArgTextSecondary,
    outline = ArgLightOutline,
    outlineVariant = ArgLightOutlineVariant,
  )

val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun MalvinasProTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
