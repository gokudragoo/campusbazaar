package app.campusbazaar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.campusbazaar.R

/*
 * CampusBazaar palette: four colours, no gradients.
 *   Paper    #F3F4F7  cool, neutral page
 *   Ink      #16213E  blue-black fountain-pen ink: text and primary actions
 *   Marigold #F5B800  second-hand price stickers: price tags and the Sell action (ink text on top, 8.8:1)
 *   Stamp    #C62E2E  rubber-stamp red: SOLD/RESERVED stamps, destructive actions, errors (5.8:1 on white)
 * Everything else is a tint of these.
 */
object CB {
    val Paper = Color(0xFFF3F4F7)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF16213E)
    val InkSoft = Color(0xFF545C70)
    val InkFaint = Color(0xFF8A90A0)
    val Hairline = Color(0xFFDCDFE6)
    val InkWash = Color(0xFFE8EAF0)
    val Marigold = Color(0xFFF5B800)
    val MarigoldWash = Color(0xFFFDF1C9)
    val Stamp = Color(0xFFC62E2E)
    val StampWash = Color(0xFFF9E3E3)
}

@OptIn(ExperimentalTextApi::class)
private fun anek(weight: Int, width: Float) = Font(
    R.font.anek_latin,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/** Anek Latin (Ek Type, Mumbai) at normal width for reading. */
val AnekText = FontFamily(anek(400, 100f), anek(500, 100f), anek(600, 100f), anek(700, 100f))

/** Condensed cut for prices on tags: the same family, narrowed like a printed sticker. */
val AnekCondensed = FontFamily(anek(600, 75f), anek(700, 75f), anek(800, 75f))

/** Expanded cut for screen titles. */
val AnekWide = FontFamily(anek(600, 118f), anek(700, 118f))

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = AnekWide, fontWeight = FontWeight(700), fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = AnekWide, fontWeight = FontWeight(700), fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = AnekWide, fontWeight = FontWeight(700), fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = AnekText, fontWeight = FontWeight(600), fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = AnekText, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = AnekText, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = AnekText, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = AnekText, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = AnekText, fontWeight = FontWeight(400), fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = AnekText, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AnekText, fontWeight = FontWeight(600), fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = AnekText, fontWeight = FontWeight(500), fontSize = 11.5.sp, lineHeight = 14.sp),
)

object PriceText {
    val Large = TextStyle(fontFamily = AnekCondensed, fontWeight = FontWeight(800), fontSize = 30.sp, lineHeight = 32.sp, color = CB.Ink)
    val Medium = TextStyle(fontFamily = AnekCondensed, fontWeight = FontWeight(800), fontSize = 19.sp, lineHeight = 21.sp, color = CB.Ink)
    val Small = TextStyle(fontFamily = AnekCondensed, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 17.sp, color = CB.Ink)
}

private val colors = lightColorScheme(
    primary = CB.Ink,
    onPrimary = Color.White,
    primaryContainer = CB.InkWash,
    onPrimaryContainer = CB.Ink,
    secondary = CB.Marigold,
    onSecondary = CB.Ink,
    secondaryContainer = CB.MarigoldWash,
    onSecondaryContainer = CB.Ink,
    tertiary = CB.Ink,
    background = CB.Paper,
    onBackground = CB.Ink,
    surface = CB.Surface,
    onSurface = CB.Ink,
    surfaceVariant = CB.InkWash,
    onSurfaceVariant = CB.InkSoft,
    surfaceContainerLowest = CB.Surface,
    surfaceContainerLow = CB.Surface,
    surfaceContainer = CB.Surface,
    surfaceContainerHigh = CB.Surface,
    surfaceContainerHighest = CB.InkWash,
    outline = CB.Hairline,
    outlineVariant = CB.Hairline,
    error = CB.Stamp,
    onError = Color.White,
    errorContainer = CB.StampWash,
    onErrorContainer = CB.Stamp,
    scrim = Color(0x9916213E),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun CampusBazaarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
