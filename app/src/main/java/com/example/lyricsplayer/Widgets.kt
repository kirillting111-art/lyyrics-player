package com.example.lyricsplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Слово с "=" на конце должно совпасть целиком, остальные - по началу слова.
enum class WKind(
    val title: String,
    val sub: String,
    val bannerApp: String,
    val bannerText: String,
    val words: List<String>
) {
    CLOCK("CLOCK", "Минималистичные часы", "", "", listOf()),
    VIS("AUDIO VISUALIZER", "Живой спектр аудиосигнала", "", "", listOf()),
    BATTERY("АККУМУЛЯТОРЫ", "Зарядка идёт", "АККУМУЛЯТОР", "Заряд 100%, всё готово",
        listOf("заряд", "батаре", "энерги")),
    SPEED("БЕГ", "Скорость растёт", "ФИТНЕС", "Темп растёт",
        listOf("бег", "бежа", "скорост", "гонк", "быстр", "мчи", "лечу", "летим")),
    WEATHER("ПОГОДА", "Осадки 100%", "ПОГОДА", "Дождь, осадки 100%",
        listOf("дожд", "слез", "слёз", "снег", "холод", "туч", "гроз", "ливен")),
    COMPASS("КОМПАС", "Куда идём", "КАРТЫ", "Маршрут построен",
        listOf("круг", "компас", "север", "дорог", "путь", "иду", "идём", "идем")),
    STOPWATCH("СЕКУНДОМЕР", "Время идёт", "СЕКУНДОМЕР", "Отсчёт пошёл",
        listOf("врем", "часы", "час=", "секунд", "минут")),
    EYE("FACE ID", "Распознаём лицо", "FACE ID", "Глаза распознаны",
        listOf("глаз", "смотр", "взгляд", "вижу", "видел", "видишь")),
    CANDLE("СВЕЧА", "Горит", "ЗАМЕТКИ", "Свеча горит",
        listOf("свеч", "огонь", "огня", "пламя", "горит", "жжёт", "жжет")),
    MOON("ФОКУС", "Не беспокоить", "ФОКУСИРОВАНИЕ", "Режим «Не беспокоить» включён",
        listOf("ноч", "луна", "луны", "сон=", "спать", "тишин", "молч")),
    HEART("ПУЛЬС", "Сердце бьётся", "ЗДОРОВЬЕ", "Пульс участился",
        listOf("сердц", "любов", "люблю", "любим", "нежн")),
    MONEY("КОШЕЛЁК", "Баланс", "КОШЕЛЁК", "Баланс пополнен",
        listOf("деньг", "бабк", "бабло", "куш=", "долл", "рубл", "богат", "налик")),
    HEAVEN("НЕБО", "Свет сверху", "ФОТО", "Найдено: небо",
        listOf("бог=", "бога", "богу", "боже", "небо", "небес", "ангел", "рай=",
            "молитв", "вера=", "верю"))
}

fun detectWidgets(text: String): List<WKind> {
    val tokens = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
    return WKind.values().filter { k ->
        k.words.isNotEmpty() && k.words.any { w ->
            tokens.any { t ->
                if (w.endsWith("=")) t == w.dropLast(1) else t.startsWith(w)
            }
        }
    }
}

@Composable
fun WidgetCard(title: String, sub: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, maxLines = 1)
        Text(sub, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
fun WidgetView(kind: WKind, bars: FloatArray, micGranted: Boolean, onRequestMic: () -> Unit) {
    WidgetCard(kind.title, kind.sub) {
        when (kind) {
            WKind.CLOCK -> ClockWidget()
            WKind.VIS -> VisualizerWidget(bars, micGranted, onRequestMic)
            WKind.BATTERY -> BatteryWidget()
            WKind.SPEED -> SpeedWidget()
            WKind.WEATHER -> EmojiWidget("🌧", "+4° · осадки 100%")
            WKind.COMPASS -> CompassWidget()
            WKind.STOPWATCH -> StopwatchWidget()
            WKind.EYE -> EmojiWidget("👁", "Глаза распознаны", Color(0xFF34C759))
            WKind.CANDLE -> EmojiWidget("🕯", "Горит")
            WKind.MOON -> EmojiWidget("🌙", "Не беспокоить")
            WKind.HEART -> HeartWidget()
            WKind.MONEY -> MoneyWidget()
            WKind.HEAVEN -> EmojiWidget("✨", "Свет сверху")
        }
    }
}

@Composable
fun Banner(app: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC1C1C1E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF0A84FF))
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(app, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
            Text(text, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun EmojiWidget(emoji: String, line: String, color: Color = Color.White.copy(alpha = 0.8f)) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 38.sp)
        Text(line, color = color, fontSize = 12.sp)
    }
}

@Composable
fun ClockWidget() {
    var now by remember { mutableStateOf(Date()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE, d MMMM", Locale.getDefault()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(timeFmt.format(now), color = Color.White, fontSize = 38.sp)
        Text(dateFmt.format(now), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
fun VisualizerWidget(bars: FloatArray, granted: Boolean, onRequest: () -> Unit) {
    if (!granted) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                )
            ) { Text("Включить", fontSize = 12.sp) }
        }
    } else {
        Canvas(Modifier.fillMaxSize()) {
            val n = bars.size
            val step = size.width / n
            val w = step * 0.65f
            bars.forEachIndexed { i, v ->
                val h = size.height * v.coerceIn(0.04f, 1f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(i * step, size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2, w / 2)
                )
            }
        }
    }
}

@Composable
fun BatteryWidget() {
    val a = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(2500)) }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(a.value)
                    .background(Color(0xFF34C759))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("${(a.value * 100).toInt()}%", color = Color.White, fontSize = 26.sp)
    }
}

@Composable
fun SpeedWidget() {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(22f, tween(2500)) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${a.value.toInt()}", color = Color.White, fontSize = 38.sp)
            Spacer(Modifier.width(6.dp))
            Text("км/ч", color = Color(0xFFFF6B6B), fontSize = 12.sp)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
        ) {
            val p = Path()
            val n = 20
            for (i in 0..n) {
                val x = size.width * i / n
                val y = size.height * (1f - (i.toFloat() / n) * (a.value / 22f))
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, Color(0xFFFF6B6B), style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun CompassWidget() {
    val t = rememberInfiniteTransition(label = "compass")
    val ang by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "angle"
    )
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) / 2 * 0.9f
        drawCircle(Color.White.copy(alpha = 0.25f), r, c, style = Stroke(3f))
        val rad = (ang - 90f) * PI.toFloat() / 180f
        val tip = Offset(c.x + r * 0.8f * cos(rad), c.y + r * 0.8f * sin(rad))
        drawLine(Color(0xFFFF453A), c, tip, strokeWidth = 6f, cap = StrokeCap.Round)
        drawCircle(Color.White, 6f, c)
    }
}

@Composable
fun StopwatchWidget() {
    var ms by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val t0 = System.nanoTime()
        while (true) {
            ms = (System.nanoTime() - t0) / 1_000_000
            delay(40)
        }
    }
    val s = ms / 1000
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(
            "%d:%02d.%02d".format(s / 60, s % 60, (ms % 1000) / 10),
            color = Color.White,
            fontSize = 32.sp
        )
    }
}

@Composable
fun HeartWidget() {
    val t = rememberInfiniteTransition(label = "heart")
    val s by t.animateFloat(
        1f, 1.3f,
        infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "scale"
    )
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "❤️",
            fontSize = 38.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = s
                scaleY = s
            }
        )
        Text("92 уд/мин", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
fun MoneyWidget() {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(100000f, tween(2500)) }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("💰", fontSize = 34.sp)
        Text("%,d ₽".format(a.value.toInt()), color = Color.White, fontSize = 20.sp)
    }
}
