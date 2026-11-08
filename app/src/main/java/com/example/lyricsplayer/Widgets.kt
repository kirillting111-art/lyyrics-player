
package com.example.lyricsplayer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val Accent = Color(0xFFB49AFF)
private val Muted = Color(0xFF9B91B5)
private val Green = Color(0xFF78DDB0)
private val Pink = Color(0xFFFF91B5)
private val Blue = Color(0xFF83BFFF)

enum class WKind(
    val title: String,
    val sub: String,
    val bannerApp: String,
    val bannerText: String,
    val words: List<String>
) {
    CLOCK("CLOCK", "Текущее время", "", "", emptyList()),
    VIS("AUDIO VISUALIZER", "Музыкальная волна", "", "", emptyList()),
    BATTERY("ЭНЕРГИЯ", "Заряд", "ЭНЕРГИЯ", "Заряд энергии",
        listOf("заряд", "батаре", "энерги")),
    SPEED("ДВИЖЕНИЕ", "Скорость", "ДВИЖЕНИЕ", "Движение",
        listOf("бег", "бежа", "скорост", "гонк", "быстр", "мчи", "лечу", "летим")),
    WEATHER("ПОГОДА", "Дождь и облака", "ПОГОДА", "Дождь",
        listOf("дожд", "слез", "слёз", "снег", "холод", "туч", "гроз", "ливен")),
    COMPASS("НАПРАВЛЕНИЕ", "Поиск пути", "КАРТЫ", "Маршрут",
        listOf("круг", "компас", "север", "дорог", "путь", "иду", "идём", "идем")),
    STOPWATCH("ВРЕМЯ", "Время идёт", "ВРЕМЯ", "Отсчёт",
        listOf("врем", "часы", "час=", "секунд", "минут")),
    EYE("ВЗГЛЯД", "Наблюдение", "ВЗГЛЯД", "Взгляд",
        listOf("глаз", "смотр", "взгляд", "вижу", "видел", "видишь")),
    CANDLE("ПЛАМЯ", "Тихий огонь", "ПЛАМЯ", "Огонь",
        listOf("свеч", "огонь", "огня", "пламя", "горит", "жжёт", "жжет")),
    MOON("НОЧЬ", "Тишина", "НОЧЬ", "Ночной режим",
        listOf("ноч", "луна", "луны", "сон=", "спать", "тишин", "молч")),
    HEART("ПУЛЬС", "Ритм сердца", "ПУЛЬС", "Ритм",
        listOf("сердц", "любов", "люблю", "любим", "нежн")),
    MONEY("БАЛАНС", "Цифры", "БАЛАНС", "Баланс",
        listOf("деньг", "бабк", "бабло", "куш=", "долл", "рубл", "богат", "налик")),
    HEAVEN("НЕБО", "Свет и пространство", "НЕБО", "Свет",
        listOf("бог=", "бога", "богу", "боже", "небо", "небес", "ангел", "рай=",
            "молитв", "вера=", "верю")),

    DISTANCE("РАССТОЯНИЕ", "Мы далеко друг от друга", "РАССТОЯНИЕ", "Далеко друг от друга",
        listOf("далек", "расстоян", "разлук", "один", "потерял", "ушёл", "ушел")),
    MEETING("ВСТРЕЧА", "Снова рядом", "ВСТРЕЧА", "Мы рядом",
        listOf("встреч", "рядом", "верн", "обним", "приходи", "увид")),
    LONELY("ОДИНОЧЕСТВО", "Тишина вокруг", "ОДИНОЧЕСТВО", "Один",
        listOf("одинок", "пустот", "одна", "один=", "никого", "пусто"))
}

fun detectWidgets(text: String): List<WKind> {
    val tokens = text.lowercase().split(Regex("[^\\p{L}]+"))
        .filter { it.isNotEmpty() }

    return WKind.values().filter { kind ->
        kind.words.isNotEmpty() && kind.words.any { word ->
            tokens.any { token ->
                if (word.endsWith("=")) token == word.dropLast(1)
                else token.startsWith(word)
            }
        }
    }
}

@Composable
fun WidgetCard(
    title: String,
    sub: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF171522).copy(alpha = 0.88f))
            .padding(12.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp, maxLines = 1)
        Text(sub, color = Muted, fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
fun WidgetView(
    kind: WKind,
    bars: FloatArray,
    micGranted: Boolean,
    onRequestMic: () -> Unit
) {
    WidgetCard(kind.title, kind.sub) {
        when (kind) {
            WKind.CLOCK -> ClockWidget()
            WKind.VIS -> VisualizerWidget(bars, micGranted, onRequestMic)
            WKind.BATTERY -> BatteryWidget()
            WKind.SPEED -> SpeedWidget()
            WKind.WEATHER -> WeatherWidget()
            WKind.COMPASS -> CompassWidget()
            WKind.STOPWATCH -> StopwatchWidget()
            WKind.EYE -> EyeWidget()
            WKind.CANDLE -> CandleWidget()
            WKind.MOON -> MoonWidget()
            WKind.HEART -> HeartWidget()
            WKind.MONEY -> MoneyWidget()
            WKind.HEAVEN -> HeavenWidget()
            WKind.DISTANCE -> DistanceWidget()
            WKind.MEETING -> MeetingWidget()
            WKind.LONELY -> LonelyWidget()
        }
    }
}

@Composable
fun Banner(app: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEE211E2B))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Accent)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(app, color = Muted, fontSize = 9.sp)
            Text(text, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun ClockWidget() {
    var now by remember { mutableStateOf(Date()) }
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val date = remember { SimpleDateFormat("EEE, d MMMM", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(time.format(now), color = Color.White, fontSize = 36.sp)
        Text(date.format(now), color = Muted, fontSize = 11.sp)
    }
}

@Composable
fun VisualizerWidget(bars: FloatArray, granted: Boolean, onRequest: () -> Unit) {
    if (!granted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent.copy(alpha = 0.2f),
                    contentColor = Color.White
                )
            ) { Text("Включить", fontSize = 11.sp) }
        }
    } else {
        Canvas(Modifier.fillMaxSize()) {
            val n = bars.size.coerceAtLeast(1)
            val step = size.width / n
            val w = step * 0.65f

            bars.forEachIndexed { i, value ->
                val h = size.height * value.coerceIn(0.04f, 1f)
                drawRoundRect(
                    color = Accent.copy(alpha = 0.9f),
                    topLeft = Offset(i * step, size.height - h),
                    size = Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)
                )
            }
        }
    }
}

@Composable
fun BatteryWidget() {
    val transition = rememberInfiniteTransition(label = "battery")
    val progress by transition.animateFloat(
        0.65f, 0.95f,
        infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "progress"
    )

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ЭНЕРГИЯ", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text("100%", color = Green, fontSize = 19.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(9.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                Modifier.fillMaxWidth(progress).fillMaxHeight()
                    .background(Green, RoundedCornerShape(10.dp))
            )
        }
    }
}

@Composable
fun SpeedWidget() {
    val transition = rememberInfiniteTransition(label = "speed")
    val progress by transition.animateFloat(
        0.15f, 0.95f,
        infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "speedProgress"
    )

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("${(progress * 120).toInt()} км/ч", color = Color.White, fontSize = 27.sp)
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            drawLine(
                Pink, Offset(0f, size.height * 0.8f),
                Offset(size.width * progress, size.height * 0.2f),
                3.dp.toPx(), StrokeCap.Round
            )
        }
    }
}

@Composable
fun StopwatchWidget() {
    var ms by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            ms = (System.nanoTime() - start) / 1_000_000
            delay(50)
        }
    }

    val seconds = ms / 1000
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(
            "%d:%02d".format(seconds / 60, seconds % 60),
            color = Color.White, fontSize = 34.sp
        )
        Text("ВРЕМЯ НЕ ОСТАНАВЛИВАЕТСЯ", color = Muted, fontSize = 9.sp)
    }
}

@Composable
fun HeartWidget() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        0.9f, 1.13f,
        infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(65.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        }) {
            val path = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.9f)
                cubicTo(-size.width * 0.1f, size.height * 0.48f,
                    size.width * 0.08f, size.height * 0.05f,
                    size.width * 0.5f, size.height * 0.3f)
                cubicTo(size.width * 0.92f, size.height * 0.05f,
                    size.width * 1.1f, size.height * 0.48f,
                    size.width * 0.5f, size.height * 0.9f)
                close()
            }
            drawPath(path, Pink)
        }
        Text("92 уд/мин", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun MoneyWidget() {
    val transition = rememberInfiniteTransition(label = "money")
    val value by transition.animateFloat(
        1000f, 9999f,
        infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "moneyValue"
    )

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("БАЛАНС", color = Muted, fontSize = 10.sp)
        Text("%,d ₽".format(value.toInt()), color = Green, fontSize = 25.sp)
    }
}

@Composable
fun DistanceWidget() {
    val transition = rememberInfiniteTransition(label = "distance")
    val distance by transition.animateFloat(
        0.15f, 0.85f,
        infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "distanceValue"
    )

    Canvas(Modifier.fillMaxSize()) {
        val centerY = size.height * 0.48f
        val leftX = size.width * (0.2f - distance * 0.1f)
        val rightX = size.width * (0.8f + distance * 0.1f)
        val personSize = size.height * 0.12f

        drawLine(
            Muted.copy(alpha = 0.35f),
            Offset(leftX + personSize, centerY),
            Offset(rightX - personSize, centerY),
            2.dp.toPx()
        )

        drawCircle(Accent, personSize * 0.42f,
            Offset(leftX, centerY - personSize * 1.3f))
        drawLine(Accent,
            Offset(leftX, centerY - personSize * 0.8f),
            Offset(leftX, centerY + personSize),
            3.dp.toPx(), StrokeCap.Round)
        drawLine(Accent,
            Offset(leftX, centerY - personSize * 0.1f),
            Offset(leftX - personSize * 0.7f, centerY + personSize * 0.45f),
            2.dp.toPx(), StrokeCap.Round)
        drawLine(Accent,
            Offset(leftX, centerY - personSize * 0.1f),
            Offset(leftX + personSize * 0.7f, centerY + personSize * 0.45f),
            2.dp.toPx(), StrokeCap.Round)

        drawCircle(Pink, personSize * 0.42f,
            Offset(rightX, centerY - personSize * 1.3f))
        drawLine(Pink,
            Offset(rightX, centerY - personSize * 0.8f),
            Offset(rightX, centerY + personSize),
            3.dp.toPx(), StrokeCap.Round)
        drawLine(Pink,
            Offset(rightX, centerY - personSize * 0.1f),
            Offset(rightX - personSize * 0.7f, centerY + personSize * 0.45f),
            2.dp.toPx(), StrokeCap.Round)
        drawLine(Pink,
            Offset(rightX, centerY - personSize * 0.1f),
            Offset(rightX + personSize * 0.7f, centerY + personSize * 0.45f),
            2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun MeetingWidget() {
    val transition = rememberInfiniteTransition(label = "meeting")
    val distance by transition.animateFloat(
        0.75f, 0.18f,
        infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "meetingDistance"
    )

    Canvas(Modifier.fillMaxSize()) {
        val y = size.height * 0.55f
        val left = size.width * (0.5f - distance * 0.4f)
        val right = size.width * (0.5f + distance * 0.4f)
        val r = size.height * 0.09f

        drawCircle(Accent, r, Offset(left, y - r * 2))
        drawLine(Accent, Offset(left, y - r), Offset(left, y + r * 1.8f), 3.dp.toPx())
        drawCircle(Pink, r, Offset(right, y - r * 2))
        drawLine(Pink, Offset(right, y - r), Offset(right, y + r * 1.8f), 3.dp.toPx())
    }
}

@Composable
fun LonelyWidget() {
    val transition = rememberInfiniteTransition(label = "lonely")
    val alpha by transition.animateFloat(
        0.2f, 0.8f,
        infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "lonelyAlpha"
    )

    Canvas(Modifier.fillMaxSize()) {
        val x = size.width * 0.5f
        val y = size.height * 0.55f
        val r = size.height * 0.1f
        drawCircle(Accent.copy(alpha = alpha), r, Offset(x, y - r * 2))
        drawLine(Accent.copy(alpha = alpha),
            Offset(x, y - r), Offset(x, y + r * 2), 3.dp.toPx())
        drawLine(Muted.copy(alpha = 0.3f),
            Offset(0f, y + r * 2), Offset(size.width, y + r * 2), 1.dp.toPx())
    }
}

@Composable
fun WeatherWidget() {
    val transition = rememberInfiniteTransition(label = "rain")
    val offset by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "rainOffset"
    )

    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.35f
        drawOval(Color(0xFF777F9B), Offset(cx - 24.dp.toPx(), cy - 8.dp.toPx()),
            Size(48.dp.toPx(), 22.dp.toPx()))
        for (i in 0..4) {
            val x = cx - 20.dp.toPx() + i * 10.dp.toPx()
            val y = cy + 20.dp.toPx() + ((offset + i * 0.2f) % 1f) * 24.dp.toPx()
            drawLine(Blue, Offset(x, y), Offset(x - 3.dp.toPx(), y + 7.dp.toPx()), 2.dp.toPx())
        }
    }
}

@Composable
fun CompassWidget() {
    val transition = rememberInfiniteTransition(label = "compass")
    val rotation by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "compassRotation"
    )

    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension * 0.35f
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Muted.copy(alpha = 0.35f), r, c, style = Stroke(2.dp.toPx()))
        val angle = rotation * PI.toFloat() / 180f
        val end = Offset(c.x + cos(angle) * r, c.y + sin(angle) * r)
        drawLine(Accent, c, end, 3.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White, 3.dp.toPx(), c)
    }
}

@Composable
fun EyeWidget() {
    val transition = rememberInfiniteTransition(label = "eye")
    val blink by transition.animateFloat(
        0.7f, 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "eyeBlink"
    )

    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        val w = size.width * 0.5f
        val h = size.height * 0.28f * blink
        val path = Path().apply {
            moveTo(c.x - w / 2, c.y)
            quadraticTo(c.x, c.y - h, c.x + w / 2, c.y)
            quadraticTo(c.x, c.y + h, c.x - w / 2, c.y)
        }
        drawPath(path, Accent, style = Stroke(2.dp.toPx()))
        drawCircle(Blue, size.height * 0.1f, c)
        drawCircle(Color.White, size.height * 0.035f, c)
    }
}

@Composable
fun CandleWidget() {
    val transition = rememberInfiniteTransition(label = "flame")
    val sway by transition.animateFloat(
        -0.15f, 0.15f,
        infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "flameSway"
    )

    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val base = size.height * 0.8f
        val flame = Path().apply {
            moveTo(cx, base - 12.dp.toPx())
            cubicTo(
                cx - 25.dp.toPx() + sway * 20, base - 30.dp.toPx(),
                cx + 20.dp.toPx() + sway * 20, base - 45.dp.toPx(),
                cx, base - 65.dp.toPx()
            )
            close()
        }
        drawPath(flame, Color(0xFFFFB66D))
        drawRoundRect(Color(0xFFE8D5BB),
            Offset(cx - 10.dp.toPx(), base - 12.dp.toPx()),
            Size(20.dp.toPx(), 32.dp.toPx()),
            androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
    }
}

@Composable
fun MoonWidget() {
    val transition = rememberInfiniteTransition(label = "moon")
    val glow by transition.animateFloat(
        0.35f, 0.85f,
        infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "moonGlow"
    )

    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension * 0.27f
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Accent.copy(alpha = glow * 0.18f), r * 1.5f, c)
        drawCircle(Color(0xFFFFE6A5), r, c)
        drawCircle(Color(0xFF171522), r, Offset(c.x + r * 0.48f, c.y - r * 0.28f))
    }
}

@Composable
fun HeavenWidget() {
    val transition = rememberInfiniteTransition(label = "heaven")
    val glow by transition.animateFloat(
        0.2f, 0.8f,
        infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "heavenGlow"
    )

    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        drawCircle(Color(0xFFFFE5AE).copy(alpha = glow), size.minDimension * 0.22f,
            Offset(cx, cy))
        for (i in 0..7) {
            val angle = i * PI.toFloat() / 4
            val x = cx + cos(angle) * size.minDimension * 0.35f
            val y = cy + sin(angle) * size.minDimension * 0.35f
            drawCircle(Color.White.copy(alpha = glow), 2.dp.toPx(), Offset(x, y))
        }
    }
}
