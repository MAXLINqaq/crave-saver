package com.cravesaver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 忍住页强调色：暖橙 */
val ResistedOrange = Color(0xFFEF6C00)

/** 吃一笔页强调色：绿 */
val AteGreen = Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = ResistedOrange
)
private val DarkColors = darkColorScheme()

@Composable
fun CraveSaverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
