package com.example.audiusplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Track(val id: String, val title: String, val artist: String, val art: String?)

object Api {
    private const val APP = "AudiusPlayer"
    private const val BASE = "https://api.audius.co/v1"

    private fun fetch(path: String): List<Track> {
        val c = URL("$BASE/$path").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 15000
        val text = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("data")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Track(
                o.getString("id"), o.getString("title"),
                o.getJSONObject("user").getString("name"),
                o.optJSONObject("artwork")?.optString("480x480")?.takeIf { s -> s.isNotBlank() }
            )
        }
    }
    fun trending() = fetch("tracks/trending?limit=30&app_name=$APP")
    fun search(q: String) = fetch("tracks/search?limit=30&app_name=$APP&query=" + URLEncoder.encode(q, "UTF-8"))
    fun streamUrl(id: String) = "$BASE/tracks/$id/stream?app_name=$APP"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(ctx).build() }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Loading...") }
    var current by remember { mutableStateOf<Track?>(null) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }

    fun load(q: String) = scope.launch {
        status = "Loading..."
        try {
            val r = withContext(Dispatchers.IO) { if (q.isBlank()) Api.trending() else Api.search(q) }
            tracks = r; status = if (r.isEmpty()) "No results" else ""
        } catch (e: Exception) { status = "Could not load songs. Check internet and tap Retry." }
    }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { playing = p }
            override fun onPlaybackStateChanged(s: Int) { buffering = s == Player.STATE_BUFFERING }
            override fun onPlayerError(e: PlaybackException) { status = "Playback failed. Try another song." }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(Unit) { load("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search songs") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { load(query) }),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        if (status.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(status, Modifier.weight(1f))
                if (status.startsWith("Could")) TextButton(onClick = { load(query) }) { Text("Retry") }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(tracks) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        current = t; status = ""
                        player.setMediaItem(MediaItem.fromUri(Api.streamUrl(t.id)))
                        player.prepare(); player.play()
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(t.art, null, Modifier.size(56.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
        current?.let { t ->
            Surface(tonalElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (buffering) "Loading..." else t.artist, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { if (playing) player.pause() else player.play() }) {
                            Text(if (playing) "Pause" else "Play")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vol")
                        Slider(value = volume, onValueChange = { volume = it; player.volume = it }, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
