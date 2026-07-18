package com.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachedSongsScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { MuseFlowDatabase.getInstance(context).cachedTrackDao() }
    val cachedEntities by dao.observeCachedTracks().collectAsState(initial = emptyList())
    
    // Convert entities to tracks, filtering only those whose keys are still in the ExoPlayer cache.
    // This handles ExoPlayer's LRU eviction nicely.
    val cachedTracks = remember(cachedEntities) {
        val simpleCache = CacheManager.getInstance(context)
        val validKeys = simpleCache.keys
        cachedEntities
            .filter { validKeys.contains(it.key) }
            .map { entity ->
                Track(
                    title = entity.title,
                    artist = entity.artist,
                    album = entity.album,
                    duration = entity.duration,
                    plays = "",
                    gradientIndex = entity.gradientIndex,
                    imageUrl = entity.imageUrl,
                    streamUrl = entity.streamUrl,
                    sourceId = entity.sourceId,
                    sourceType = entity.sourceType?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() }
                )
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cached Songs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (cachedTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No cached tracks.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(cachedTracks) { track ->
                    LibrarySongRow(
                        track = track,
                        queue = cachedTracks,
                        onPlayTrack = onPlayTrack
                    )
                }
            }
        }
    }
}
