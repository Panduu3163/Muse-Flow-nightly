package com.example

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Records every track that actually starts playing, so Home's "Recently Played" shelf can be
 * real playback history instead of a fixed mock list. A process-wide singleton (see
 * [getInstance]), same pattern as [DownloadRepository], since playback (and thus history
 * recording) happens from [PlaybackService]/[PlayerViewModel] regardless of which screen is
 * currently visible.
 */
class PlaybackHistoryRepository private constructor(context: Context) {

    private val db = MuseFlowDatabase.getInstance(context.applicationContext)
    private val dao = db.playbackHistoryDao()
    private val statsDao = db.statsDao()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>> = dao.observeRecent(limit)

    fun recordPlayed(track: Track) {
        repositoryScope.launch {
            val playedAtTime = System.currentTimeMillis()
            dao.record(
                PlaybackHistoryEntity(
                    key = track.downloadKey(),
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = track.duration,
                    gradientIndex = track.gradientIndex,
                    imageUrl = track.imageUrl,
                    streamUrl = track.streamUrl,
                    playedAt = playedAtTime,
                    sourceId = track.sourceId,
                    sourceType = track.sourceType?.name
                )
            )
            val durationMs = parseDurationToMs(track.duration)
            statsDao.recordEvent(
                PlaybackEventEntity(
                    trackKey = track.downloadKey(),
                    playedAt = playedAtTime,
                    durationMs = durationMs
                )
            )
        }
    }

    private fun parseDurationToMs(durationStr: String): Long {
        val parts = durationStr.split(":")
        if (parts.size == 2) {
            val min = parts[0].toLongOrNull() ?: 0L
            val sec = parts[1].toLongOrNull() ?: 0L
            return (min * 60 + sec) * 1000
        }
        return 0L
    }

    companion object {
        @Volatile private var instance: PlaybackHistoryRepository? = null

        fun getInstance(context: Context): PlaybackHistoryRepository =
            instance ?: synchronized(this) {
                instance ?: PlaybackHistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}

fun PlaybackHistoryEntity.toTrack(): Track = Track(
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    plays = "",
    gradientIndex = gradientIndex,
    imageUrl = imageUrl,
    streamUrl = streamUrl,
    sourceType = sourceType?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() },
    sourceId = sourceId
)
