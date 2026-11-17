package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val KasKuDarkColorScheme = darkColorScheme(
    primary = EmeraldMintLight,
    onPrimary = OnMintTint,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = MintTintContainer,
    secondary = GoldAmberLight,
    onSecondary = OnGoldContainer,
    secondaryContainer = GoldDarkContainer,
    onSecondaryContainer = GoldContainer,
    tertiary = CoralLight,
    onTertiary = OnTerracottaContainer,
    tertiaryContainer = CoralDarkContainer,
    onTertiaryContainer = TerracottaContainer,
    background = ObsidianBackground,
    onBackground = InkLightPrimary,
    surface = ObsidianSurface,
    onSurface = InkLightPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = InkLightSecondary,
    outline = ObsidianOutline,
    outlineVariant = ObsidianSurfaceVariant,
    error = CoralLight,
    onError = OnTerracottaContainer,
    errorContainer = CoralDarkContainer,
    onErrorContainer = TerracottaContainer
)

private val KasKuLightColorScheme = lightColorScheme(
    primary = ForestEmerald,
    onPrimary = AlabasterSurface,
    primaryContainer = MintTintContainer,
    onPrimaryContainer = OnMintTint,
    secondary = AntiqueGold,
    onSecondary = AlabasterSurface,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = TerracottaRed,
    onTertiary = AlabasterSurface,
    tertiaryContainer = TerracottaContainer,
    onTertiaryContainer = OnTerracottaContainer,
    background = CreamBackground,
    onBackground = InkPrimary,
    surface = AlabasterSurface,
    onSurface = InkPrimary,
    surfaceVariant = WarmSandSurface,
    onSurfaceVariant = InkSecondary,
    outline = SoftStoneOutline,
    outlineVariant = SubtleDivider,
    error = TerracottaRed,
    onError = AlabasterSurface,
    errorContainer = TerracottaContainer,
    onErrorContainer = OnTerracottaContainer
)

val KasKuShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KasKuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> KasKuDarkColorScheme
        else -> KasKuLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KasKuTypography,
        shapes = KasKuShapes,
        content = content
    )
}
