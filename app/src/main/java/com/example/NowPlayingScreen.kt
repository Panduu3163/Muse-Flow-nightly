package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    positionMs: Long,
    onProgressChange: (Float) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    sleepTimerRemainingSeconds: Int? = null,
    queue: List<Track> = emptyList(),
    queueIndex: Int = 0,
    onSkipToIndex: (Int) -> Unit = {},
    onSetSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Interactive states
    var isShuffleEnabled by remember { mutableStateOf(false) }
    var isRepeatEnabled by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val appSettingsViewModel: AppSettingsViewModel = viewModel()
    val appSettings by appSettingsViewModel.state.collectAsState()

    // Real, Room-backed liked state - shared with Library's Liked Songs tab
    val likedSongsViewModel: LikedSongsViewModel = viewModel()
    val likedKeys by likedSongsViewModel.likedKeys.collectAsState()
    val isLiked = likedKeys.contains(track.downloadKey())

    val themeViewModel: ThemeViewModel = viewModel()
    val themeState by themeViewModel.themeState.collectAsState()
    val plateModifier = when (themeState.backgroundMode) {
        BackgroundMode.Amoled -> Modifier.background(Color.Black)
        BackgroundMode.Gradient -> Modifier.background(Brush.verticalGradient(themeState.palette.colors))
    }

    // Real synced lyrics from LRCLib, keyed to this track; re-fetched whenever the track changes.
    val lyricsViewModel: LyricsViewModel = viewModel()
    val lyricsResult by lyricsViewModel.lyricsResult.collectAsState()
    LaunchedEffect(track) { lyricsViewModel.loadLyricsFor(track) }

    // Parse track duration to seconds
    val totalSeconds = remember(track.duration) { parseDurationToSeconds(track.duration) }

    // Helper to format elapsed/remaining time
    fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }

    val elapsedSeconds = (progress * totalSeconds).toInt()
    val remainingSeconds = totalSeconds - elapsedSeconds

    // Animated Ambient Canvas / Floating Glow
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_1_angle"
    )
    val angle2 by infiniteTransition.animateFloat(
        initialValue = 180f,
        targetValue = 540f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_2_angle"
    )

    val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(plateModifier)
            .testTag("now_playing_screen")
    ) {
        // Floating Ambient Liquid Orbs Background
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.35f)
        ) {
            val centerWidth = size.width / 2
            val centerHeight = size.height / 3 // Float primarily behind/around the album art
            val radius = size.width * 0.7f

            val x1 = centerWidth + (size.width * 0.25f * cos(Math.toRadians(angle1.toDouble())).toFloat())
            val y1 = centerHeight + (size.height * 0.15f * sin(Math.toRadians(angle1.toDouble())).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(gradientColors.first().copy(alpha = 0.8f), Color.Transparent),
                    center = Offset(x1, y1),
                    radius = radius
                ),
                center = Offset(x1, y1),
                radius = radius
            )

            val x2 = centerWidth + (size.width * 0.25f * cos(Math.toRadians(angle2.toDouble())).toFloat())
            val y2 = centerHeight + (size.height * 0.15f * sin(Math.toRadians(angle2.toDouble())).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (gradientColors.size > 1) gradientColors[1] else gradientColors.first()).copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    center = Offset(x2, y2),
                    radius = radius
                ),
                center = Offset(x2, y2),
                radius = radius
            )
        }

        // Main Layout Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // 1. Header Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("now_playing_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = Color(0xFFE6E1E5),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM ALBUM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFCAC4D0).copy(alpha = 0.7f)
                    )
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFE6E1E5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                }

                IconButton(onClick = { /* Settings context option */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track Options",
                        tint = Color(0xFFE6E1E5)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            var accumulatedDrag = 0f
            val pointerInputModifier = if (appSettings.swipeToChangeSong) {
                Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onDrag = { change, dragAmount ->
                            accumulatedDrag += dragAmount.x
                        },
                        onDragEnd = {
                            if (accumulatedDrag > 100f) {
                                onPrevious()
                            } else if (accumulatedDrag < -100f) {
                                onNext()
                            }
                        }
                    )
                }
            } else {
                Modifier
            }

            // 2. Large Album Art Container (with interactive lyrics flip overlay)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(gradientColors))
                    .then(pointerInputModifier)
                    .testTag("album_art_container"),
                contentAlignment = Alignment.Center
            ) {
                // Real cover art (JioSaavn search results), if this track has one. Mock-catalog
                // tracks have no imageUrl and keep the emoji placeholder below unchanged.
                if (track.imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(track.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${track.title} cover art",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }

                // If lyrics overlay is requested, show them on top of the cover art beautifully
                if (showLyrics) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(20.dp)
                    ) {
                        LyricsContent(
                            lyricsResult = lyricsResult,
                            positionMs = positionMs,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Pill overlay to collapse lyrics
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF4F378B))
                                .clickable { showLyrics = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Show Cover", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (track.imageUrl == null) {
                    // Huge Vinyl/Note Icon representation - only when there's no real art to show
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 64.sp)
                    }
                }
                
                // Top-right Icons (Heart & Queue)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .bounceClick { likedSongsViewModel.toggle(track) }
                            .padding(8.dp)
                            .testTag("now_playing_heart_button")
                    ) {
                        androidx.compose.animation.Crossfade(targetState = isLiked, label = "like_animation") { liked ->
                            Icon(
                                imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (liked) "Remove from Liked" else "Add to Liked",
                                tint = if (liked) Color.Red else Color(0xFFE6E1E5),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    DownloadButton(
                        track = track,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // 3. Track Title & Artist Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFE6E1E5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("now_playing_title")
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFCAC4D0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { onArtistClick(track.artist) }
                        .padding(vertical = 4.dp)
                        .testTag("now_playing_artist")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Progress Seek Bar & Labels
            Column(modifier = Modifier.fillMaxWidth()) {
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(targetValue = progress, label = "progress")
                Slider(
                    value = animatedProgress,
                    onValueChange = onProgressChange,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFD0BCFF),
                        inactiveTrackColor = Color(0xFFE6E1E5).copy(alpha = 0.2f),
                        thumbColor = Color(0xFFD0BCFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp) // Slim slider
                        .testTag("now_playing_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(elapsedSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCAC4D0)
                    )
                    Text(
                        text = "${track.sourceType ?: "JioSaavn"} • Stream", // Codec/bitrate label
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFCAC4D0).copy(alpha = 0.6f)
                    )
                    Text(
                        text = "-${formatTime(remainingSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCAC4D0)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // 5. Main Controls Row (Prev, Play/Pause, Next)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = { isShuffleEnabled = !isShuffleEnabled },
                    modifier = Modifier.testTag("now_playing_shuffle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous Button
                Box(
                    modifier = Modifier
                        .bounceClick { onPrevious() }
                        .testTag("now_playing_prev"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = Color(0xFFE6E1E5),
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Play / Pause Circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD0BCFF))
                        .bounceClick { onPlayPauseToggle() }
                        .testTag("now_playing_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0C0C0E),
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Next Button
                Box(
                    modifier = Modifier
                        .bounceClick { onNext() }
                        .testTag("now_playing_next"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = Color(0xFFE6E1E5),
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Repeat Button
                IconButton(
                    onClick = { isRepeatEnabled = !isRepeatEnabled },
                    modifier = Modifier.testTag("now_playing_repeat")
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeatEnabled) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // 6. Secondary Action Row (Queue, Sleep Timer, Lyrics)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Queue Icon
                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue List",
                        tint = Color(0xFFE6E1E5).copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Interactive Lyrics Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (showLyrics) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.1f)
                        )
                        .clickable { showLyrics = !showLyrics }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("now_playing_lyrics_chip"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (showLyrics) Color(0xFF0C0C0E) else Color(0xFFD0BCFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lyrics",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (showLyrics) Color(0xFF0C0C0E) else Color(0xFFE6E1E5)
                        )
                    }
                }

                // Sleep Timer Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sleepTimerRemainingSeconds != null) {
                        Text(
                            text = formatTime(sleepTimerRemainingSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD0BCFF),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    IconButton(onClick = { showTimerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerRemainingSeconds != null) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column {
                    val options = listOf(10, 15, 30, 45)
                    options.forEach { minutes ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetSleepTimer(minutes)
                                    showTimerDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text("$minutes Minutes")
                        }
                    }
                    if (sleepTimerRemainingSeconds != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCancelSleepTimer()
                                    showTimerDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text("Cancel Timer", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showQueueSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp).fillMaxHeight(0.7f)) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(queue.size) { index ->
                        val queuedTrack = queue[index]
                        val isPlayingItem = index == queueIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSkipToIndex(index)
                                    showQueueSheet = false
                                }
                                .background(if (isPlayingItem) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            if (isPlayingItem) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            } else {
                                Box(modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(text = queuedTrack.title, style = MaterialTheme.typography.bodyLarge, color = if (isPlayingItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = queuedTrack.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * Renders whatever [LrcLibProvider.fetchLyrics] came back with. Null means still loading. Synced
 * lyrics get a scrolling, karaoke-style list that highlights and auto-centers the line whose
 * timestamp [positionMs] has most recently passed - everything else (plain-only lyrics,
 * instrumental tracks, no match, a fetch error) gets a plain, non-highlighted message instead,
 * since none of those have real timing data to sync against.
 */
@Composable
private fun LyricsContent(
    lyricsResult: LyricsResult?,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    when (lyricsResult) {
        null -> LyricsMessage("Loading lyrics…", modifier)
        is LyricsResult.Error -> LyricsMessage("Couldn't load lyrics right now.", modifier)
        LyricsResult.NotFound -> LyricsMessage("No lyrics found for this track.", modifier)
        LyricsResult.Instrumental -> LyricsMessage("Instrumental - no lyrics.", modifier)
        is LyricsResult.PlainOnly -> {
            Column(modifier = modifier.verticalScroll(rememberScrollState())) {
                LyricsHeader()
                Text(
                    text = lyricsResult.text,
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 26.sp),
                    color = Color(0xFFE6E1E5).copy(alpha = 0.85f)
                )
            }
        }
        is LyricsResult.Synced -> SyncedLyricsList(lyricsResult.lines, positionMs, modifier)
    }
}

@Composable
private fun LyricsMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFE6E1E5).copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LyricsHeader() {
    Text(
        text = "Lyrics",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color(0xFFD0BCFF),
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncedLyricsList(lines: List<LyricLine>, positionMs: Long, modifier: Modifier = Modifier) {
    val currentIndex = remember(lines, positionMs) { LrcLibProvider.currentLineIndex(lines, positionMs) }
    val listState = rememberLazyListState()

    // +1 to skip past the "Lyrics" header item at index 0; -2 so the current line settles a
    // little below the top of the visible area rather than being pinned to it.
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(max(0, currentIndex + 1 - 2))
        }
    }

    LazyColumn(modifier = modifier, state = listState) {
        item { LyricsHeader() }
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == currentIndex
            
            if (line.syllables.isNotEmpty()) {
                // Word-by-word rendering
                FlowRow(modifier = Modifier.padding(vertical = 4.dp)) {
                    for (syl in line.syllables) {
                        // Highlight if the current time is past this syllable's start
                        val isSylHighlighted = positionMs >= syl.timeMs
                        Text(
                            text = syl.text,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isSylHighlighted && isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                                lineHeight = 26.sp
                            ),
                            color = if (isSylHighlighted && isCurrent) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                // Fallback line-level rendering
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                        lineHeight = 26.sp
                    ),
                    color = if (isCurrent) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
