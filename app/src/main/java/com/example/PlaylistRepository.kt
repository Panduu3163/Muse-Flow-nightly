package com.example

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Playlists the user has created, for Library's real "Playlists" tab. A process-wide singleton,
 * same pattern as [DownloadRepository].
 */
class PlaylistRepository private constructor(context: Context) {

    private val dao = MuseFlowDatabase.getInstance(context.applicationContext).playlistDao()

    fun observeAll(): Flow<List<PlaylistEntity>> = dao.observeAll()

    suspend fun create(name: String, tracksJson: String? = null) {
        val id = dao.insert(PlaylistEntity(name = name, createdAt = System.currentTimeMillis(), tracksJson = tracksJson))
        if (tracksJson != null) {
            // Need to update it to have a remoteId so the click handler treats it as playable
            dao.insert(PlaylistEntity(id = id, name = name, createdAt = System.currentTimeMillis(), tracksJson = tracksJson, remoteId = "local_$id"))
        }
    }

    suspend fun getTracksJson(remoteId: String): String? = dao.getTracksJson(remoteId)

    fun observeIsSaved(remoteId: String): Flow<Boolean> = dao.observeIsSaved(remoteId)

    suspend fun saveRemotePlaylist(playlist: PlaylistResult) {
        dao.insert(
            PlaylistEntity(
                name = playlist.title,
                createdAt = System.currentTimeMillis(),
                remoteId = playlist.id,
                sourceType = playlist.sourceType.name,
                imageUrl = playlist.imageUrl,
                subtitle = playlist.subtitle
            )
        )
    }

    suspend fun removeRemotePlaylist(remoteId: String) {
        dao.removeByRemoteId(remoteId)
    }

    companion object {
        @Volatile private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository =
            instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context.applicationContext).also { instance = it }
            }
    }
}
