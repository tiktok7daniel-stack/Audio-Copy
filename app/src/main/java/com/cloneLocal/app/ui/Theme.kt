package com.cloneLocal.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cloneLocal.app.R

object C {
    val Bg = Color(0xFF04060B)
    val Surface = Color(0xFF0A0F1C)
    val SurfaceHi = Color(0xFF111A2E)
    val Line = Color(0xFF1C2740)
    val Cyan = Color(0xFF38D5FF)
    val Blue = Color(0xFF2E6BFF)
    val Violet = Color(0xFF7A5CFF)
    val Text = Color(0xFFE9F0FF)
    val Muted = Color(0xFF8590AA)
    val Faint = Color(0xFF3A4560)
    val Danger = Color(0xFFFF4D6A)
    val Success = Color(0xFF34F5B0)
    val Warn = Color(0xFFFFB547)
}

val Glow = Brush.horizontalGradient(listOf(C.Blue, C.Cyan))

class Fonts(val display: FontFamily, val body: FontFamily)

val EnFonts = Fonts(
    display = FontFamily(
        Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
        Font(R.font.rajdhani_bold, FontWeight.Bold),
    ),
    body = FontFamily(
        Font(R.font.poppins_light, FontWeight.Light),
        Font(R.font.poppins_regular, FontWeight.Normal),
    ),
)

// Rajdhani has no Arabic glyphs, so Arabic uses Tajawal for every role.
private val Tajawal = FontFamily(
    Font(R.font.tajawal_light, FontWeight.Light),
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_regular, FontWeight.SemiBold),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)
val ArFonts = Fonts(display = Tajawal, body = Tajawal)

val LocalFonts = staticCompositionLocalOf { EnFonts }
val LocalArabic = staticCompositionLocalOf { false }

/** Text styles from the design spec. `ar` drops letter spacing and uppercase concerns for Arabic. */
class Type(private val f: Fonts, private val ar: Boolean) {
    fun hero(color: Color = C.Text) = TextStyle(
        fontFamily = f.display, fontWeight = FontWeight.Bold, fontSize = 52.sp,
        lineHeight = (52 * 1.05).sp, color = color,
    )

    fun heroGlow() = TextStyle(
        fontFamily = f.display, fontWeight = FontWeight.Bold, fontSize = 52.sp,
        lineHeight = (52 * 1.05).sp,
        brush = Brush.verticalGradient(listOf(Color(0xFFBFF1FF), C.Cyan, C.Blue)),
        shadow = Shadow(C.Cyan.copy(alpha = .55f), Offset.Zero, 28f),
    )

    fun section(color: Color = C.Text) = TextStyle(
        fontFamily = f.display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color,
    )

    fun label(color: Color = C.Muted) = TextStyle(
        fontFamily = f.display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
        letterSpacing = if (ar) 0.sp else 2.2.sp, color = color,
    )

    fun button(color: Color = C.Text) = TextStyle(
        fontFamily = f.display, fontWeight = FontWeight.Bold, fontSize = 18.sp,
        letterSpacing = if (ar) 0.sp else 1.8.sp, color = color,
    )

    fun body(color: Color = C.Text, size: Int = 15) = TextStyle(
        fontFamily = f.body, fontWeight = FontWeight.Light, fontSize = size.sp,
        lineHeight = (size * 1.55).sp, color = color,
    )

    fun bodyRegular(color: Color = C.Text, size: Int = 15) = body(color, size).copy(fontWeight = FontWeight.Normal)

    fun stat(color: Color = C.Cyan, size: Int = 30) = TextStyle(
        fontFamily = f.display, fontWeight = FontWeight.Bold, fontSize = size.sp, color = color,
        shadow = Shadow(C.Cyan.copy(alpha = .4f), Offset.Zero, 18f),
    )
}

val LocalType = staticCompositionLocalOf { Type(EnFonts, false) }
