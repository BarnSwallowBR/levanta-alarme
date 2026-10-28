package br.com.barnswallow.levanta.ui

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.barnswallow.levanta.R

/**
 * Identidade "amanhecer": fundo azul-noite e um sol âmbar que nasce. O gradiente quente
 * aparece só onde importa (próximo alarme, botão Desligar, ícone); o resto fica calmo.
 * Contrastes conferidos para WCAG AA sobre os fundos escuros.
 */
object Dawn {
    val Night = Color(0xFF0E1024)       // fundo
    val Dusk = Color(0xFF181B36)        // cards
    val DuskHigh = Color(0xFF232750)    // teclas, chips
    val Ink = Color(0xFFF3F1FF)         // texto principal (17:1 no fundo)
    val Mist = Color(0xFFB3B5D9)        // texto secundário (8:1 nos cards)
    val Haze = Color(0xFF7C80AD)        // texto desligado, só em tamanho grande (4:1)
    val Sun = Color(0xFFFFB547)         // acento principal (11:1 no fundo)
    val Ember = Color(0xFFFF7A59)       // segundo tom do nascer do sol
    val Mint = Color(0xFF5EE6A8)        // acerto
    val Coral = Color(0xFFFF6B7A)       // erro
    val OnSun = Color(0xFF2B1700)

    /** Nascer do sol, para o botão principal e destaques. */
    val sunrise: Brush = Brush.horizontalGradient(listOf(Sun, Ember))

    /** Céu clareando, para o card do próximo alarme e a tela do alarme tocando. */
    val sky: Brush = Brush.verticalGradient(listOf(Color(0xFF1C1F45), Color(0xFF3A2147), Color(0xFF5A2A3C)))
}

val Bricolage = FontFamily(
    Font(R.font.bricolage_400, FontWeight.Normal),
    Font(R.font.bricolage_600, FontWeight.SemiBold),
    Font(R.font.bricolage_800, FontWeight.ExtraBold),
)

/** Números de relógio e contas: algarismos de largura fixa, para não "dançarem". */
val ClockStyle = TextStyle(
    fontFamily = Bricolage,
    fontWeight = FontWeight.ExtraBold,
    fontFeatureSettings = "tnum",
    letterSpacing = (-2).sp,
)

private val LevantaColors = darkColorScheme(
    primary = Dawn.Sun,
    onPrimary = Dawn.OnSun,
    primaryContainer = Color(0xFF4A3214),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Dawn.Ember,
    onSecondary = Color(0xFF2E0B00),
    secondaryContainer = Color(0xFF3B2A52),
    onSecondaryContainer = Color(0xFFEAE0FF),
    tertiary = Dawn.Mint,
    onTertiary = Color(0xFF002714),
    background = Dawn.Night,
    onBackground = Dawn.Ink,
    surface = Dawn.Night,
    onSurface = Dawn.Ink,
    surfaceVariant = Dawn.DuskHigh,
    onSurfaceVariant = Dawn.Mist,
    surfaceContainerLowest = Color(0xFF0A0C1C),
    surfaceContainerLow = Color(0xFF14172F),
    surfaceContainer = Dawn.Dusk,
    surfaceContainerHigh = Dawn.DuskHigh,
    surfaceContainerHighest = Color(0xFF2C3160),
    outline = Color(0xFF6B6F9C),
    outlineVariant = Color(0xFF34385F),
    error = Dawn.Coral,
    onError = Color(0xFF33000A),
    errorContainer = Color(0xFF4A1424),
    onErrorContainer = Color(0xFFFFDADF),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Bricolage,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val LevantaType = Typography(
    displayLarge = style(64, 68, FontWeight.ExtraBold, -1.5),
    displayMedium = style(48, 54, FontWeight.ExtraBold, -1.0),
    displaySmall = style(36, 42, FontWeight.ExtraBold, -0.5),
    headlineLarge = style(32, 38, FontWeight.ExtraBold, -0.5),
    headlineMedium = style(28, 34, FontWeight.ExtraBold, -0.25),
    headlineSmall = style(24, 30, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(18, 24, FontWeight.SemiBold),
    titleSmall = style(16, 22, FontWeight.SemiBold),
    bodyLarge = style(17, 25, FontWeight.Normal),
    bodyMedium = style(15, 22, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(16, 20, FontWeight.SemiBold),
    labelMedium = style(14, 18, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.SemiBold),
)

private val LevantaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun LevantaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LevantaColors, typography = LevantaType, shapes = LevantaShapes, content = content)
}

/** true quando o usuário desligou as animações do sistema: aí o pulso e o tremor ficam parados. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/** Ícones desenhados aqui para não depender do pacote grande de ícones. */
object LevantaIcons {
    /** Sol nascendo sobre o horizonte: a marca do app. */
    val Sunrise: ImageVector by lazy {
        ImageVector.Builder("Sunrise", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(5f, 17f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 17f)
                close()
            }
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(2f, 20f); lineTo(22f, 20f)
                moveTo(12f, 2.5f); lineTo(12f, 6.5f)
                moveTo(4.2f, 6.7f); lineTo(6.6f, 9.1f)
                moveTo(19.8f, 6.7f); lineTo(17.4f, 9.1f)
            }
        }.build()
    }

    val Qr: ImageVector by lazy {
        ImageVector.Builder("Qr", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
                fun finder(x: Float, y: Float) {
                    moveTo(x, y); lineTo(x + 8f, y); lineTo(x + 8f, y + 8f); lineTo(x, y + 8f); close()
                    moveTo(x + 2f, y + 2f); lineTo(x + 2f, y + 6f); lineTo(x + 6f, y + 6f); lineTo(x + 6f, y + 2f); close()
                }
                finder(3f, 3f)
                finder(13f, 3f)
                finder(3f, 13f)
                listOf(13f to 13f, 17f to 13f, 15f to 15f, 13f to 17f, 19f to 17f, 17f to 19f, 13f to 19f, 19f to 13f).forEach { (x, y) ->
                    moveTo(x, y); lineTo(x + 2f, y); lineTo(x + 2f, y + 2f); lineTo(x, y + 2f); close()
                }
            }
        }.build()
    }

    /** "+ −" para a missão de contas. */
    val Math: ImageVector by lazy {
        ImageVector.Builder("Math", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2.2f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(7f, 3.5f); lineTo(7f, 10.5f)
                moveTo(3.5f, 7f); lineTo(10.5f, 7f)
                moveTo(13.5f, 7f); lineTo(20.5f, 7f)
                moveTo(4f, 15f); lineTo(10f, 21f)
                moveTo(10f, 15f); lineTo(4f, 21f)
                moveTo(13.5f, 16f); lineTo(20.5f, 16f)
                moveTo(13.5f, 20f); lineTo(20.5f, 20f)
            }
        }.build()
    }
}
