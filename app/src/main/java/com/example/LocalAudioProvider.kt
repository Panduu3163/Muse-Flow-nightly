package com.example

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAudioProvider(private val context: Context) : Provider<TrackResult> {

    override val name = "Local Device"

    override suspend fun search(query: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TrackResult>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // Only search for music files
        var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        var selectionArgs: Array<String>? = null

        if (query.isNotBlank()) {
            selection += " AND (${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?)"
            val likeQuery = "%$query%"
            selectionArgs = arrayOf(likeQuery, likeQuery)
        }

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val durationMs = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                
                val durationSec = durationMs / 1000
                val durationStr = "${durationSec / 60}:${(durationSec % 60).toString().padStart(2, '0')}"

                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")

                results.add(
                    TrackResult(
                        id = id.toString(),
                        title = title,
                        artist = artist,
                        duration = durationStr,
                        source = name,
                        sourceType = MusicSource.LOCAL_DEVICE,
                        directStreamUrl = contentUri.toString(),
                        imageUrl = albumArtUri.toString()
                    )
                )
            }
        }
        
        results
    }

    override suspend fun getStreamUrl(item: TrackResult): StreamResolution? {
        return item.directStreamUrl?.let { StreamResolution(url = it) }
    }
}
