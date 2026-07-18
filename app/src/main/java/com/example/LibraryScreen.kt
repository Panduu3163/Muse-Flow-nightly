package com.example

import com.example.ui.theme.MusePrimary
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LibraryScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlaylistClick: (PlaylistResult) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Playlists", "Songs", "Albums", "Artists", "Local")

    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistSource by remember { mutableStateOf("Empty") }
    val playlistSources = listOf("Empty", "Liked Songs", "Downloads", "Online Search")

    val downloadViewModel: DownloadViewModel = viewModel()
    val downloadedTracks by downloadViewModel.downloadedTracks.collectAsState()
    val downloadedKeys = remember(downloadedTracks) { downloadedTracks.map { it.downloadKey() }.toSet() }

    val libraryViewModel: LibraryViewModel = viewModel()
    val playlists by libraryViewModel.playlists.collectAsState()
    val recentlyPlayed by libraryViewModel.recentlyPlayed.collectAsState()

    val likedSongsViewModel: LikedSongsViewModel = viewModel()
    val likedSongs by likedSongsViewModel.likedSongs.collectAsState()

    // When on, every track tab (Liked/Recently Played - Downloads is already downloads-only)
    // filters down to just what's actually playable offline.
    var offlineModeEnabled by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    if (selectedTab == 1) {
        LikedSongsScreen(onPlayTrack = onPlayTrack, onBack = { selectedTab = 0 })
        return
    } else if (selectedTab == 2) {
        DownloadedSongsScreen(onPlayTrack = onPlayTrack, onBack = { selectedTab = 0 })
        return
    } else if (selectedTab == 3) {
        CachedSongsScreen(onPlayTrack = onPlayTrack, onBack = { selectedTab = 0 })
        return
    } else if (selectedTab == 4) {
        LocalSongsScreen(onPlayTrack = onPlayTrack, onBack = { selectedTab = 0 })
        return
    } else if (selectedTab == 5) {
        StatsScreen(onBack = { selectedTab = 0 })
        return
    } else if (selectedTab == 6) {
        HistoryScreen(onPlayTrack = onPlayTrack, onBack = { selectedTab = 0 })
        return
    }

    ThemedBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { selectedTab = 6 }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Default.History, contentDescription = "History", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { selectedTab = 5 }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Stats", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            // Top Filter Chips removed as requested
            // Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Quick Access Tiles
                    val quickAccessTiles = listOf(
                        "Liked" to Icons.Default.Favorite,
                        "Downloaded" to Icons.Default.DownloadDone,
                        "Cached" to Icons.Default.Sync,
                        "Local" to Icons.Default.Folder
                    )

                    items(quickAccessTiles.size) { index ->
                        val (title, icon) = quickAccessTiles[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (title == "Liked") selectedTab = 1
                                    if (title == "Downloaded") selectedTab = 2
                                    if (title == "Cached") selectedTab = 3
                                    if (title == "Local") selectedTab = 4
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = if (title == "Liked") Color(0xFFFF8A8A) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Playlists Header
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Playlists",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            IconButton(onClick = { showNewPlaylistDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Playlist",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    // Playlists Grid View
                    items(playlists) { playlist ->
                        val isRemote = playlist.remoteId != null
                        val gradientColors = MusicData.Gradients[(playlist.id % MusicData.Gradients.size.toLong()).toInt()]
                        Column(
                            modifier = Modifier
                                
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val safeSubtitle = playlist.subtitle ?: if (isRemote) "" else "Local Playlist"
                                    onPlaylistClick(
                                        PlaylistResult(
                                            id = if (isRemote) playlist.remoteId!! else "local_${playlist.id}",
                                            title = playlist.name,
                                            subtitle = safeSubtitle,
                                            imageUrl = playlist.imageUrl,
                                            songCount = null,
                                            sourceType = if (playlist.sourceType != null) MusicSource.valueOf(playlist.sourceType) else MusicSource.JIOSAAVN
                                        )
                                    )
                                }
                                .testTag("library_playlist_card_${playlist.name.lowercase().replace(" ", "_")}"),
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (isRemote && playlist.imageUrl != null) {
                                TrackArtwork(
                                    imageUrl = playlist.imageUrl,
                                    gradientColors = gradientColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    Text("🎵", fontSize = 48.sp)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Brush.linearGradient(gradientColors)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎵", fontSize = 48.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isRemote) {
                                Text(
                                    text = playlist.subtitle ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Local Playlist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue for "+ New Playlist"
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewPlaylistDialog = false
                newPlaylistName = ""
                newPlaylistSource = "Empty"
            },
            title = {
                Text(
                    text = "Create Playlist",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Column {
                    Text(
                        text = "Give your playlist a name.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("My Awesome Playlist") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_new_playlist_input")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Source",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    playlistSources.forEach { source ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { newPlaylistSource = source }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = newPlaylistSource == source,
                                onClick = { newPlaylistSource = source },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = source, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistSource == "Online Search") {
                            libraryViewModel.createPlaylist(newPlaylistName, "Empty")
                            showNewPlaylistDialog = false
                            newPlaylistName = ""
                            newPlaylistSource = "Empty"
                            onNavigateToSearch()
                        } else {
                            libraryViewModel.createPlaylist(newPlaylistName, newPlaylistSource)
                            showNewPlaylistDialog = false
                            newPlaylistName = ""
                            newPlaylistSource = "Empty"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("dialog_confirm_button")
                ) {
                    Text("Create", color = Color(0xFF090A0F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewPlaylistDialog = false
                        newPlaylistName = ""
                        newPlaylistSource = "Empty"
                    },
                    modifier = Modifier.testTag("dialog_cancel_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun LibrarySongRow(
    track: Track,
    queue: List<Track>,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlayTrack(track, queue) }
            .padding(8.dp)
            .testTag("library_song_row_${track.title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(
            imageUrl = track.imageUrl,
            gradientColors = gradientColors,
            modifier = Modifier.size(52.dp)
        ) {
            Text("🎵", fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else {
            Text(
                text = track.duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** When Offline mode is on, keeps only tracks actually available in [downloadedKeys]; otherwise
 * returns [this] unchanged. */
private fun List<Track>.filterOfflineIfNeeded(offlineModeEnabled: Boolean, downloadedKeys: Set<String>): List<Track> =
    if (offlineModeEnabled) filter { downloadedKeys.contains(it.downloadKey()) } else this

@Composable
private fun OfflineEmptyState(message: String = "No downloaded tracks here yet. Turn off Offline mode, or download some tracks first.") {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DownloadDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
