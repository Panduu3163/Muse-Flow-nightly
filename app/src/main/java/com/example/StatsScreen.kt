package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statsViewModel: StatsViewModel = viewModel()
    val topSongs by statsViewModel.topSongs.collectAsState()
    val topArtists by statsViewModel.topArtists.collectAsState()
    val topAlbums by statsViewModel.topAlbums.collectAsState()
    val selectedDropdown by statsViewModel.selectedDropdown.collectAsState()
    val selectedChip by statsViewModel.selectedChip.collectAsState()
    val chipOptions by statsViewModel.chipOptions.collectAsState()

    var dropdownExpanded by remember { mutableStateOf(false) }

    ThemedBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stats",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = { /* TODO History */ }) {
                    Icon(Icons.Default.History, contentDescription = "History", tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            // Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dropdown
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E2722)) // Matches green-ish theme
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFA1C6AD), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(selectedDropdown, color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        listOf("Continuous", "Weeks", "Months", "Years").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    statsViewModel.selectDropdown(option)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))
                // Vertical divider
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.DarkGray))
                Spacer(modifier = Modifier.width(12.dp))

                // Chips
                chipOptions.forEach { chip ->
                    val isSelected = chip == selectedChip
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0xFF2E3A32) else Color(0xFF1C1C1C))
                            .clickable { statsViewModel.selectChip(chip) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFA1C6AD), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = chip,
                            color = if (isSelected) Color(0xFFA1C6AD) else Color.LightGray,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content Sections
            StatsSection(title = "${topSongs.size} Songs", items = topSongs, isCircular = false)
            StatsSection(title = "${topArtists.size} Artists", items = topArtists, isCircular = true)
            StatsSection(title = "${topAlbums.size} Albums", items = topAlbums, isCircular = false)
        }
    }
}

@Composable
fun StatsSection(title: String, items: List<TopItem>, isCircular: Boolean) {
    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFFA1C6AD),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(items) { index, item ->
                Column(
                    modifier = Modifier.width(140.dp)
                ) {
                    val shape = if (isCircular) CircleShape else RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(shape)
                            .background(Brush.linearGradient(MusicData.Gradients[item.gradientIndex % MusicData.Gradients.size]))
                    ) {
                        if (item.imageUrl != null) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(item.imageUrl)
                                    
                                    .build(),
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("🎵", fontSize = 48.sp)
                            }
                        }
                        // Play overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color.White, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${index + 1}. ${item.title}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val durationStr = formatDuration(item.totalDurationMs)
                    Text(
                        text = "${item.playCount} times · $durationStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return String.format("%d:%02d", min, sec)
}
