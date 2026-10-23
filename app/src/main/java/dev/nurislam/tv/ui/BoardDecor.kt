package dev.nurislam.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import dev.nurislam.tv.model.ScheduleEvent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArchitecturalBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val s = minOf(w / 1080f, h / 1920f)
        drawRect(Brush.verticalGradient(listOf(BoardColors.Deep, BoardColors.Background, BoardColors.Deep)))
        drawRect(Brush.radialGradient(listOf(BoardColors.Emerald.copy(alpha = .5f), Color.Transparent), Offset(w * .5f, h * .3f), w * .7f))
        val step = 60f * s
        // Restrained geometric border; the information area stays quiet.
        for (x in 0..(w / step).toInt()) for (y in 0..(h / step).toInt()) {
            if (x * step < w * .065f || x * step > w * .935f || y * step > h * .94f) {
                val cx = x * step
                val cy = y * step
                val p = Path().apply { moveTo(cx, cy - step * .5f); lineTo(cx + step * .5f, cy); lineTo(cx, cy + step * .5f); lineTo(cx - step * .5f, cy); close() }
                drawPath(p, BoardColors.Gold.copy(alpha = .12f), style = Stroke(1.5f * s))
                drawCircle(BoardColors.Gold.copy(alpha = .1f), step * .31f, Offset(cx, cy), style = Stroke(1f * s))
            }
        }
        val arch = Path().apply {
            moveTo(w * .035f, h * .93f); lineTo(w * .035f, h * .20f)
            cubicTo(w * .035f, h * .15f, w * .08f, h * .14f, w * .115f, h * .13f)
            cubicTo(w * .11f, h * .07f, w * .22f, h * .07f, w * .22f, h * .045f)
            quadraticTo(w * .23f, 0f, w * .5f, -h * .04f)
            quadraticTo(w * .77f, 0f, w * .78f, h * .045f)
            cubicTo(w * .78f, h * .07f, w * .89f, h * .07f, w * .885f, h * .13f)
            cubicTo(w * .92f, h * .14f, w * .965f, h * .15f, w * .965f, h * .20f)
            lineTo(w * .965f, h * .93f)
        }
        drawPath(arch, BoardColors.Gold.copy(alpha = .12f), style = Stroke(15f * s))
        drawPath(arch, BoardColors.Outline.copy(alpha = .6f), style = Stroke(3f * s))
        // Architectural silhouettes, all vector Canvas shapes (no reference bitmap in the APK).
        for (side in listOf(.15f, .85f)) {
            val x = w * side
            val y = h * .32f
            drawRect(BoardColors.Deep.copy(alpha = .6f), Offset(x - 8 * s, y - 185 * s), Size(16 * s, 245 * s))
            drawOval(BoardColors.Deep.copy(alpha = .65f), Offset(x - 16 * s, y - 212 * s), Size(32 * s, 42 * s))
            drawLine(BoardColors.Gold.copy(alpha = .12f), Offset(x, y - 190 * s), Offset(x, y + 40 * s), 2 * s)
        }
    }
}

@Composable
fun Crescent(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.height * .43f
        val c = Offset(size.width * .5f, size.height * .5f)
        val crescent = Path().apply {
            moveTo(c.x + r * .3f, c.y - r)
            cubicTo(c.x - r * 1.4f, c.y - r, c.x - r * 1.25f, c.y + r * 1.4f, c.x + r * .45f, c.y + r * .7f)
            cubicTo(c.x - r * .65f, c.y + r, c.x - r * .8f, c.y - r * .55f, c.x + r * .3f, c.y - r)
            close()
        }
        drawPath(crescent, Brush.linearGradient(listOf(BoardColors.Text, BoardColors.Gold)))
        val star = Path()
        val center = Offset(c.x + r * .55f, c.y - r * .25f)
        repeat(10) { i ->
            val a = -PI / 2 + i * PI / 5
            val radius = if (i % 2 == 0) r * .36f else r * .15f
            val x = center.x + cos(a).toFloat() * radius
            val y = center.y + sin(a).toFloat() * radius
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close(); drawPath(star, BoardColors.Gold)
    }
}

@Composable
fun Ornament(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.height * .29f
        drawLine(BoardColors.Outline.copy(alpha = .7f), Offset(0f, c.y), Offset(c.x - r * 2, c.y), 1.5f)
        drawLine(BoardColors.Outline.copy(alpha = .7f), Offset(c.x + r * 2, c.y), Offset(size.width, c.y), 1.5f)
        val star = Path()
        repeat(16) { i ->
            val a = i * PI / 8
            val radius = if (i % 2 == 0) r else r * .6f
            val p = Offset(c.x + cos(a).toFloat() * radius, c.y + sin(a).toFloat() * radius)
            if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
        }
        star.close(); drawPath(star, BoardColors.Gold, style = Stroke(1.5f))
    }
}

@Composable
fun EventIcon(event: ScheduleEvent, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { scale(size.width / 48f, size.height / 48f, pivot = Offset.Zero) {
        if (event.isPrayer) mosque(color) else {
            val center = Offset(24f, 25f)
            drawCircle(color, 9f, center, style = Stroke(1.8f))
            repeat(8) { i ->
                val a = i * PI / 4
                drawLine(color, Offset(24 + cos(a).toFloat() * 14, 25 + sin(a).toFloat() * 14), Offset(24 + cos(a).toFloat() * 19, 25 + sin(a).toFloat() * 19), 1.7f)
            }
            if (event != ScheduleEvent.ZAWAL) drawLine(color, Offset(4f, 38f), Offset(44f, 38f), 2f)
        }
    } }
}

private fun DrawScope.mosque(color: Color) {
    val dome = Path().apply {
        moveTo(10f, 24f); cubicTo(10f, 14f, 24f, 14f, 24f, 5f)
        cubicTo(24f, 14f, 38f, 14f, 38f, 24f); close()
    }
    drawPath(dome, color)
    drawRect(color, Offset(10f, 24f), Size(28f, 18f))
    for (x in listOf(14f, 22f, 30f)) {
        drawRoundRect(BoardColors.Card, Offset(x, 29f), Size(4f, 14f), androidx.compose.ui.geometry.CornerRadius(2f, 3f))
    }
    drawLine(color, Offset(6f, 43f), Offset(42f, 43f), 2f)
    drawLine(color, Offset(24f, 5f), Offset(24f, 1f), 1.5f)
}
