package com.example.lyricsplayer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class LrcLine(val timeMs: Long, val text: String)

private val lrcRegex = Regex("""\[(\d+):(\d+(?:\.\d+)?)\]""")

fun parseLrc(raw: String): List<LrcLine> {
    val out = mutableListOf<LrcLine>()
    for (line in raw.lines()) {
        val stamps = lrcRegex.findAll(line).toList()
        if (stamps.isEmpty()) continue
        val text = line.substring(stamps.last().range.last + 1).trim()
        if (text.isEmpty()) continue
        for (m in stamps) {
            val t = m.groupValues[1].toLong() * 60_000L +
                (m.groupValues[2].toDouble() * 1000).toLong()
            out.add(LrcLine(t, text))
        }
    }
    return out.sortedBy { it.timeMs }
}

private fun httpGet(url: String): String? {
    return try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "LyricsPlayer/1.0")
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) {
        null
    }
}

fun fetchLyrics(title: String, artist: String, durationSec: Int): List<LrcLine> {
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    val knownArtist = artist.isNotBlank() && artist != "Неизвестный исполнитель"

    if (knownArtist && durationSec > 0) {
        val body = httpGet(
            "https://lrclib.net/api/get?artist_name=${enc(artist)}" +
                "&track_name=${enc(title)}&duration=$durationSec"
        )
        val synced = body?.let {
            runCatching { JSONObject(it).optString("syncedLyrics") }.getOrNull()
        }
        if (!synced.isNullOrBlank() && synced != "null") return parseLrc(synced)
    }

    val q = if (knownArtist) "$artist $title" else title
    val body = httpGet("https://lrclib.net/api/search?q=${enc(q)}") ?: return emptyList()
    val arr = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
    for (i in 0 until arr.length()) {
        val s = arr.getJSONObject(i).optString("syncedLyrics")
        if (s.isNotBlank() && s != "null") return parseLrc(s)
    }
    return emptyList()
}

@Composable
fun LyricsView(
    lines: List<LrcLine>,
    current: Int,
    progress: Float,
    status: String,
    modifier: Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(current, lines) {
        if (current >= 0 && lines.isNotEmpty()) {
            listState.animateScrollToItem(current.coerceIn(0, lines.size - 1))
        }
    }

    BoxWithConstraints(modifier) {
        if (lines.isEmpty()) {
            Text(
                status,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = maxHeight * 0.3f, bottom = maxHeight * 0.6f),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(lines) { i, line ->
                    LyricLine(line.text, i - current, progress)
                }
            }
        }
    }
}

@Composable
fun LyricLine(text: String, rel: Int, progress: Float) {
    val d = abs(rel)
    val al by animateFloatAsState(
        when (d) {
            0 -> 1f
            1 -> 0.45f
            2 -> 0.28f
            else -> 0.16f
        },
        tween(400),
        label = "al"
    )
    val blurR = (if (d == 0) 0 else minOf(d * 2, 6)).dp
    val words = text.split(" ")
    val lit = if (rel == 0) {
        (progress * (words.size + 0.6f)).toInt().coerceIn(0, words.size)
    } else {
        words.size
    }
    val styled = buildAnnotatedString {
        words.forEachIndexed { i, w ->
            withStyle(
                SpanStyle(
                    color = if (i < lit) Color.White else Color.White.copy(alpha = 0.5f)
                )
            ) { append(w) }
            if (i < words.size - 1) append(" ")
        }
    }
    Text(
        text = styled,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .alpha(al)
            .blur(blurR, BlurredEdgeTreatment.Unbounded)
    )
}
