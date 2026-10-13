package app.fireplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun NetWorthSparkline(points: List<Double>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(88.dp)) {
        if (points.isEmpty()) return@Canvas
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val coords = points.mapIndexed { index, value ->
            val x = if (points.size == 1) size.width / 2f else index * size.width / (points.size - 1)
            val y = size.height - ((value - min) / span).toFloat() * (size.height - 8f) - 4f
            Offset(x, y)
        }
        if (coords.size >= 2) {
            val path = Path()
            coords.forEachIndexed { index, offset ->
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        coords.forEach { offset ->
            drawCircle(color = color, radius = 7f, center = offset)
        }
    }
}
