package com.example

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class DownloadStatus { DOWNLOADING, COMPLETED, FAILED }

/**
 * A track downloaded for offline playback. Keyed by [Track.downloadKey] (title/artist) rather
 * than any provider-specific id, since the mock catalog, JioSaavn search results, and this table
 * itself all need to agree on the same identity for a track without sharing a common id field.
 */
@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val gradientIndex: Int,
    val imageUrl: String?,
    val filePath: String,
    val status: String,
    val updatedAt: Long,
    /** [Track.sourceId]/[Track.sourceType], persisted (added in [MIGRATION_3_4]) so a
     * YouTube-sourced download's [Track] can be fully reconstructed - e.g. if its local file is
     * ever missing, it re-resolves via [YouTubeStreamResolver] instead of falling through to
     * mock-catalog resolution. Null for sources that never needed either field. */
    val sourceId: String? = null,
    val sourceType: String? = null
)

@Dao
interface DownloadedTrackDao {
    @Query("SELECT * FROM downloaded_tracks WHERE status = 'COMPLETED'")
    fun observeCompleted(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE key = :key")
    suspend fun deleteByKey(key: String)
}

/**
 * One row per distinct track that's actually been played, for Home's real "Recently Played"
 * shelf. Keyed the same way as [DownloadedTrackEntity] (title/artist), so replaying a track
 * updates its [playedAt] via REPLACE rather than growing a duplicate row per play.
 */
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val gradientIndex: Int,
    val imageUrl: String?,
    val streamUrl: String?,
    val playedAt: Long,
    /** [Track.sourceId]/[Track.sourceType], persisted (added in [MIGRATION_3_4]) so replaying a
     * YouTube-sourced track from history re-resolves via [YouTubeStreamResolver] instead of
     * falling through to mock-catalog resolution (it has no [streamUrl] of its own). Null for
     * sources that never needed either field. */
    val sourceId: String? = null,
    val sourceType: String? = null
)

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(entity: PlaybackHistoryEntity)
}

/**
 * A track the user has explicitly liked, for Library's real "Liked Songs" tab. Keyed the same way
 * as [DownloadedTrackEntity]/[PlaybackHistoryEntity] (title/artist), so liking is idempotent and
 * agrees on identity with the rest of the app.
 */
@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val gradientIndex: Int,
    val imageUrl: String?,
    val streamUrl: String?,
    val likedAt: Long
)

@Dao
interface LikedSongDao {
    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC")
    fun observeAll(): Flow<List<LikedSongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE key = :key)")
    fun observeIsLiked(key: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(entity: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE key = :key")
    suspend fun unlike(key: String)
}

/** A playlist the user has created, for Library's real "Playlists" tab. There's no "add track to
 * playlist" feature yet, so every playlist starts (and stays) empty. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val remoteId: String? = null,
    val sourceType: String? = null,
    val imageUrl: String? = null,
    val subtitle: String? = null,
    val tracksJson: String? = null
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlaylistEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE remoteId = :remoteId LIMIT 1)")
    fun observeIsSaved(remoteId: String): Flow<Boolean>

    @Query("SELECT tracksJson FROM playlists WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getTracksJson(remoteId: String): String?

    @Query("DELETE FROM playlists WHERE remoteId = :remoteId")
    suspend fun removeByRemoteId(remoteId: String)
}

@Entity(tableName = "home_shelves")
data class HomeShelfEntity(
    @PrimaryKey val title: String,
    val tracksJson: String,
    val updatedAt: Long
)

@Dao
interface HomeShelfDao {
    @Query("SELECT * FROM home_shelves WHERE title = :title LIMIT 1")
    suspend fun getShelf(title: String): HomeShelfEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShelf(entity: HomeShelfEntity)
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun observeRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSearch(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val gradientIndex: Int,
    val imageUrl: String?,
    val streamUrl: String?,
    val cachedAt: Long,
    val sourceId: String? = null,
    val sourceType: String? = null
)

@Dao
interface CachedTrackDao {
    @Query("SELECT * FROM cached_tracks ORDER BY cachedAt DESC")
    fun observeCachedTracks(): Flow<List<CachedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(entity: CachedTrackEntity)
}

@Entity(
    tableName = "playback_events",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = PlaybackHistoryEntity::class,
            parentColumns = ["key"],
            childColumns = ["trackKey"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("trackKey"), androidx.room.Index("playedAt")]
)
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackKey: String,
    val playedAt: Long,
    val durationMs: Long
)

data class TopItem(
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val gradientIndex: Int,
    val playCount: Int,
    val totalDurationMs: Long
)

@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordEvent(entity: PlaybackEventEntity)

    @Query("""
        SELECT h.title, h.artist as subtitle, h.imageUrl, h.gradientIndex, COUNT(e.id) as playCount, SUM(e.durationMs) as totalDurationMs
        FROM playback_events e
        INNER JOIN playback_history h ON e.trackKey = h.key
        WHERE e.playedAt >= :since
        GROUP BY e.trackKey
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    fun getTopSongs(since: Long, limit: Int): Flow<List<TopItem>>

    @Query("""
        SELECT h.artist as title, '' as subtitle, h.imageUrl, h.gradientIndex, COUNT(e.id) as playCount, SUM(e.durationMs) as totalDurationMs
        FROM playback_events e
        INNER JOIN playback_history h ON e.trackKey = h.key
        WHERE e.playedAt >= :since
        GROUP BY h.artist
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    fun getTopArtists(since: Long, limit: Int): Flow<List<TopItem>>

    @Query("""
        SELECT h.album as title, h.artist as subtitle, h.imageUrl, h.gradientIndex, COUNT(e.id) as playCount, SUM(e.durationMs) as totalDurationMs
        FROM playback_events e
        INNER JOIN playback_history h ON e.trackKey = h.key
        WHERE e.playedAt >= :since AND h.album != 'Unknown' AND h.album != ''
        GROUP BY h.album
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    fun getTopAlbums(since: Long, limit: Int): Flow<List<TopItem>>
}


/** v3 -> v4: adds [DownloadedTrackEntity.sourceId]/[DownloadedTrackEntity.sourceType] and
 * [PlaybackHistoryEntity.sourceId]/[PlaybackHistoryEntity.sourceType] - both nullable with no
 * default needed beyond SQLite's implicit NULL, so a plain `ADD COLUMN` is enough; existing rows
 * simply get NULL for both (read paths already treat that as "not a YouTube-sourced track"). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN sourceId TEXT")
        db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN sourceType TEXT")
        db.execSQL("ALTER TABLE playback_history ADD COLUMN sourceId TEXT")
        db.execSQL("ALTER TABLE playback_history ADD COLUMN sourceType TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `home_shelves` (`title` TEXT NOT NULL, `tracksJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`title`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`query`))")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `cached_tracks` (`key` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `duration` TEXT NOT NULL, `gradientIndex` INTEGER NOT NULL, `imageUrl` TEXT, `streamUrl` TEXT, `cachedAt` INTEGER NOT NULL, `sourceId` TEXT, `sourceType` TEXT, PRIMARY KEY(`key`))")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `playback_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackKey` TEXT NOT NULL, `playedAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, FOREIGN KEY(`trackKey`) REFERENCES `playback_history`(`key`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_events_trackKey` ON `playback_events` (`trackKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_events_playedAt` ON `playback_events` (`playedAt`)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN remoteId TEXT")
        db.execSQL("ALTER TABLE playlists ADD COLUMN sourceType TEXT")
        db.execSQL("ALTER TABLE playlists ADD COLUMN imageUrl TEXT")
        db.execSQL("ALTER TABLE playlists ADD COLUMN subtitle TEXT")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN tracksJson TEXT")
    }
}

@Database(
    entities = [
        DownloadedTrackEntity::class,
        PlaybackHistoryEntity::class,
        LikedSongEntity::class,
        PlaylistEntity::class,
        HomeShelfEntity::class,
        SearchHistoryEntity::class,
        CachedTrackEntity::class,
        PlaybackEventEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class MuseFlowDatabase : RoomDatabase() {
    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun likedSongDao(): LikedSongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun homeShelfDao(): HomeShelfDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun statsDao(): StatsDao

    companion object {
        @Volatile private var instance: MuseFlowDatabase? = null

        fun getInstance(context: Context): MuseFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuseFlowDatabase::class.java,
                    "museflow.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build().also { instance = it }
            }

    }
}
