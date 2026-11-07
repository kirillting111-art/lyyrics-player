package com.example.lyricsplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

fun loadSong(context: Context, uri: Uri): Song {
    val r = MediaMetadataRetriever()
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
            title ?: fileName(context, uri).substringBeforeLast('.'),
            artist ?: "Неизвестный исполнитель",
            cover,
            (durMs / 1000).toInt()
        )
    } catch (e: Exception) {
        Song(uri, fileName(context, uri).substringBeforeLast('.'), "Неизвестный исполнитель", null, 0)
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
                "+",
                color = Color.White,
                fontSize = 26.sp,
                modifier = Modifier
                    .clickable { onAdd() }
                    .padding(start = 12.dp)
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
                Text(
                    "⏮", color = Color.White, fontSize = 22.sp,
                    modifier = Modifier.clickable { onPrev() }.padding(horizontal = 10.dp)
                )
                Text(
                    if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 24.sp,
                    modifier = Modifier.clickable { onToggle() }.padding(horizontal = 10.dp)
                )
                Text(
                    "⏭", color = Color.White, fontSize = 22.sp,
                    modifier = Modifier.clickable { onNext() }.padding(horizontal = 10.dp)
                )
            }
            Text(fmt(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
        }
    }
}

@Composable
fun PlayerApp() {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

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
    var slots by remember { mutableStateOf(listOf(WKind.CLOCK, WKind.VIS)) }
    var bannerKind by remember { mutableStateOf(WKind.CLOCK) }
    var bannerVisible by remember { mutableStateOf(false) }
    var bannerTick by remember { mutableStateOf(0) }

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
        slots = listOf(WKind.CLOCK, WKind.VIS)
        if (song != null) {
            status = "Ищу текст…"
            val r = withContext(Dispatchers.IO) {
                fetchLyrics(song.title, song.artist, song.durationSec)
            }
            lyrics = r
            status = if (r.isEmpty()) "Синхронный текст не найден" else ""
        }
    }

    val current = if (lyrics.isEmpty()) -1
    else lyrics.indexOfLast { it.timeMs <= position + 150 }
    val progress = if (current >= 0) {
        val start = lyrics[current].timeMs
        val end = lyrics.getOrNull(current + 1)?.timeMs ?: (start + 4000)
        ((position - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(current, lyrics) {
        if (current >= 0) {
            val found = detectWidgets(lyrics[current].text)
            if (found.isNotEmpty()) {
                slots = (found + listOf(WKind.VIS, WKind.CLOCK)).distinct().take(2)
                bannerKind = found.first()
                bannerTick += 1
            }
        }
    }

    LaunchedEffect(bannerTick) {
        if (bannerTick > 0) {
            bannerVisible = true
            delay(3200)
            bannerVisible = false
        }
    }

    val base = remember(song) { coverColor(song) }
    val hueShift = if (current >= 0) (current % 8) * 12f else 0f
    val c1 by animateColorAsState(base, tween(900), label = "c1")
    val c2target = remember(base, hueShift) { shiftHue(base, 40f + hueShift) }
    val c2 by animateColorAsState(c2target, tween(1500), label = "c2")

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
                CoverCard(song, Modifier.weight(1.1f).fillMaxWidth())
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
                    modifier = Modifier.weight(0.9f).fillMaxWidth()
                )
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Crossfade(slots[0], animationSpec = tween(500), label = "s0") { k ->
                            WidgetView(k, bars, micGranted) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Crossfade(slots[1], animationSpec = tween(500), label = "s1") { k ->
                            WidgetView(k, bars, micGranted) {
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
            Banner(bannerKind.bannerApp, bannerKind.bannerText)
        }
    }
}
