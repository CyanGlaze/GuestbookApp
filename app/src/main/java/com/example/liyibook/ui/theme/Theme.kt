package com.example.liyibook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌色沿用小程序：主色喜庆红 #c8323c，强调蓝 #5b6fd8
val BrandRed = Color(0xFFC8323C)
val BrandBlue = Color(0xFF5B6FD8)
val BgGray = Color(0xFFF2F2F7)

private val LightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADB),
    secondary = BrandBlue,
    onSecondary = Color.White,
    background = BgGray,
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    error = Color(0xFFFA5151)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A8F),
    onPrimary = Color(0xFF69000B),
    secondary = Color(0xFFB4C4FF),
    background = Color(0xFF1C1C1E),
    surface = Color(0xFF2C2C2E)
)

@Composable
fun LiJinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
