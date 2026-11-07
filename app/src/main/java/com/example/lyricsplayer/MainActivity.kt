package com.example.lyricsplayer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.hypot

data class Song(
    val uri: Uri,
    val title: String,
    val artist: String,
    val cover: Bitmap?,
    val durationSec: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlayerApp() }
    }
}

fun fileName(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && i >= 0) c.getString(i) else null
    } ?: "Трек"
}

// "Исполнитель - Название.mp3" -> (исполнитель, название)
fun guessFromName(name: String): Pair<String?, String> {
    val base = name.substringBeforeLast('.').replace('_', ' ').trim()
    val parts = base.split(" - ", limit = 2)
    return if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else Pair(null, base)
}

fun loadSong(context: Context, uri: Uri): Song {
    val r = MediaMetadataRetriever()
    val guess = guessFromName(fileName(context, uri))
    return try {
        r.setDataSource(context, uri)
        val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val pic = r.embeddedPicture
        val cover = pic?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        Song(
            uri,
            if (title.isNullOrBlank()) guess.second else title,
            if (artist.isNullOrBlank()) (guess.first ?: "Неизвестный исполнитель") else artist,
            cover,
            (durMs / 1000).toInt()
        )
    } catch (e: Exception) {
        Song(uri, guess.second, guess.first ?: "Неизвестный исполнитель", null, 0)
    } finally {
        r.release()
    }
}

fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun coverColor(song: Song?): Color {
    if (song == null) return Color(0xFF3B1F6B)
    val bmp = song.cover
    if (bmp != null) {
        val p = Palette.from(bmp).generate()
        val c = p.getDarkVibrantColor(
            p.getDarkMutedColor(p.getDominantColor(0xFF3B1F6B.toInt()))
        )
        return Color(c)
    }
    val hue = ((song.title.hashCode() % 360) + 360) % 360
    return Color.hsv(hue.toFloat(), 0.6f, 0.45f)
}

fun shiftHue(c: Color, deg: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    return Color.hsv(
        (hsv[0] + deg) % 360f,
        hsv[1].coerceIn(0.4f, 1f),
        hsv[2].coerceIn(0.35f, 0.7f)
    )
}

// 0 - назад, 1 - играть, 2 - пауза, 3 - вперёд
@Composable
fun TransportIcon(kind: Int) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val white = Color.White
        when (kind) {
            0 -> {
                drawRect(white, Offset(w * 0.15f, h * 0.2f), Size(w * 0.12f, h * 0.6f))
                val p = Path().apply {
                    moveTo(w * 0.85f, h * 0.2f)
                    lineTo(w * 0.35f, h * 0.5f)
                    lineTo(w * 0.85f, h * 0.8f)
                    close()
                }
                drawPath(p, white)
            }
            1 -> {
                val p = Path().apply {
                    moveTo(w * 0.25f, h * 0.12f)
                    lineTo(w * 0.9f, h * 0.5f)
                    lineTo(w * 0.25f, h * 0.88f)
                    close()
                }
                drawPath(p, white)
            }
            2 -> {
                drawRoundRect(
                    white, Offset(w * 0.2f, h * 0.15f),
                    Size(w * 0.2f, h * 0.7f), CornerRadius(3f, 3f)
                )
                drawRoundRect(
                    white, Offset(w * 0.6f, h * 0.15f),
                    Size(w * 0.2f, h * 0.7f), CornerRadius(3f, 3f)
                )
            }
            else -> {
                drawRect(white, Offset(w * 0.73f, h * 0.2f), Size(w * 0.12f, h * 0.6f))
                val p = Path().apply {
                    moveTo(w * 0.15f, h * 0.2f)
                    lineTo(w * 0.65f, h * 0.5f)
                    lineTo(w * 0.15f, h * 0.8f)
                    close()
                }
                drawPath(p, white)
            }
        }
    }
}

@Composable
fun CoverCard(song: Song?, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        val cover = song?.cover
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                "♪",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 48.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            "Сейчас в эфире",
            color = Color.White,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun PlayerCard(
    song: Song?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    onCopy: () -> Unit,
    onScene: () -> Unit,
    modifier: Modifier
) {
    val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    song?.title ?: "Добавь песню",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song?.artist ?: "",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "📋",
                fontSize = 15.sp,
                modifier = Modifier.clickable { onCopy() }.padding(horizontal = 4.dp)
            )
            Text(
                "🎬",
                fontSize = 15.sp,
                modifier = Modifier.clickable { onScene() }.padding(horizontal = 4.dp)
            )
            Text(
                "🔍",
                fontSize = 15.sp,
                modifier = Modifier.clickable { onSearch() }.padding(horizontal = 4.dp)
            )
            Text(
                "+",
                color = Color.White,
                fontSize = 26.sp,
                modifier = Modifier.clickable { onAdd() }.padding(start = 6.dp)
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .pointerInput(duration) {
                    detectTapGestures { o -> onSeek(o.x / size.width) }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(frac)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(fmt(position), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { onPrev() }.padding(horizontal = 12.dp, vertical = 2.dp)) {
                    TransportIcon(0)
                }
                Box(Modifier.clickable { onToggle() }.padding(horizontal = 12.dp, vertical = 2.dp)) {
                    TransportIcon(if (isPlaying) 2 else 1)
                }
                Box(Modifier.clickable { onNext() }.padding(horizontal = 12.dp, vertical = 2.dp)) {
                    TransportIcon(3)
                }
            }
            Text(fmt(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
        }
    }
}

@Composable
fun PlayerApp() {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val prefs = remember { context.getSharedPreferences("scenes", Context.MODE_PRIVATE) }

    var songs by remember { mutableStateOf(listOf<Song>()) }
    var index by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var audioSession by remember { mutableStateOf(player.audioSessionId) }
    var bars by remember { mutableStateOf(FloatArray(24)) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var lyrics by remember { mutableStateOf(listOf<LrcLine>()) }
    var status by remember { mutableStateOf("Добавь песню, и здесь появится текст") }
    var matched by remember { mutableStateOf(listOf<WKind>()) }
    var scene by remember { mutableStateOf(listOf<SLine>()) }
    var bannerApp by remember { mutableStateOf("") }
    var bannerText by remember { mutableStateOf("") }
    var bannerVisible by remember { mutableStateOf(false) }
    var bannerTick by remember { mutableStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var manualQuery by remember { mutableStateOf<String?>(null) }
    var showScene by remember { mutableStateOf(false) }
    var sceneText by remember { mutableStateOf("") }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { micGranted = it }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioSession = audioSessionId
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = maxOf(player.duration, 0L)
            delay(120)
        }
    }

    DisposableEffect(audioSession, micGranted) {
        var vis: Visualizer? = null
        if (micGranted && audioSession != 0) {
            try {
                val v = Visualizer(audioSession)
                v.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int
                    ) {
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int
                    ) {
                        if (fft == null) return
                        val n = 24
                        val usable = fft.size / 2
                        val out = FloatArray(n)
                        for (i in 0 until n) {
                            val t = i.toFloat() / n
                            val idx = 1 + (t * t * (usable - 2)).toInt()
                            val re = fft[2 * idx].toInt().toFloat()
                            val im = fft[2 * idx + 1].toInt().toFloat()
                            out[i] = (hypot(re, im) / 90f).coerceIn(0f, 1f)
                        }
                        bars = out
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                v.setEnabled(true)
                vis = v
            } catch (e: Exception) {
                vis = null
            }
        }
        onDispose { vis?.release() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val wasEmpty = songs.isEmpty()
            val loaded = uris.map { loadSong(context, it) }
            songs = songs + loaded
            loaded.forEach { player.addMediaItem(MediaItem.fromUri(it.uri)) }
            if (wasEmpty) {
                player.prepare()
                player.play()
            }
        }
    }

    val song = songs.getOrNull(index)

    LaunchedEffect(song?.uri) {
        lyrics = emptyList()
        matched = emptyList()
        manualQuery = null
        scene = if (song != null) {
            prefs.getString(song.uri.toString(), null)?.let { parseScene(it) } ?: emptyList()
        } else emptyList()
        if (song != null) {
            status = "Ищу текст…"
            var r = withContext(Dispatchers.IO) {
                fetchLyrics(song.title, song.artist, song.durationSec)
            }
            if (song.cover == null || r.isEmpty()) {
                val q = if (song.artist != "Неизвестный исполнитель")
                    "${song.artist} ${song.title}" else song.title
                val hit = withContext(Dispatchers.IO) { searchItunes(q) }
                if (hit != null && looksSame(song.title, hit.title)) {
                    if (hit.cover != null && song.cover == null) {
                        songs = songs.map {
                            if (it.uri == song.uri) it.copy(cover = hit.cover) else it
                        }
                    }
                    if (r.isEmpty()) {
                        r = withContext(Dispatchers.IO) { fetchLyrics(hit.title, hit.artist, 0) }
                    }
                }
            }
            lyrics = r
            status = if (r.isEmpty()) "Текст не найден. Нажми 🔍 и введи название" else ""
        }
    }

    LaunchedEffect(manualQuery) {
        val q = manualQuery
        if (q != null && q.isNotBlank()) {
            status = "Ищу текст…"
            val hit = withContext(Dispatchers.IO) { searchItunes(q) }
            var r = withContext(Dispatchers.IO) {
                fetchLyrics(hit?.title ?: q, hit?.artist ?: "", 0)
            }
            if (r.isEmpty()) r = withContext(Dispatchers.IO) { fetchLyrics(q, "", 0) }
            if (hit != null && song != null) {
                songs = songs.map {
                    if (it.uri == song.uri) {
                        it.copy(
                            title = hit.title.ifBlank { it.title },
                            artist = hit.artist.ifBlank { it.artist },
                            cover = hit.cover ?: it.cover
                        )
                    } else it
                }
            }
            lyrics = r
            status = if (r.isEmpty()) "Текст не найден" else ""
        }
    }

    val current = if (lyrics.isEmpty()) -1
    else lyrics.indexOfLast { it.timeMs <= position + 150 }
    val progress = if (current >= 0) {
        val start = lyrics[current].timeMs
        val end = lyrics.getOrNull(current + 1)?.timeMs ?: (start + 4000)
        ((position - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    } else 0f

    val sl: SLine? = if (scene.isNotEmpty() && current >= 0) {
        scene.lastOrNull { it.i <= current }
    } else null

    LaunchedEffect(current, lyrics) {
        if (current >= 0 && scene.isEmpty()) {
            val found = detectWidgets(lyrics[current].text)
            matched = found
            if (found.isNotEmpty()) {
                bannerApp = found.first().bannerApp
                bannerText = found.first().bannerText
                bannerTick += 1
            }
        }
    }

    LaunchedEffect(sl?.i, scene) {
        val b = sl?.banner
        if (b != null) {
            bannerApp = b.app
            bannerText = b.text
            bannerTick += 1
        }
    }

    LaunchedEffect(bannerTick) {
        if (bannerTick > 0) {
            bannerVisible = true
            delay(3200)
            bannerVisible = false
        }
    }

    // Если сцены нет и в строке нет подходящих слов, виджеты сменяются каждые 5 секунд
    val neutral = listOf(
        WKind.CLOCK, WKind.VIS, WKind.STOPWATCH, WKind.COMPASS, WKind.HEART, WKind.SPEED
    )
    val rot = (position / 5000).toInt()
    val slot0 = matched.getOrNull(0) ?: neutral[(rot * 2) % neutral.size]
    val slot1Raw = matched.getOrNull(1)
        ?: if (matched.isNotEmpty()) WKind.VIS else neutral[(rot * 2 + 1) % neutral.size]
    val slot1 = if (slot1Raw == slot0) neutral[(rot * 2 + 2) % neutral.size] else slot1Raw

    val w0: SWidget
    val w1: SWidget
    if (sl != null && sl.widgets.isNotEmpty()) {
        w0 = sl.widgets[0]
        w1 = sl.widgets.getOrNull(1) ?: kindWidget(WKind.VIS)
    } else {
        w0 = kindWidget(slot0)
        w1 = kindWidget(slot1)
    }

    val sceneHue = sl?.hue
    val base = remember(song, sceneHue) {
        if (song?.cover == null && sceneHue != null) Color.hsv(sceneHue, 0.65f, 0.45f)
        else coverColor(song)
    }
    val hueShift = if (current >= 0) (current % 8) * 12f else (rot % 8) * 12f
    val c1 by animateColorAsState(base, tween(900), label = "c1")
    val c2target = remember(base, hueShift, sceneHue) {
        if (sceneHue != null) Color.hsv((sceneHue + 50f) % 360f, 0.7f, 0.5f)
        else shiftHue(base, 40f + hueShift)
    }
    val c2 by animateColorAsState(c2target, tween(1500), label = "c2")

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Найти текст") },
            text = {
                TextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("Исполнитель и название") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    manualQuery = searchText
                    showSearch = false
                }) { Text("Искать") }
            },
            dismissButton = {
                TextButton(onClick = { showSearch = false }) { Text("Отмена") }
            }
        )
    }

    if (showScene) {
        AlertDialog(
            onDismissRequest = { showScene = false },
            title = { Text("Вставить сцену") },
            text = {
                TextField(
                    value = sceneText,
                    onValueChange = { sceneText = it },
                    placeholder = { Text("Вставь сюда ответ ИИ") },
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = parseScene(sceneText)
                    if (p == null || p.isEmpty()) {
                        Toast.makeText(context, "Не получилось прочитать сцену", Toast.LENGTH_LONG).show()
                    } else {
                        scene = p
                        if (song != null) {
                            prefs.edit().putString(song.uri.toString(), sceneText).apply()
                        }
                        showScene = false
                    }
                }) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scene = emptyList()
                    if (song != null) prefs.edit().remove(song.uri.toString()).apply()
                    showScene = false
                }) { Text("Сбросить") }
            }
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(c1, c2, Color(0xFF0B0B0B))))
    ) {
        val bg = song?.cover
        if (bg != null) {
            Image(
                bitmap = bg.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
                    .alpha(0.3f)
            )
        }

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoverCard(song, Modifier.weight(1.0f).fillMaxWidth())
                PlayerCard(
                    song = song,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    onPrev = { player.seekToPreviousMediaItem() },
                    onToggle = { if (isPlaying) player.pause() else player.play() },
                    onNext = { player.seekToNextMediaItem() },
                    onSeek = { f -> if (duration > 0) player.seekTo((f * duration).toLong()) },
                    onAdd = { picker.launch(arrayOf("audio/*")) },
                    onSearch = {
                        searchText = if (song != null && song.artist != "Неизвестный исполнитель")
                            "${song.artist} ${song.title}" else (song?.title ?: "")
                        showSearch = true
                    },
                    onCopy = {
                        if (lyrics.isEmpty()) {
                            Toast.makeText(context, "Сначала найди текст песни", Toast.LENGTH_SHORT).show()
                        } else {
                            val sb = StringBuilder(SCENE_PROMPT)
                            sb.append("\n\nПесня: ")
                                .append(song?.title ?: "")
                                .append(" - ")
                                .append(song?.artist ?: "")
                                .append("\n")
                            lyrics.forEachIndexed { i, l ->
                                sb.append(i).append("|").append(fmt(l.timeMs))
                                    .append("|").append(l.text).append("\n")
                            }
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("lyrics", sb.toString()))
                            Toast.makeText(
                                context, "Скопировано. Вставь в чат с ИИ", Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onScene = {
                        sceneText = ""
                        showScene = true
                    },
                    modifier = Modifier.weight(1.0f).fillMaxWidth()
                )
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Crossfade(w0, animationSpec = tween(500), label = "s0") { k ->
                            SceneWidget(k, bars, micGranted) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Crossfade(w1, animationSpec = tween(500), label = "s1") { k ->
                            SceneWidget(k, bars, micGranted) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                }
            }

            LyricsView(
                lines = lyrics,
                current = current,
                progress = progress,
                status = status,
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            )
        }

        AnimatedVisibility(
            visible = bannerVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 20.dp)
        ) {
            Banner(bannerApp, bannerText)
        }
    }
}
