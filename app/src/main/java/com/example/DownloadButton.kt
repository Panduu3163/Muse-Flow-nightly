package com.example

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MusePrimary

/**
 * Tap to download [track] for offline playback; tap again to cancel mid-download, or to remove
 * it once complete. While downloading, shows a determinate progress ring with percentage instead
 * of a featureless spinner, so the user can see exactly how far along the download is. The ring
 * tracks the real per-byte progress from [DownloadRepository.inProgress].
 */
@Composable
fun DownloadButton(
    track: Track,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFE6E1E5).copy(alpha = 0.8f),
    downloadViewModel: DownloadViewModel = viewModel()
) {
    val downloadedTracks by downloadViewModel.downloadedTracks.collectAsState()
    val downloadProgress by downloadViewModel.downloadProgress.collectAsState()
    val downloadingKeys by downloadViewModel.downloadingKeys.collectAsState()
    val failures by downloadViewModel.failures.collectAsState()

    val key = track.downloadKey()
    val isDownloaded = downloadedTracks.any { it.downloadKey() == key }
    val isDownloading = downloadingKeys.contains(key)
    val percent = downloadProgress[key]  // null = not downloading, -1 = indeterminate

    // Otherwise a failed download just silently reverts to "not downloaded" with no explanation
    // - overwhelmingly because the connection dropped mid-download.
    val context = LocalContext.current
    LaunchedEffect(failures[key]) {
        if (failures[key] != null) {
            Toast.makeText(context, "Download failed. Check your connection and try again.", Toast.LENGTH_SHORT).show()
        }
    }

    IconButton(
        onClick = {
            when {
                isDownloading -> downloadViewModel.cancel(track)
                isDownloaded -> downloadViewModel.delete(track)
                else -> downloadViewModel.download(track)
            }
        },
        modifier = modifier
    ) {
        when {
            isDownloading -> {
                Box(contentAlignment = Alignment.Center) {
                    if (percent != null && percent >= 0) {
                        // Determinate progress ring — real per-byte percentage.
                        CircularProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.size(24.dp),
                            color = MusePrimary,
                            trackColor = tint.copy(alpha = 0.2f),
                            strokeWidth = 2.5.dp,
                            strokeCap = StrokeCap.Round
                        )
                        // Percentage label centered inside the ring.
                        Text(
                            text = "$percent",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = tint
                        )
                    } else {
                        // Indeterminate (no content-length, or just started resolving).
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MusePrimary,
                            trackColor = tint.copy(alpha = 0.2f),
                            strokeWidth = 2.5.dp,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
            isDownloaded -> Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = "Downloaded - tap to remove",
                tint = MusePrimary
            )
            else -> Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download for offline playback",
                tint = tint
            )
        }
    }
}
