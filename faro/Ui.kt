package com.taller2.faro

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ======================================================================
// Identidad visual de Faro: tinta azul marino, papel de carta náutica y el
// magenta con el que las cartas y los GPS marcan los faros y las rutas.
// Son los mismos colores del panel web.
// ======================================================================
object FaroColors {
    val Ink = Color(0xFF0F2A3D)          // texto principal y cabeceras
    val Ink2 = Color(0xFF48606F)         // texto secundario
    val Ink3 = Color(0xFF7A8D98)         // marcas y elementos apagados
    val Paper = Color(0xFFF3F6F4)        // fondo
    val Water = Color(0xFFDCE9EF)        // superficies suaves
    val Line = Color(0xFFCBD7DD)         // bordes
    val Magenta = Color(0xFFC2187A)      // acción principal
    val Pink = Color(0xFFFF8FCB)         // luz del faro sobre fondo oscuro
    val Mist = Color(0xFFC5D3DC)         // texto secundario sobre fondo oscuro
    val Green = Color(0xFF1E7B4F)
    val GreenTint = Color(0xFFDFF2E8)
    val Amber = Color(0xFF9A5B00)
    val AmberTint = Color(0xFFFBEED3)
    val Red = Color(0xFFB93424)
    val RedTint = Color(0xFFFBE4E0)
}

private val FaroScheme = lightColorScheme(
    primary = FaroColors.Magenta,
    onPrimary = Color.White,
    secondary = FaroColors.Ink,
    onSecondary = Color.White,
    background = FaroColors.Paper,
    onBackground = FaroColors.Ink,
    surface = Color.White,
    onSurface = FaroColors.Ink,
    surfaceVariant = FaroColors.Water,
    onSurfaceVariant = FaroColors.Ink2,
    outline = FaroColors.Line,
    error = FaroColors.Red,
    onError = Color.White,
)

private val BaseType = Typography()
private val FaroType = Typography(
    headlineLarge = BaseType.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
)

/** Tema de la app. Se usa siempre el esquema claro, igual que el panel web. */
@Composable
fun FaroAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FaroScheme, typography = FaroType, content = content)
}

// ======================================================================
// El faro: punto de luz con anillos que se expanden mientras se comparte.
// Es el elemento visual que identifica a la app.
// ======================================================================
@Composable
fun Beacon(active: Boolean, modifier: Modifier = Modifier) {
    if (active) {
        // Animación infinita: 'phase' va de 0 a 1 una y otra vez
        val transition = rememberInfiniteTransition()
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2800, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        )
        Canvas(modifier) {
            val maxRadius = size.minDimension / 2f
            // Tres anillos desfasados: cada uno crece y se desvanece
            for (i in 0 until 3) {
                val f = (phase + i / 3f) % 1f
                drawCircle(
                    color = FaroColors.Pink.copy(alpha = (1f - f) * 0.6f),
                    radius = maxRadius * (0.2f + 0.8f * f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            drawCircle(color = FaroColors.Magenta, radius = maxRadius * 0.2f)
            drawCircle(color = FaroColors.Pink, radius = maxRadius * 0.1f)
        }
    } else {
        // Apagado: anillos quietos y grises
        Canvas(modifier) {
            val maxRadius = size.minDimension / 2f
            drawCircle(color = FaroColors.Ink3.copy(alpha = 0.25f), radius = maxRadius * 0.85f, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = FaroColors.Ink3.copy(alpha = 0.45f), radius = maxRadius * 0.52f, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = FaroColors.Ink3, radius = maxRadius * 0.2f)
        }
    }
}

/** Marca: el símbolo pequeño junto al nombre. */
@Composable
fun BrandRow(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(28.dp)) {
            val r = size.minDimension / 2f
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = r * 0.92f, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(color = Color.White.copy(alpha = 0.7f), radius = r * 0.58f, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(color = FaroColors.Pink, radius = r * 0.27f)
        }
        Spacer(Modifier.width(10.dp))
        Text("Faro", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ======================================================================
// Componentes reutilizables
// ======================================================================

/** Botón principal (magenta). Muestra un indicador mientras 'loading' es true. */
@Composable
fun FaroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text)
    }
}

/** Botón secundario con borde. 'color' cambia el tono (rojo para acciones destructivas). */
@Composable
fun FaroOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = FaroColors.Ink, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) {
        Text(text)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 24.dp, bottom = 8.dp),
        color = FaroColors.Ink2,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Etiqueta de estado redondeada. */
@Composable
fun Pill(text: String, fill: Color, ink: Color) {
    Text(
        text,
        modifier = Modifier.background(fill, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
        color = ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Mensaje dentro de la pantalla: rojo si es un error, verde si es una confirmación. */
@Composable
fun Notice(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .background(if (isError) FaroColors.RedTint else FaroColors.GreenTint, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (isError) FaroColors.Red else FaroColors.Green,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Tarjeta blanca con borde fino. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, FaroColors.Line),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

// ======================================================================
// Iconos de la barra inferior, dibujados a mano en un lienzo de 24 x 24.
// Así la app no depende de ninguna librería de iconos.
// ======================================================================
private fun iconStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun IconShare(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawCircle(color = color, radius = 3f * u)
        drawCircle(color = color, radius = 7f * u, style = iconStroke(1.8f * u))
        drawCircle(color = color.copy(alpha = 0.5f), radius = 10.6f * u, style = iconStroke(1.8f * u))
    }
}

@Composable
fun IconPermissions(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawCircle(color = color, radius = 9.6f * u, style = iconStroke(1.8f * u))
        val check = Path()
        check.moveTo(7.6f * u, 12.3f * u)
        check.lineTo(10.7f * u, 15.3f * u)
        check.lineTo(16.5f * u, 9.1f * u)
        drawPath(path = check, color = color, style = iconStroke(1.9f * u))
    }
}

@Composable
fun IconRoute(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val line = Path()
        line.moveTo(5f * u, 18.5f * u)
        line.lineTo(10f * u, 9.5f * u)
        line.lineTo(14.5f * u, 14.5f * u)
        line.lineTo(19f * u, 5.5f * u)
        drawPath(path = line, color = color, style = iconStroke(1.8f * u))
        drawCircle(color = color, radius = 2.4f * u, center = Offset(5f * u, 18.5f * u))
        drawCircle(color = color, radius = 2.4f * u, center = Offset(19f * u, 5.5f * u))
    }
}

@Composable
fun IconAccount(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawCircle(color = color, radius = 3.7f * u, center = Offset(12f * u, 8.4f * u), style = iconStroke(1.8f * u))
        val shoulders = Path()
        shoulders.moveTo(4.6f * u, 20f * u)
        shoulders.cubicTo(4.6f * u, 15.8f * u, 8f * u, 14f * u, 12f * u, 14f * u)
        shoulders.cubicTo(16f * u, 14f * u, 19.4f * u, 15.8f * u, 19.4f * u, 20f * u)
        drawPath(path = shoulders, color = color, style = iconStroke(1.8f * u))
    }
}
