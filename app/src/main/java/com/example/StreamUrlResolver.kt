package com.example

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single point of entry for resolving a [Track] to a downloadable stream URL, used by
 * [DownloadRepository] regardless of which screen triggered the download (Search results,
 * Album/Playlist/Artist detail, Now Playing). Playback has its own resolution paths —
 * [TrackStreamResolver] for mock-catalog tracks and [PlaybackService]'s resolving data source
 * for YouTube tracks — and those are intentionally separate (different freshness/caching
 * requirements). This is the **download-only** resolver.
 *
 * Resolution priority:
 * 1. [Track.streamUrl] already set (JioSaavn search results) → use it directly.
 * 2. [Track.sourceType] is [MusicSource.YOUTUBE_MUSIC] with a known [Track.sourceId] →
 *    resolve via [YouTubeStreamResolver] (no search needed, we already have the video id).
 * 3. Otherwise → race JioSaavn and YouTube Music searches **in parallel** (with a short
 *    per-source timeout) and take whichever resolves first, so a dead/slow source doesn't
 *    block the download for the full timeout before the other source gets a turn.
 */
object StreamUrlResolver {

    /** Per-source timeout for the search+resolve step — deliberately shorter than the
     * [DEFAULT_LOAD_TIMEOUT_MS] used by UI-facing shelf loads, because a download is already
     * a deliberate user action and waiting 9+ seconds on a dead source feels broken. */
    private const val RESOLVE_TIMEOUT_MS = 4_000L

    /**
     * Returns a directly-downloadable stream URL for [track], or null if every resolution
     * attempt fails. Safe to call from any dispatcher (performs its own IO internally).
     */
    suspend fun resolve(context: Context, track: Track): String? {
        // 1. Already resolved (e.g. a JioSaavn search result with a direct CDN URL).
        track.streamUrl?.let { return it }

        // 2. YouTube-sourced track with a known video id — resolve directly, no search needed.
        if (track.sourceType == MusicSource.YOUTUBE_MUSIC && track.sourceId != null) {
            val resolved = try {
                withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                    YouTubeStreamResolver.resolve(context, track.sourceId)
                }
            } catch (_: Exception) { null }
            if (resolved != null) return resolved
        }

        // 3. Race both providers in parallel with short timeouts — whichever delivers a usable
        //    URL first wins. A dead JioSaavn no longer blocks YouTube Music from responding.
        val query = "${track.title} ${track.artist}"

        return coroutineScope {
            val jioDeferred = async {
                try {
                    withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                        JioSaavnProvider().search(query)
                            .firstOrNull { it.directStreamUrl != null }?.directStreamUrl
                    }
                } catch (_: Exception) { null }
            }

            val ytDeferred = async {
                try {
                    withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                        val ytResult = YouTubeMusicProvider(context).search(query)
                            .firstOrNull() ?: return@withTimeoutOrNull null
                        YouTubeStreamResolver.resolve(context, ytResult.id)
                    }
                } catch (_: Exception) { null }
            }

            // Prefer JioSaavn (directly playable, no expiry concerns) when both succeed.
            val jioUrl = jioDeferred.await()
            if (jioUrl != null) {
                ytDeferred.cancel()
                return@coroutineScope jioUrl
            }
            ytDeferred.await()
        }
    }
}
