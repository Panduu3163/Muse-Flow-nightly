package com.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit
) {
    val likedSongsViewModel: LikedSongsViewModel = viewModel()
    val likedSongs by likedSongsViewModel.likedSongs.collectAsState()
    
    val downloadViewModel: DownloadViewModel = viewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liked Songs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        likedSongs.forEach { track ->
                            if (!downloadViewModel.isDownloaded(track) && !downloadViewModel.isDownloading(track)) {
                                downloadViewModel.download(track)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Download All")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(likedSongs) { track ->
                LibrarySongRow(
                    track = track,
                    queue = likedSongs,
                    onPlayTrack = onPlayTrack
                )
            }
        }
    }
}
