package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Facade for the parts of Library that aren't covered by [DownloadViewModel] (Downloads tab) or
 * [LikedSongsViewModel] (Liked Songs tab): the user's created playlists and real playback
 * history, both Room-backed so they start empty on a fresh install and update live.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository.getInstance(application)
    private val playbackHistoryRepository = PlaybackHistoryRepository.getInstance(application)
    private val likedSongsRepository = LikedSongsRepository.getInstance(application)
    private val downloadRepository = DownloadRepository.getInstance(application)

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val trackListAdapter = moshi.adapter<List<Track>>(Types.newParameterizedType(List::class.java, Track::class.java))

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = playbackHistoryRepository.observeRecent(50)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String, source: String = "Empty") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val tracksJson = when (source) {
                "Liked Songs" -> {
                    val liked = likedSongsRepository.observeAll().first().map { it.toTrack() }
                    trackListAdapter.toJson(liked)
                }
                "Downloads" -> {
                    val downloads = downloadRepository.completedDownloads.first().map {
                        Track(
                            title = it.title,
                            artist = it.artist,
                            album = it.album,
                            duration = it.duration,
                            plays = "",
                            gradientIndex = it.gradientIndex,
                            imageUrl = it.imageUrl,
                            streamUrl = null,
                            sourceType = it.sourceType?.let { type -> runCatching { MusicSource.valueOf(type) }.getOrNull() },
                            sourceId = it.sourceId
                        )
                    }
                    trackListAdapter.toJson(downloads)
                }
                else -> null
            }
            playlistRepository.create(name, tracksJson)
        }
    }

    fun observeIsPlaylistSaved(remoteId: String): kotlinx.coroutines.flow.Flow<Boolean> = 
        playlistRepository.observeIsSaved(remoteId)

    fun toggleSaveRemotePlaylist(playlist: PlaylistResult, isSaved: Boolean) {
        viewModelScope.launch {
            if (isSaved) {
                playlistRepository.removeRemotePlaylist(playlist.id)
            } else {
                playlistRepository.saveRemotePlaylist(playlist)
            }
        }
    }
}
