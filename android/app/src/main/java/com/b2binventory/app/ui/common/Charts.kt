package com.b2binventory.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** One bar: label on the left, value text on the right. */
data class BarItem(val label: String, val value: Double, val valueLabel: String, val key: Long? = null)

/**
 * Ranked horizontal bars. Rows are tappable (onClick) and each bar is scaled to the largest value.
 * Readable on small screens because labels and values are text, not drawn on the canvas.
 */
@Composable
fun HorizontalBarChart(
    items: List<BarItem>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    emptyText: String = "No data for this period",
    onClick: ((BarItem) -> Unit)? = null,
) {
    if (items.isEmpty() || items.all { it.value == 0.0 }) {
        ChartEmpty(emptyText, modifier)
        return
    }
    val max = items.maxOf { it.value }.coerceAtLeast(1e-9)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Column(
                Modifier.fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable { onClick(item) } else Modifier)
                    .semantics { contentDescription = "${item.label}: ${item.valueLabel}" },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(item.valueLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val r = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(track, cornerRadius = r)
                    val w = (size.width * (item.value / max)).toFloat().coerceAtLeast(if (item.value > 0) size.height else 0f)
                    drawRoundRect(color, size = Size(w, size.height), cornerRadius = r)
                }
            }
        }
    }
}

/**
 * Line chart with an area fill. Tap anywhere to show the nearest point's label and value.
 * xLabels are shown for the first, middle and last points to stay readable.
 */
@Composable
fun LineChart(
    points: List<Pair<String, Double>>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    emptyText: String = "No sales in this period",
) {
    if (points.isEmpty() || points.all { it.second == 0.0 }) {
        ChartEmpty(emptyText, modifier)
        return
    }
    var selected by remember(points) { mutableIntStateOf(points.indices.maxBy { points[it].second }) }
    val max = points.maxOf { it.second }.coerceAtLeast(1e-9)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    Column(modifier.fillMaxWidth()) {
        val p = points[selected]
        Text("${p.first}: ${valueLabel(p.second)}", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(180.dp)
                .semantics { contentDescription = "Trend chart, highest ${valueLabel(max)}" }
                .pointerInput(points) {
                    detectTapGestures { pos ->
                        val step = if (points.size > 1) size.width / (points.size - 1f) else 0f
                        selected = if (step == 0f) 0 else (pos.x / step).roundToInt().coerceIn(points.indices)
                    }
                },
        ) {
            val step = if (points.size > 1) size.width / (points.size - 1) else 0f
            val h = size.height
            fun pt(i: Int) = Offset(if (points.size == 1) size.width / 2 else i * step,
                h - (points[i].second / max * (h * 0.9f)).toFloat())
            for (g in 1..3) {
                val y = h - h * 0.9f * g / 3
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            val line = Path().apply { points.indices.forEach { i -> if (i == 0) moveTo(pt(i).x, pt(i).y) else lineTo(pt(i).x, pt(i).y) } }
            val area = Path().apply {
                addPath(line)
                lineTo(pt(points.lastIndex).x, h)
                lineTo(pt(0).x, h)
                close()
            }
            drawPath(area, color.copy(alpha = 0.15f))
            drawPath(line, color, style = Stroke(width = 3.dp.toPx()))
            val s = pt(selected)
            drawLine(grid, Offset(s.x, 0f), Offset(s.x, h), strokeWidth = 2f)
            drawCircle(surface, radius = 6.dp.toPx(), center = s)
            drawCircle(color, radius = 4.dp.toPx(), center = s)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(points.first().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (points.size > 2) {
                Text(points[points.size / 2].first, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
            }
            if (points.size > 1) {
                Text(points.last().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Current vs previous period as two labelled bars. */
@Composable
fun ComparisonBars(
    title: String,
    current: Double,
    previous: Double,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BarItem("This period", current, valueLabel(current)),
        BarItem("Previous period", previous, valueLabel(previous)),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalBarChart(items, emptyText = "No sales in either period")
    }
}

@Composable
private fun ChartEmpty(text: String, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().height(96.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Default.BarChart, null, tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
