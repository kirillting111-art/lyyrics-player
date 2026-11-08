package com.example.lyricsplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SWidget(
    val type: String,
    val title: String = "",
    val sub: String = "",
    val emoji: String = "",
    val from: Float = 0f,
    val to: Float = 0f,
    val unit: String = "",
    val items: List<String> = emptyList(),
    val color: Color = Color(0xFF34C759)
)

data class SBanner(val app: String, val text: String)

data class SLine(
    val i: Int,
    val hue: Float?,
    val widgets: List<SWidget>,
    val banner: SBanner?
)

fun kindWidget(k: WKind) = SWidget("kind:" + k.name)

val SCENE_PROMPT = """
Ты арт-директор клипа с живым экраном. Ниже синхронный текст песни в формате: номер строки|время|текст.
Придумай «сцену»: на нужных строках экран показывает 2 виджета, которые иллюстрируют смысл строки (как системные виджеты iOS), и иногда всплывающее уведомление сверху. Меняй виджеты примерно каждые 2-4 строки, не на каждой. В ответе не цитируй текст песни.
Верни ТОЛЬКО JSON без пояснений в таком виде:
{"lines":[{"i":0,"bg":280,"w":[{"t":"counter","title":"ВНИМАНИЕ","sub":"взглядов в секунду","emoji":"👁","from":0,"to":11,"unit":"","color":"#FF6B6B"},{"t":"ring","title":"КРУГ","sub":"смотрят на вас","from":0,"to":100,"unit":"%","color":"#0A84FF"}],"banner":{"app":"НАЙТИ","text":"Рядом 12 человек смотрят на вас"}}]}
Поле i - номер строки, с которой сцена действует (до следующего i). bg - оттенок фона 0-360. banner необязателен.
Типы виджетов t: counter (число от from до to, рядом unit), bar (полоса 0-100), ring (кольцо 0-100), spark (число и растущий график от from до to), wheel (крутящееся колесо выбора), grid (сетка из 18 точек, to - сколько закрашено 0-18), map (карта с границей и точкой), emoji (крупный emoji и подпись sub), list (items до 4 коротких строк), clock, visualizer, stopwatch, compass, heart.
В w ровно 2 виджета, разных по типу. Заголовки, подписи и уведомления короткие, на русском, придумывай их по смыслу строк.
""".trimIndent()

private fun parseCol(s: String?): Color {
    return try {
        Color(android.graphics.Color.parseColor(s))
    } catch (e: Exception) {
        Color(0xFF34C759)
    }
}

fun parseScene(raw: String): List<SLine>? {
    return try {
        val s = raw.indexOf('{')
        val e = raw.lastIndexOf('}')
        if (s < 0 || e <= s) return null
        val arr = JSONObject(raw.substring(s, e + 1)).getJSONArray("lines")
        val out = mutableListOf<SLine>()
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            val ws = mutableListOf<SWidget>()
            val wa = o.optJSONArray("w")
            if (wa != null) {
                for (j in 0 until minOf(wa.length(), 2)) {
                    val w = wa.getJSONObject(j)
                    val items = mutableListOf<String>()
                    val ia = w.optJSONArray("items")
                    if (ia != null) {
                        for (n in 0 until minOf(ia.length(), 4)) items.add(ia.optString(n))
                    }
                    ws.add(
                        SWidget(
                            type = w.optString("t", "emoji"),
                            title = w.optString("title"),
                            sub = w.optString("sub"),
                            emoji = w.optString("emoji"),
                            from = w.optDouble("from", 0.0).toFloat(),
                            to = w.optDouble("to", 0.0).toFloat(),
                            unit = w.optString("unit"),
                            items = items,
                            color = parseCol(if (w.has("color")) w.optString("color") else null)
                        )
                    )
                }
            }
            val b = o.optJSONObject("banner")
            out.add(
                SLine(
                    i = o.optInt("i"),
                    hue = if (o.has("bg")) o.optDouble("bg").toFloat().coerceIn(0f, 360f) else null,
                    widgets = ws,
                    banner = if (b != null) SBanner(b.optString("app"), b.optString("text")) else null
                )
            )
        }
        out.sortedBy { it.i }
    } catch (ex: Exception) {
        null
    }
}

data class ItunesHit(val title: String, val artist: String, val cover: Bitmap?)

fun looksSame(a: String, b: String): Boolean {
    val x = a.lowercase().filter { it.isLetterOrDigit() }
    val y = b.lowercase().filter { it.isLetterOrDigit() }
    return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
}

fun searchItunes(query: String): ItunesHit? {
    return try {
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" +
            URLEncoder.encode(query, "UTF-8")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        if (c.responseCode != 200) return null
        val body = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(body).optJSONArray("results") ?: return null
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        val art = o.optString("artworkUrl100").replace("100x100", "600x600")
        val bmp = if (art.isNotBlank()) {
            URL(art).openStream().use { BitmapFactory.decodeStream(it) }
        } else null
        ItunesHit(o.optString("trackName"), o.optString("artistName"), bmp)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SceneWidget(w: SWidget, bars: FloatArray, micGranted: Boolean, onMic: () -> Unit) {
    if (w.type.startsWith("kind:")) {
        val k = try {
            WKind.valueOf(w.type.removePrefix("kind:"))
        } catch (e: Exception) {
            WKind.CLOCK
        }
        WidgetView(k, bars, micGranted, onMic)
        return
    }
    when (w.type) {
        "clock" -> WidgetView(WKind.CLOCK, bars, micGranted, onMic)
        "visualizer" -> WidgetView(WKind.VIS, bars, micGranted, onMic)
        "stopwatch" -> WidgetCard(w.title.ifBlank { "СЕКУНДОМЕР" }, w.sub) { StopwatchWidget() }
        "compass" -> WidgetCard(w.title.ifBlank { "КОМПАС" }, w.sub) { CompassWidget() }
        "heart" -> WidgetCard(w.title.ifBlank { "ПУЛЬС" }, w.sub) { HeartWidget() }
        "counter" -> WidgetCard(w.title, w.sub) { CounterWidget(w) }
        "bar" -> WidgetCard(w.title, w.sub) { BarWidget(w) }
        "ring" -> WidgetCard(w.title, w.sub) { RingWidget(w) }
        "list" -> WidgetCard(w.title, w.sub) { ListWidget(w) }
        "spark" -> WidgetCard(w.title, w.sub) { SparkWidget(w) }
        "wheel" -> WidgetCard(w.title, w.sub) { WheelWidget(w) }
        "grid" -> WidgetCard(w.title, w.sub) { GridWidget(w) }
        "map" -> WidgetCard(w.title, w.sub) { MapWidget() }
        else -> WidgetCard(w.title, "") {
            EmojiWidget(w.emoji.ifBlank { "✨" }, w.sub, Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun CounterWidget(w: SWidget) {
    val a = remember(w) { Animatable(w.from) }
    LaunchedEffect(w) { a.animateTo(w.to, tween(2500)) }
    val whole = w.from == w.from.toInt().toFloat() && w.to == w.to.toInt().toFloat()
    val shown = if (whole) "${a.value.toInt()}" else "%.1f".format(a.value)
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (w.emoji.isNotBlank()) {
            Text(w.emoji, fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
        }
        Text(shown, color = Color.White, fontSize = 38.sp)
        if (w.unit.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(w.unit, color = w.color, fontSize = 13.sp)
        }
    }
}

@Composable
fun BarWidget(w: SWidget) {
    val a = remember(w) { Animatable(w.from) }
    LaunchedEffect(w) { a.animateTo(w.to, tween(2500)) }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((a.value / 100f).coerceIn(0f, 1f))
                    .background(w.color)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${a.value.toInt()}${if (w.unit.isBlank()) "%" else w.unit}",
            color = Color.White,
            fontSize = 24.sp
        )
    }
}

@Composable
fun RingWidget(w: SWidget) {
    val a = remember(w) { Animatable(w.from) }
    LaunchedEffect(w) { a.animateTo(w.to, tween(2500)) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
        ) {
            val stroke = size.minDimension * 0.12f
            val tl = Offset(stroke / 2, stroke / 2)
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.15f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = tl,
                size = sz,
                style = Stroke(stroke)
            )
            drawArc(
                color = w.color,
                startAngle = -90f,
                sweepAngle = 360f * (a.value / 100f).coerceIn(0f, 1f),
                useCenter = false,
                topLeft = tl,
                size = sz,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            "${a.value.toInt()}${if (w.unit.isBlank()) "%" else w.unit}",
            color = Color.White,
            fontSize = 16.sp
        )
    }
}

@Composable
fun ListWidget(w: SWidget) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        w.items.forEach { t ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(w.color)
                )
                Spacer(Modifier.width(8.dp))
                Text(t, color = Color.White, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun SparkWidget(w: SWidget) {
    val a = remember(w) { Animatable(w.from) }
    LaunchedEffect(w) { a.animateTo(w.to, tween(2500)) }
    val range = (w.to - w.from).let { if (it == 0f) 1f else it }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (w.emoji.isNotBlank()) {
                Text(w.emoji, fontSize = 24.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text("${a.value.toInt()}", color = Color.White, fontSize = 38.sp)
            if (w.unit.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(w.unit, color = w.color, fontSize = 12.sp)
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
        ) {
            val frac = ((a.value - w.from) / range).coerceIn(0f, 1f)
            val p = Path()
            val n = 24
            for (i in 0..n) {
                val t = i.toFloat() / n
                val x = size.width * t
                val y = size.height * (1f - t * t * frac)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, w.color, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun WheelWidget(w: SWidget) {
    val rot = remember(w) { Animatable(0f) }
    LaunchedEffect(w) { rot.animateTo(1080f, tween(3500, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2 + 6f)
        val r = minOf(size.width, size.height) / 2 * 0.8f
        val cols = listOf(
            Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A),
            Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFFBF5AF2)
        )
        val n = cols.size
        for (i in 0 until n) {
            drawArc(
                color = cols[i],
                startAngle = rot.value + i * 360f / n,
                sweepAngle = 360f / n,
                useCenter = true,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(2 * r, 2 * r)
            )
        }
        val p = Path().apply {
            moveTo(c.x, c.y - r + 4f)
            lineTo(c.x - 10f, c.y - r - 12f)
            lineTo(c.x + 10f, c.y - r - 12f)
            close()
        }
        drawPath(p, Color.White)
    }
}

@Composable
fun GridWidget(w: SWidget) {
    val a = remember(w) { Animatable(0f) }
    LaunchedEffect(w) { a.animateTo(1f, tween(2000)) }
    val total = 18
    val on = (w.to.toInt().coerceIn(0, total) * a.value).toInt()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        for (row in 0 until 3) {
            Row(
                Modifier.padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (col in 0 until 6) {
                    val idx = row * 6 + col
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(if (idx < on) w.color else Color.White.copy(alpha = 0.15f))
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("$on из $total", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
fun MapWidget() {
    val t = rememberInfiniteTransition(label = "map")
    val pulse by t.animateFloat(
        0.6f, 1.4f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
    ) {
        drawRect(Color(0xFF1E3A2A))
        drawLine(
            Color(0xFF2F5D3F),
            Offset(0f, size.height * 0.7f),
            Offset(size.width, size.height * 0.25f),
            strokeWidth = 14f
        )
        drawLine(
            Color(0xFFFFD60A),
            Offset(size.width * 0.15f, size.height),
            Offset(size.width * 0.6f, 0f),
            strokeWidth = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
        val me = Offset(size.width * 0.38f, size.height * 0.52f)
        drawCircle(Color(0xFF0A84FF).copy(alpha = 0.3f), 18f * pulse, me)
        drawCircle(Color(0xFF0A84FF), 7f, me)
    }
}
