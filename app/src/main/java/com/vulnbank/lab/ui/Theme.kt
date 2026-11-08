package com.vulnbank.lab.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF031229)
val Accent = Color(0xFF15D6FF)
val Subdued = Color(0xFFADCBE9)
val Panel = Color(0xFF0B264A)
val Bad = Color(0xFFFF8E8E)

private val scheme = darkColorScheme(
    primary = Accent,
    secondary = Color(0xFF82BCFF),
    background = Ink,
    surface = Panel,
    onSurface = Color(0xFFEAF2FA),
    onBackground = Color(0xFFEAF2FA),
    onPrimary = Color(0xFF00182D),
    error = Bad
)

@Composable fun BankTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
